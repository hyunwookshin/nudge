#!/usr/bin/env bash

set -e

make clean
if [ "$(uname)" == "Darwin" ]; then
    make -C client/android clean
else
    make test
fi
git add .
git commit
