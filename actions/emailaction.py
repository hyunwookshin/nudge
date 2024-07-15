import smtplib
import os
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from actions import acts

class EmailAction(acts.Action):
    def __init__(self, config, high_priority, test_server=None):
        self.sender = config.getEmail().getSender()
        self.password = config.getEmail().getPassword()
        self.recepient_low = config.getEmail().getRecepientLow()
        self.recepient_high = config.getEmail().getRecepientHigh()
        self.server = test_server
        self.high_priority = high_priority

    def actuate(self, reminder, dryrun=False):
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
            self.server.starttls()  # Enable security
            self.server.login(self.sender, self.password)

            # Send the email
            text = message.as_string()
            recepient = self.recepient_high if self.high_priority else self.recepient_low
            self.server.sendmail(self.sender, recepient, text)

            # Close the server connection
            self.server.quit()

            print("Email sent successfully!")
        except Exception as e:
            print(f"Failed to send email: {e}")
