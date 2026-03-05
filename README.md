# Nudge App

## App Details

Simple reminder/notification app for everyday use.

### Demo

[Nudge demo](https://drive.google.com/file/d/1jBkPdaATrMwrStPJbjUniXb2p3BQxaiy/view?usp=sharing)

### Work flow

- Create a reminder manually or using AI prompt
- Set priority and time to be reminded
- Check future reminders, update them
- Get reminders via in-app notifications
- Advanced: you can configure to send email/SMS, but requires manual backend setup.

### Screenshots

<img src="https://github.com/hyunwookshin/nudge/blob/main/images/reminder_notification.png?raw=true" alt="Screenshot of the notification" width="250"/>
<img src="https://github.com/hyunwookshin/nudge/blob/main/images/reminder_login.png?raw=true" alt="Screenshot of the login page" width="250"/>
<img src="https://github.com/hyunwookshin/nudge/blob/main/images/reminder_screenshot.png?raw=true" alt="Screenshot of the reminder page" width="250"/>
<img src="https://github.com/hyunwookshin/nudge/blob/main/images/reminders_dark_screenshot.png?raw=true" alt="Screenshot of the reminders page" width="250"/>

## Adding new reminders

Build and install the android app, and create reminder directly from the app.

# Deployment Guide

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
pip3 install openai
pip3 install bcrypt
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
     location /add_reminder_ai {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /delete_reminder {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /reminders {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /login {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /signup {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     location /delete_account {
         proxy_pass http://127.0.0.1:5000;
         proxy_set_header X-Real-IP $remote_addr;
     }
     # For desktop browser access
     location ^~ /public/nudge/web/ {
         auth_basic "Restricted Content";
         auth_basic_user_file /etc/nginx/.htpasswd;
     }
}
```

And run the server

```
OPENAI_API_KEY=. NUDGE_STORE_PATH=. NUDGE_SECURE_KEY_PATH=... NUDGE_CONFIG_PATH=./config.yaml ./server.py
```

The `NUDGE_SERVER_SECURE_PATH` should be where the secure key is stored.

## Sending reminders from Server-side

The server-side reminders are optional, as app notifications should be sufficient.

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

### Configuring android and web clients

You need to set `nudge_hostname` and `nudge_api_key` in `client/android`
and `client/web` (only hostname).

```
echo "https://<hostname>.com" > client/android/nudge_hostname
...
make config
```
