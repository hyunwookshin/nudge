# Nudge App

## App Details

Simple reminder/notification app for everyday use.

### Work flow

- Create a reminder
- Set priority and time to be reminded
- Check future reminders
- Get reminders via email/SMS depending on the priorities

### Screenshots

<img src="https://github.com/hyunwookshin/nudge/blob/main/images/reminder_screenshot.jpg?raw=true" alt="Screenshot of the reminder page" width="250"/>
<img src="https://github.com/hyunwookshin/nudge/blob/main/images/schedule_screenshot.jpg?raw=true" alt="Screenshot of the reminders/schedule page" width="250"/>

## Adding new reminders

### Via App

Build and install the android app, and create reminder directly from the app.

### Via CLI

```
export NUDGE_CONFIG_PATH=./config.yaml
export NUDGE_STORE_PATH=store
./remind [-d]
```

### Via Curl

As shown in the `client/remind.sh` run this curl command:
```
curl -k -X POST https://<url>/add_reminder \
   -H Content-Type:application/json \
   -d '{
      "Title": "Meeting with team",
      "Description": "Discuss project status",
      "Date": "2024-01-01",
      "Time": "17:00:00",
      "Link": "http://google.com",
      "Priority": 2
   }'

curl -k -X GET https://<url>/reminders \
   -H Content-Type:application/json
```

## Set up

### Dependency

```
curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
python3 get-pip.py
pip3 install pyyaml
pip3 install pytz
pip3 install flask
pip3 install waitress
pip3 install autocorrect
```

### Set up the config

- Fill out `config.yaml.tpt` and save it as `config.yaml`, by running `make all`
- Fill out `store/reminders.yaml.tpt` and save it as `config.yaml`, by running `make all`

### Activating Sender Email

Refer to https://www.youtube.com/watch?v=Y_u5KIeXiVI

- Go to "Manage account" under Account Mavatar
- Click on Security, and set up Two-Factor-Authentication (Required)
- Then search for "App Password"
- Use "Nudge" as the app name

## Server Config

This requires the following nginx settings:

```
 server {
     listen       443 ssl http2 default_server;
     listen       [::]:443 ssl http2 default_server;
     server_name  _;
     root         /usr/share/nginx/html;
     autoindex on;

     ssl_certificate "/etc/pki/nginx/server.crt";
     ssl_certificate_key "/etc/pki/nginx/private/server.key";
     ssl_session_cache shared:SSL:1m;
     ssl_session_timeout  10m;
     ssl_ciphers HIGH:!aNULL:!MD5;
     ssl_prefer_server_ciphers on;
     # Nudge
     location /add_reminder {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /reminders {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
}
```

And run the server

```
NUDGE_SECURE_KEY_PATH=... NUDGE_CONFIG_PATH=config.yaml ./server.py
```

The `NUDGE_SERVER_SECURE_PATH` should be where the secure key is stored.

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

### cron job

```
*/10 * * * * cd ~/wses/nudge && make job
```

### Committing

```
./commit.sh
```

### Configuring android cllient

```
make config
```
