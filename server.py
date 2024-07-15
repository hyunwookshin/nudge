#!/usr/bin/python3

from flask import Flask, request, jsonify
from datetime import datetime, timedelta, timezone
import pytz
import yaml
import os
from waitress import serve

from models import reminder, config
from data import yamldatasource

app = Flask(__name__)

@app.route('/add_reminder', methods=['POST'])
def add_reminder():
    data = request.json

    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg)
    reminders = datasource.loadReminders()

    info = {}
    info["Title"] = data["Title"]
    info["Description"] = data["Description"]
    date = data["Date"]
    time = data["Time"]

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
    r = reminder.Reminder(info)

    reminders.append(r)
    datasource.storeReminders(reminders)

    return jsonify({"message": "Reminder added successfully!"}), 200

if __name__ == "__main__":
    # app.run(debug=True)
    serve(app, host='0.0.0.0', port=5000)
