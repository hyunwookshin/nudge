#!/bin/bash
# Adds the project columns to an existing reminders table (create_tables.sh already
# has them for new installs). Idempotent and works on MySQL and MariaDB: each column is
# only added if information_schema says it is missing.
#
#   NUDGE_MYSQL_USER=... NUDGE_MYSQL_PASSWORD=... [NUDGE_MYSQL_HOST=127.0.0.1] ./migrate_projects.sh

set -e

HOST="${NUDGE_MYSQL_HOST:-nudge.cmqx6tpknayx.us-east-2.rds.amazonaws.com}"
DB="nudge"

if [ -z "$NUDGE_MYSQL_USER" ] || [ -z "$NUDGE_MYSQL_PASSWORD" ]; then
    echo "Error: NUDGE_MYSQL_USER and NUDGE_MYSQL_PASSWORD must be set"
    exit 1
fi
export MYSQL_PWD="$NUDGE_MYSQL_PASSWORD"

run() { mysql -h "$HOST" -u "$NUDGE_MYSQL_USER" "$DB" "$@"; }

add_column() {   # add_column <name> <definition>
    local exists
    exists=$(run -N -e "SELECT COUNT(*) FROM information_schema.columns \
        WHERE table_schema = '$DB' AND table_name = 'reminders' AND column_name = '$1'")
    if [ "$exists" = "0" ]; then
        run -e "ALTER TABLE reminders ADD COLUMN $1 $2"
        echo "added reminders.$1"
    else
        echo "reminders.$1 already exists"
    fi
}

add_column kind         "VARCHAR(16) NOT NULL DEFAULT 'event'"
add_column end_date     "DATE NULL"
add_column project_type "VARCHAR(32) NOT NULL DEFAULT ''"
add_column parent_id    "VARCHAR(32) NOT NULL DEFAULT ''"
