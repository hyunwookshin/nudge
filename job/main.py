#!/usr/bin/python3

import os
import yaml
import argparse

from models import config
from data import yamldatasource
from remind import RemindJob
from actions.priority import getPriorities
from actions.acts import Actions

def parseArgs():
   parser = argparse.ArgumentParser()
   parser.add_argument( "-d", "--dryrun", required=False, action='store_true')
   parser.add_argument( "-a", "--all", help="Consider all reminders as new", required=False, action='store_true')
   return parser.parse_args()

def main(args):
    configPath = os.getenv("NUDGE_CONFIG_PATH", "")
    with open(configPath, "r") as f:
        info = yaml.safe_load(f.read().strip())
    cfg = config.Config(info)

    datasource = yamldatasource.YamlDataSource(cfg)
    reminders = datasource.loadReminders()
    priorities = getPriorities(cfg, args.dryrun)
    actions = Actions(priorities)

    job = RemindJob(datasource, actions)
    job.run(args.all, args.dryrun)

if __name__ == "__main__":
    args = parseArgs()
    main(args)
