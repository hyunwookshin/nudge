
## Dependency

```
curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
python3 get-pip.py
pip3 install pyyaml
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
