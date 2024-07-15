import os

class Email():
    def __init__(self, info):
        self.sender = info["Sender"]
        self.password = os.getenv("NUDGE_EMAIL_PASSWD")
        self.recepient_low = info["RecepientLow"]
        self.recepient_high = info["RecepientHigh"]

    def getSender(self):
        return self.sender

    def getPassword(self):
        return self.password
    
    def getRecepientLow(self):
        return self.recepient_low

    def getRecepientHigh(self):
        return self.recepient_high

class Config():
    def __init__(self, info):
        self.email = Email(info["Email"])
        self.store_path = os.getenv("NUDGE_STORE_PATH")
        self.timezone_offset = info["TimeZoneOffset"]

    def getEmail(self):
        return self.email

    def getStorePath(self):
        return self.store_path

    def getTimeZoneOffset(self):
        return self.timezone_offset
