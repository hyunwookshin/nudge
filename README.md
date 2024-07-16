
## Dependency

```
curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
python3 get-pip.py
pip3 install pyyaml
pip3 install pytz
pip3 install flask
pip3 install waitress
```

## Set up

### Set up the config

- Fill out `config.yaml.tpt` and save it as `config.yaml`, by running `make all`
- Fill out `store/reminders.yaml.tpt` and save it as `config.yaml`, by running `make all`

### Activating Sender Email

Refer to https://www.youtube.com/watch?v=Y_u5KIeXiVI

- Go to "Manage account" under Account Mavatar
- Click on Security, and set up Two-Factor-Authentication (Required)
- Then search for "App Password"
- Use "Nudge" as the app name

## Adding new reminders

### Via CLI

```
export NUDGE_CONFIG_PATH=./config.yaml
export NUDGE_STORE_PATH=store
./remind [-d]
```

### Via Curl

As shown in the `client/remind.sh` run this curl command:
```
curl -k -X POST https://<url>/add_reminder \
   -H Content-Type:application/json \
   -d '{
      "Title": "Meeting with team",
      "Description": "Discuss project status",
      "Date": "2024-01-01",
      "Time": "17:00:00",
      "Link": "http://google.com",
      "Priority": 2
   }'
```

This requires the following nginx settings:

```
 server {
     listen       443 ssl http2 default_server;
     listen       [::]:443 ssl http2 default_server;
     server_name  _;
     root         /usr/share/nginx/html;
     autoindex on;

     ssl_certificate "/etc/pki/nginx/server.crt";
     ssl_certificate_key "/etc/pki/nginx/private/server.key";
     ssl_session_cache shared:SSL:1m;
     ssl_session_timeout  10m;
     ssl_ciphers HIGH:!aNULL:!MD5;
     ssl_prefer_server_ciphers on;
     # Nudge
     location /add_reminder {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
}
```

And run the server

```
NUDGE_SECURE_KEY_PATH=... NUDGE_CONFIG_PATH=config.yaml ./server.py
```

The `NUDGE_SERVER_SECURE_PATH` should be where the secure key is stored.

## Sending reminders

To run eligible reminders,
```
make job
```

To run all reminders regardless of the timestamp,
```
make job-all
```

To dryrun eligible reminders,
```
make job-dry
```

To dryrun all reminders,
```
make job-dry-all
```

### cron job

```
*/10 * * * * cd ~/wses/nudge && make job
```

### Committing

```
./commit.sh
```
