
class FakeServer():
    def __init__(self):
        self.tls = False
        self.sender = ""
        self.password = ""
        self.recepient = ""
        self.text = ""

    def starttls(self):
        self.tls = True

    def login(self, sender, password):
        self.sender = sender
        self.password = password

    def sendmail(self, sender, recepient, text):
        assert sender == self.sender, "%s not logged in" % sender
        self.sender = sender
        self.recepient = recepient
        self.text = text

    def quit(self):
        print("TLS:", self.tls)
        print("Sender: %s" % self.sender)
        print("PasswordSet: %s" % self.password)
        print("Recepient: %s" % self.recepient)
        print("Text: %s" % self.text)
