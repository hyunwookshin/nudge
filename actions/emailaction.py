import smtplib
import os
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import time

from actions import acts

class EmailAction(acts.Action):
    def __init__(self, config, high_priority, test_server=None):
        self.sender = config.getEmail().getSender()
        self.password = config.getEmail().getPassword()
        self.recepient_low = config.getEmail().getRecepientLow()
        self.recepient_high = config.getEmail().getRecepientHigh()
        self.server = test_server
        self.high_priority = high_priority
        self.sleep = 0 if test_server else 5

    def actuate(self, reminder, history, dryrun=False):
        # Setup the MIME
        message = MIMEMultipart()
        message['From'] = self.sender
        message['To'] = self.recepient_low
        message['Subject'] = reminder.title

        # Attach the body with the msg instance
        message.attach(MIMEText(reminder.description, 'plain'))

        # Create SMTP session for sending the mail
        try:
            # Setup the server
            if self.server is None:
                self.server = smtplib.SMTP('smtp.gmail.com', 587)
            history.setdefault("email", 0)
            if history["email"] == 0:
                self.server.starttls()  # Enable security
                self.server.ehlo()
                self.server.login(self.sender, self.password)

            # Send the email
            text = message.as_string()
            recepient = self.recepient_high if self.high_priority else self.recepient_low
            self.server.sendmail(self.sender, recepient, text)

            # self.server.quit()
            history["email"] += 1
            time.sleep(self.sleep)

            print("Email sent successfully!")
            super().actuate(reminder)
        except Exception as e:
            print(f"Failed to send email: {e}")
