#!/usr/bin/python3

from autocorrect import Speller

class CustomSpeller():

    def spell(self, string):
        tokens = string.split()
        internal = Speller()
        corrected = internal(string).split()

        output = []
        for i in range(len(tokens)):
            if tokens[i][1:].lower() != tokens[i][1:]:
                output.append(tokens[i])
            else:
                output.append(corrected[i])
        return " ".join(output)
