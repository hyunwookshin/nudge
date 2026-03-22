#!/usr/bin/env python3
"""
Copy all reminders from the YAML data store to MySQL.

Usage:
    NUDGE_MYSQL_USER=x NUDGE_MYSQL_PASSWORD=y ./yaml_to_mysql.py --store /path/to/store

The script discovers users by listing subdirectories under {store}/users/.
It reads each user's enc_key from {store}/auth.yaml (override with --auth).

Reminders are decrypted from YAML and re-encrypted before writing to MySQL,
so the MySQL rows use the same field-level encryption as the YAML files.
"""

import argparse
import os
import sys

# Allow running from the repo root without installing the package.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from auth import auth as auth_mod
from data import yamldatasource, mysqldatasource


class _Config:
    """Minimal config object that satisfies both datasource constructors."""

    def __init__(self, store_path, mysql_user, mysql_password):
        self._store_path = store_path
        self._mysql_user = mysql_user
        self._mysql_password = mysql_password

    def getStorePath(self):
        return self._store_path

    def getMySQLUser(self):
        return self._mysql_user

    def getMySQLPassword(self):
        return self._mysql_password


def _discover_users(store_path):
    users_dir = os.path.join(store_path, "users")
    if not os.path.isdir(users_dir):
        print(f"No users directory found at {users_dir}")
        return []
    return [
        name for name in os.listdir(users_dir)
        if os.path.isdir(os.path.join(users_dir, name))
    ]


def migrate(store_path, auth_path, dry_run):
    mysql_user = os.environ.get("NUDGE_MYSQL_USER", "")
    mysql_password = os.environ.get("NUDGE_MYSQL_PASSWORD", "")
    if not mysql_user or not mysql_password:
        print("Error: NUDGE_MYSQL_USER and NUDGE_MYSQL_PASSWORD must be set")
        sys.exit(1)

    # Point auth module at the right auth.yaml
    auth_mod.DEFAULT_USERS_PATH = auth_path

    config = _Config(store_path, mysql_user, mysql_password)
    users = _discover_users(store_path)

    if not users:
        print("No users found — nothing to migrate.")
        return

    print(f"Found {len(users)} user(s): {', '.join(users)}")
    if dry_run:
        print("[dry-run] No data will be written.")

    total_copied = 0
    for username in users:
        enc_key = auth_mod.get_enc_key(username)
        encrypt = bool(enc_key)

        yaml_src = yamldatasource.YamlDataSource(
            config, user=username, encrypt=encrypt, password_hash=enc_key
        )
        sql_dst = mysqldatasource.MySQLDataSource(
            config, user=username, encrypt=encrypt, password_hash=enc_key
        )

        reminders = yaml_src.loadReminders()
        print(f"  {username}: {len(reminders)} reminder(s)", end="")

        if not dry_run:
            sql_dst.storeReminders(reminders)
            print(" — copied")
        else:
            print(" — skipped (dry-run)")

        total_copied += len(reminders)

    if not dry_run:
        print(f"\nDone. {total_copied} reminder(s) copied across {len(users)} user(s).")
    else:
        print(f"\n[dry-run] Would have copied {total_copied} reminder(s) across {len(users)} user(s).")


def main():
    parser = argparse.ArgumentParser(description="Migrate YAML reminders to MySQL.")
    parser.add_argument(
        "--store", required=True,
        help="Path to the store directory root (contains users/ and optionally auth.yaml)"
    )
    parser.add_argument(
        "--auth",
        help="Path to auth.yaml (default: {store}/auth.yaml)"
    )
    parser.add_argument(
        "--dry-run", action="store_true",
        help="Read from YAML but do not write to MySQL"
    )
    args = parser.parse_args()

    store_path = os.path.abspath(args.store)
    auth_path = os.path.abspath(args.auth) if args.auth else os.path.join(store_path, "auth.yaml")

    if not os.path.isdir(store_path):
        print(f"Error: store path does not exist: {store_path}")
        sys.exit(1)
    if not os.path.isfile(auth_path):
        print(f"Error: auth file not found: {auth_path}")
        sys.exit(1)

    migrate(store_path, auth_path, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
