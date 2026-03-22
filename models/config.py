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
        self.timezone = info["TimeZone"]
        self.preserved_words = info["PreservedWords"]
        self.mysql_user = os.getenv("NUDGE_MYSQL_USER", "")
        self.mysql_password = os.getenv("NUDGE_MYSQL_PASSWORD", "")

    def getEmail(self):
        return self.email

    def getStorePath(self):
        return self.store_path

    def getTimeZoneOffset(self):
        return self.timezone_offset

    def getTimeZone(self):
        return self.timezone

    def getPreservedWords(self):
        return self.preserved_words

    def getMySQLUser(self):
        return self.mysql_user

    def getMySQLPassword(self):
        return self.mysql_password
