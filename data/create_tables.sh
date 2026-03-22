#!/bin/bash

set -e

HOST="nudge.cmqx6tpknayx.us-east-2.rds.amazonaws.com"
DB="nudge"

if [ -z "$NUDGE_MYSQL_USER" ] || [ -z "$NUDGE_MYSQL_PASSWORD" ]; then
    echo "Error: NUDGE_MYSQL_USER and NUDGE_MYSQL_PASSWORD must be set"
    exit 1
fi

mysql -h "$HOST" -u "$NUDGE_MYSQL_USER" -p"$NUDGE_MYSQL_PASSWORD" "$DB" <<'EOF'
CREATE TABLE IF NOT EXISTS reminders (
    id           VARCHAR(32)  NOT NULL,
    username     VARCHAR(255) NOT NULL,
    title        TEXT         NOT NULL,
    description  TEXT         NOT NULL,
    link         TEXT         NOT NULL DEFAULT '',
    time         DATETIME     NOT NULL,
    read_time    DATETIME     NOT NULL,
    priority     TINYINT      NOT NULL DEFAULT 2,
    closed       BOOLEAN      NOT NULL DEFAULT FALSE,
    snooze       INT          NOT NULL DEFAULT 0,
    repeat_days  INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id, username),
    INDEX idx_username (username),
    INDEX idx_time     (time)
);
EOF

echo "Done."
