#!/usr/bin/python3

from flask import Flask, request, jsonify
from datetime import datetime, timedelta, timezone
import pytz
import yaml
import os
from waitress import serve

from spell import spell
from models import reminder, config
from data import yamldatasource

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

@app.route('/reminders', methods=['GET'])
def get_reminders():
    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg)
    reminders = datasource.loadReminders()
    for r in reminders:
        if cfg.getTimeZone() and False:
            local_timezone= pytz.timezone(cfg.getTimeZone())
            r.time = r.time.astimezone(local_timezone)
        else:
            # TimeZone not set fall back to offset
            offset = timedelta(hours=cfg.getTimeZoneOffset())
            r.time = r.time.astimezone(timezone(offset))

    return jsonify({ "reminders" : [ r.toInfo() for r in reminders if recent(r) ] }), 200

@app.route('/add_reminder', methods=['POST'])
def add_reminder():
    data = request.json

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg)
    reminders = datasource.loadReminders()
    speller = spell.CustomSpeller()

    info = {}
    info["Title"] = speller.spell(data["Title"])
    info["Description"] = speller.spell(data["Description"])
    date = data["Date"]
    time = data["Time"]
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
    r = reminder.Reminder(info)

    reminders.append(r)
    datasource.storeReminders(reminders)

    return jsonify({"message": "Reminder added successfully!"}), 200

if __name__ == "__main__":
    # app.run(debug=True)
    get_secure_key()
    serve(app, host='0.0.0.0', port=5000)
