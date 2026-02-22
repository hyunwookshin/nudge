#!/usr/bin/python3

from flask import Flask, request, jsonify
from datetime import datetime, timedelta, timezone
import pytz
import yaml
import os
import ai
from waitress import serve

from spell import spell
from models import reminder, config
from data import yamldatasource
from auth import auth

app = Flask(__name__)

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

@app.route('/login', methods=['POST'])
def login():
    data = request.json or {}
    username = (data.get("Username") or "").strip()
    password = data.get("Password") or ""

    if not username or not password:
        return jsonify({"message": "Missing Username/Password"}), 400

    db = auth.load_auth_db()
    user = db["users"].get(username)
    if not user:
        return jsonify({"message": "Invalid credentials"}), 401

    stored_bcrypt = user.get("password_bcrypt", "")
    if not stored_bcrypt:
        return jsonify({"message": "User not configured"}), 500

    if not auth.verify_password(stored_bcrypt, password):
        return jsonify({"message": "Invalid credentials"}), 401

    raw_token = auth.issue_token()
    auth.store_token_for_user(db, username, raw_token)
    auth.save_auth_db(db)

    return jsonify({"token": raw_token, "username": username}), 200

@app.route('/reminders', methods=['GET'])
def get_reminders():
    user, err = auth.require_user()
    if err: return err

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    include = request.args.get("include", "none")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg, user)
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

    if include == "all":
        return jsonify({ "reminders" : [ r.toInfo() for r in reminders ] }), 200
    return jsonify({ "reminders" : [ r.toInfo() for r in reminders if recent(r) ] }), 200

def replaceWords(speller, string, preservedWords):
    tokens = string.split()
    for i in range(len(tokens)):
        if tokens[i].lower() in preservedWords:
            continue
        tokens[i] = speller.spell(tokens[i])
    return " ".join(tokens)

@app.route('/delete_reminder', methods=['POST'])
def delete_reminder():
    user, err = auth.require_user()
    if err: return err

    data = request.json
    reminderId = data.get("Id", "")

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg, user)
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
def add_reminder():
    user, err = auth.require_user()
    if err: return err

    data = request.json

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg, user)
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
def add_reminder_ai():
    user, err = auth.require_user(cfg)
    if err: return err

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

    datasource = yamldatasource.YamlDataSource(cfg, user)
    reminders = datasource.loadReminders()
    speller = spell.CustomSpeller()

    # 1) Ask OpenAI to parse free text into structured fields
    ai_r = ai.parse_reminder_from_text_openai(free_text, cfg)

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

if __name__ == "__main__":
    # app.run(debug=True)
    get_secure_key()
    serve(app, host='0.0.0.0', port=5000)
