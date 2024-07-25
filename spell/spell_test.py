#!/usr/bin/python3

import unittest

from spell import CustomSpeller

class TestCustomSpeller(unittest.TestCase):

    def test_speller(self):
        string = "I was headign to thh Stroe today, call this NUMBRE"
        speller = CustomSpeller()
        corrected = speller.spell(string)

        self.assertEqual("I was heading to the Store today, call this NUMBRE", corrected)

    def test_common(self):
        string = "Doctre's visat"
        speller = CustomSpeller()
        corrected = speller.spell(string)

        self.assertEqual("Doctor's visit", corrected)

if __name__ == "__main__":
    unittest.main()
