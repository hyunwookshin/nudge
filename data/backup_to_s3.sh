#!/bin/bash
# Runs on the server (cron, see backup_setup.md): dumps the nudge DB and uploads it to S3 as
#   daily/   every run
#   weekly/  on Sundays
#   monthly/ on the 1st
# Old copies are expired by the bucket's lifecycle rules, so this script never deletes anything.
# Config comes from /etc/nudge-backup.env (chmod 600): NUDGE_MYSQL_USER, NUDGE_MYSQL_PASSWORD, BACKUP_BUCKET.
set -euo pipefail

source /etc/nudge-backup.env
: "${NUDGE_MYSQL_USER:?}" "${NUDGE_MYSQL_PASSWORD:?}" "${BACKUP_BUCKET:?}"

DB="${NUDGE_DB:-nudge}"
STAMP="$(date -u +%Y-%m-%d)"
FILE="$(mktemp /tmp/nudge-${STAMP}-XXXXXX.sql.gz)"
trap 'rm -f "$FILE"' EXIT

# password via a temp option file, not argv (visible in ps)
CNF="$(mktemp)"; chmod 600 "$CNF"
printf '[client]\nuser=%s\npassword=%s\n' "$NUDGE_MYSQL_USER" "$NUDGE_MYSQL_PASSWORD" > "$CNF"
trap 'rm -f "$FILE" "$CNF"' EXIT

mysqldump --defaults-extra-file="$CNF" --single-transaction --routines "$DB" | gzip -9 > "$FILE"

# a dump that is tiny or has no tables is a failed dump
[ "$(gzip -dc "$FILE" | grep -c '^CREATE TABLE')" -ge 2 ] || { echo "dump looks empty, not uploading" >&2; exit 1; }

NAME="nudge-${STAMP}.sql.gz"
put() { aws s3 cp --only-show-errors --sse AES256 "$FILE" "s3://${BACKUP_BUCKET}/$1/${NAME}"; }

put daily
[ "$(date -u +%u)" = 7 ] && put weekly
[ "$(date -u +%d)" = 01 ] && put monthly
echo "uploaded ${NAME} ($(du -h "$FILE" | cut -f1))"
exit 0
