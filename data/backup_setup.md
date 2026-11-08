# Nightly DB snapshots to S3

`backup_to_s3.sh` runs on the server. Daily dump, copied to `weekly/` on Sundays and `monthly/` on the 1st.
Lifecycle rules expire them: daily after 7 days, weekly after 35, monthly after 365.

## 1. Bucket (run from a machine with AWS admin access)
```sh
B=nudge-backups-$(aws sts get-caller-identity --query Account --output text)
aws s3api create-bucket --bucket $B --region us-east-2 --create-bucket-configuration LocationConstraint=us-east-2
aws s3api put-public-access-block --bucket $B --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-encryption --bucket $B --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-bucket-lifecycle-configuration --bucket $B --lifecycle-configuration '{"Rules":[
 {"ID":"daily","Status":"Enabled","Filter":{"Prefix":"daily/"},"Expiration":{"Days":7}},
 {"ID":"weekly","Status":"Enabled","Filter":{"Prefix":"weekly/"},"Expiration":{"Days":35}},
 {"ID":"monthly","Status":"Enabled","Filter":{"Prefix":"monthly/"},"Expiration":{"Days":365}}]}'
```

## 2. Upload-only permission for the server (instance role, or an IAM user's keys in `~/.aws`)
Policy: `s3:PutObject` on `arn:aws:s3:::$B/*` (nothing else; no delete, no read).

## 3. On the server
```sh
sudo tee /etc/nudge-backup.env >/dev/null <<ENV
NUDGE_MYSQL_USER=...
NUDGE_MYSQL_PASSWORD=...
BACKUP_BUCKET=$B
ENV
sudo chmod 600 /etc/nudge-backup.env
sudo cp backup_to_s3.sh /usr/local/bin/ && sudo chmod 755 /usr/local/bin/backup_to_s3.sh
sudo /usr/local/bin/backup_to_s3.sh        # test once by hand
echo '15 3 * * * root /usr/local/bin/backup_to_s3.sh >> /var/log/nudge-backup.log 2>&1' | sudo tee /etc/cron.d/nudge-backup
```
(AL2023 needs `sudo dnf install -y cronie && sudo systemctl enable --now crond`.)

## Restore
`aws s3 cp s3://$B/daily/nudge-YYYY-MM-DD.sql.gz - | gunzip | mysql nudge` (into a scratch DB first).
