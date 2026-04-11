#!/usr/bin/python3

from flask import Flask, request, jsonify, g
from datetime import datetime, timedelta, timezone
import copy
import pytz
import yaml
import os
import ai
from waitress import serve

from spell import spell
from models import reminder, config
from data import yamldatasource
from data import mysqldatasource
from auth import auth

app = Flask(__name__)

MYSQL_DB="mysql"
DB_IMPLEMENTATION=MYSQL_DB
SKIP_SPELL=True

def get_secure_key():
    securePath = os.getenv("NUDGE_SECURE_KEY_PATH", "")
    with open(securePath, "r") as f:
        key = f.read().strip()
        assert key != "", "Empty Key"
    return key

def getCurrentUTCTime():
    return datetime.utcnow().replace(tzinfo=timezone.utc)

def recent(reminder):
    current_utc_time = getCurrentUTCTime()
    return (reminder.time - current_utc_time).total_seconds() >= 0

def unpack_repeating(reminders):
    """Expand repeating reminders into individual instances up to 16 weeks from now.

    For old repeating reminders we skip directly to the first occurrence that
    falls near the present, so future instances always appear regardless of how
    long ago the original event was created.
    """
    now = getCurrentUTCTime()
    cutoff = now + timedelta(weeks=16)
    result = []
    for r in reminders:
        if r.repeat == 0:
            result.append(r)
            continue
        # Jump ahead to the occurrence just before 'now' so we don't iterate
        # through hundreds of past instances for old repeating reminders.
        if r.time < now:
            elapsed_days = (now - r.time).total_seconds() / 86400
            n_start = max(0, int(elapsed_days / r.repeat) - 1)
        else:
            n_start = 0
        n = n_start
        while True:
            instance_time = r.time + timedelta(days=n * r.repeat)
            if instance_time > cutoff:
                break
            instance = copy.copy(r)
            instance.time = instance_time
            if n > 0:
                instance.id = f"{r.id}_r{n}"
            result.append(instance)
            n += 1
    return result

@app.route('/login', methods=['POST'])
def login():
    data = request.json or {}

    username = (data.get("Username") or "").strip()
    password = (data.get("Password") or "").strip()

    # change "@" to "_AT_"
    username = username.replace("@", "_AT_")

    if not username or not password:
        return jsonify({"message": "Missing Username/Password"}), 400

    # Verify user/password against store/auth.yaml
    if not auth.verify_password(username, password):
        return jsonify({"message": "Invalid credentials"}), 401

    # Mint a NEW long random token and store its sha256 in auth.yaml
    raw_token, token_sha = auth.issue_token(username)
    if not raw_token:
        return jsonify({"message": "User not found"}), 404

    # App should store `raw_token` and send it as X-Nudge-Token on every request
    return jsonify({
        "username": username,
        "token": raw_token,
    }), 200

@app.route('/reminders', methods=['GET'])
@auth.require_user
def get_reminders():

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    include = request.args.get("include", "none")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    user = g.username
    enc_key = auth.get_enc_key(user)

    if DB_IMPLEMENTATION == MYSQL_DB:
        datasource = mysqldatasource.MySQLDataSource(cfg, user, True, enc_key)
    else:
        datasource = yamldatasource.YamlDataSource(cfg, user, True, enc_key)

    reminders = datasource.loadReminders()
    for r in reminders:
        if cfg.getTimeZone():
            local_timezone= pytz.timezone(cfg.getTimeZone())
            r.time = r.time.astimezone(local_timezone)
            r.read = r.read.astimezone(local_timezone)
        else:
            # TimeZone not set fall back to offset
            offset = timedelta(hours=cfg.getTimeZoneOffset())
            r.time = r.time.astimezone(timezone(offset))
            r.read = r.read.astimezone(timezone(offset))

    reminders = unpack_repeating(reminders)

    try:
        days = int(include)
        cutoff = getCurrentUTCTime() - timedelta(days=days)
        return jsonify({ "reminders": [ r.toInfo() for r in reminders if r.time >= cutoff ] }), 200
    except (ValueError, TypeError):
        return jsonify({ "reminders": [ r.toInfo() for r in reminders if recent(r) ] }), 200

def replaceWords(speller, string, preservedWords):
    tokens = string.split()
    for i in range(len(tokens)):
        if SKIP_SPELL:
            continue
        if tokens[i].lower() in preservedWords:
            continue
        tokens[i] = speller.spell(tokens[i])
    return " ".join(tokens)

@app.route('/delete_reminder', methods=['POST'])
@auth.require_user
def delete_reminder():

    data = request.json
    reminderId = data.get("Id", "")

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    user = g.username
    enc_key = auth.get_enc_key(user)

    if DB_IMPLEMENTATION == MYSQL_DB:
        datasource = mysqldatasource.MySQLDataSource(cfg, user, True, enc_key)
    else:
        datasource = yamldatasource.YamlDataSource(cfg, user, True, enc_key)
    reminders = datasource.loadReminders()
    filtered = []
    for r in reminders:
        if r.id == reminderId:
            continue
        filtered.append(r)

    datasource.storeReminders(filtered)
    if len(filtered) < len(reminders):
        return jsonify({"message": "Reminder removed successfully!"}), 200
    return jsonify({"message": "No reminder found"}), 500

@app.route('/add_reminder', methods=['POST'])
@auth.require_user
def add_reminder():

    data = request.json

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    user = g.username
    enc_key = auth.get_enc_key(user)

    if DB_IMPLEMENTATION == MYSQL_DB:
        datasource = mysqldatasource.MySQLDataSource(cfg, user, True, enc_key)
    else:
        datasource = yamldatasource.YamlDataSource(cfg, user, True, enc_key)
    reminders = datasource.loadReminders()
    speller = spell.CustomSpeller()

    info = {}
    info["Title"] = replaceWords(speller, data["Title"], cfg.getPreservedWords())
    info["Description"] = replaceWords(speller, data["Description"], cfg.getPreservedWords())
    date = data["Date"]
    time = data["Time"]
    id = data.get("Id", "")
    secureKey = data["Key"]
    if secureKey != get_secure_key():
        return jsonify({"message": "Key mismatch!"}), 401

    if cfg.getTimeZone():
        local = pytz.timezone(cfg.getTimeZone())
        defaulttime = datetime.strptime(date + " " + time, "%Y-%m-%d %H:%M:%S")
        localtime = local.localize(defaulttime)
        utc_time = localtime.astimezone(pytz.utc)
    else:
        # TimeZone not set fall back to offset
        offset = timedelta(hours=cfg.getTimeZoneOffset())
        localtime = datetime.strptime(date + " " + time, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone(offset))
        utc_time = localtime.astimezone(timezone.utc)

    info["Time"] = utc_time.strftime("%Y-%m-%d %H:%M:%S")
    info["Link"] = data["Link"]
    info["Priority"] = int(data["Priority"])
    info["Closed"] = False
    info["Snooze"] = data.get("Snooze", 0)
    info["Repeat"] = int(data.get("Repeat", 0))
    info["Id"] = id
    r = reminder.Reminder(info)
    orig_reminders = reminders[:]
    reminders = []
    for orig_r in orig_reminders:
        if orig_r.id == r.id:
            # skip reminder being updated
            continue
        else:
            reminders.append(orig_r)

    reminders.append(r)
    datasource.storeReminders(reminders)

    return jsonify({"message": "Reminder added successfully!"}), 200

@app.route('/add_reminder_ai', methods=['POST'])
@auth.require_user
def add_reminder_ai():

    data = request.json or {}
    free_text = data.get("Text", "").strip()
    secureKey = data.get("Key", "")

    if not free_text:
        return jsonify({"message": "Missing Text"}), 400

    if secureKey != get_secure_key():
        return jsonify({"message": "Key mismatch!"}), 401

    # Load config
    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    user = g.username
    enc_key = auth.get_enc_key(user)

    if DB_IMPLEMENTATION == MYSQL_DB:
        datasource = mysqldatasource.MySQLDataSource(cfg, user, True, enc_key)
    else:
        datasource = yamldatasource.YamlDataSource(cfg, user, True, enc_key)
    reminders = datasource.loadReminders()
    speller = spell.CustomSpeller()

    # 1) Ask Gemini to parse free text into structured fields
    ai_r = ai.parse_reminder_from_text(free_text, cfg)

    # 2) Build the same "info" dict shape you already store
    info = {}
    info["Title"] = replaceWords(speller, ai_r["Title"], cfg.getPreservedWords())
    info["Description"] = replaceWords(speller, ai_r["Description"], cfg.getPreservedWords())

    # AI gives local Date + Time
    date = ai_r["Date"]               # yyyy-mm-dd
    time = ai_r["Time"]               # HH:MM:SS

    if cfg.getTimeZone():
        local = pytz.timezone(cfg.getTimeZone())
        defaulttime = datetime.strptime(date + " " + time, "%Y-%m-%d %H:%M:%S")
        localtime = local.localize(defaulttime)
        utc_time = localtime.astimezone(pytz.utc)
    else:
        offset = timedelta(hours=cfg.getTimeZoneOffset())
        localtime = datetime.strptime(date + " " + time, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone(offset))
        utc_time = localtime.astimezone(timezone.utc)

    info["Time"] = utc_time.strftime("%Y-%m-%d %H:%M:%S")

    # Template rules you specified
    info["Closed"] = False
    info["Priority"] = 2
    info["Snooze"] = 2
    info["Id"] = ""          # leave empty
    info["Link"] = ai_r.get("Link", "") or ""
    info["Read"] = utc_time.strftime("%Y-%m-%d %H:%M:%S")  # "match the time"

    r = reminder.Reminder(info)

    # Remove any existing reminder with same ID (yours uses ID for updates).
    # Since ID is empty here, it will behave like "append new" unless your model assigns one.
    orig_reminders = reminders[:]
    reminders = []
    for orig_r in orig_reminders:
        if orig_r.id == r.id and r.id != "":
            continue
        reminders.append(orig_r)

    reminders.append(r)
    datasource.storeReminders(reminders)

    return jsonify({"message": "Reminder added successfully!"}), 200

@app.route('/signup', methods=['POST'])
def signup():
    data = request.json or {}
    username = (data.get("Username") or "").strip()
    password = (data.get("Password") or "").strip()
    password2 = (data.get("PasswordConfirm") or "").strip()

    # change "@" to "_AT_"
    username = username.replace("@", "_AT_")

    if not username or not password:
        return jsonify({"message": "Missing Username/Password"}), 400
    if password != password2:
        return jsonify({"message": "Password mismatch"}), 400

    pw_err = auth.validate_password(password)
    if pw_err:
        return jsonify({"message": pw_err}), 400

    user_obj, err = auth.create_user(username, password)
    if err:
        return jsonify({"message": err}), 409

    token, _ = auth.issue_token(username)
    return jsonify({"username": username, "token": token}), 200

@app.route('/delete_account', methods=['POST'])
@auth.require_user
def delete_account():
    username = g.username

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    enc_key = auth.get_enc_key(username)
    if DB_IMPLEMENTATION == MYSQL_DB:
        datasource = mysqldatasource.MySQLDataSource(cfg, user, True, enc_key)
    else:
        datasource = yamldatasource.YamlDataSource(cfg, user, True, enc_key)
    datasource.deleteUser()

    err = auth.delete_user(username)
    if err:
        return jsonify({"message": err}), 500

    return jsonify({"message": "Account deleted successfully"}), 200

@app.route('/change_password', methods=['POST'])
@auth.require_user
def change_password():
    data = request.json or {}
    current_password = (data.get("CurrentPassword") or "").strip()
    new_password = (data.get("NewPassword") or "").strip()
    confirm_password = (data.get("ConfirmPassword") or "").strip()

    if not current_password or not new_password or not confirm_password:
        return jsonify({"message": "All password fields are required"}), 400
    if new_password != confirm_password:
        return jsonify({"message": "New passwords do not match"}), 400

    err = auth.change_password(g.username, current_password, new_password)
    if err:
        return jsonify({"message": err}), 400

    return jsonify({"message": "Password changed successfully"}), 200

if __name__ == "__main__":
    # app.run(debug=True)
    get_secure_key()
    serve(app, host='0.0.0.0', port=5000)
