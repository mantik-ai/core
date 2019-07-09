#!/usr/bin/env bash
set -e
if [ -x "$(command -v pipenv)" ]; then
    echo "Using pipenv..."
    pipenv --python 3 install # automatically uses requirements.txt
    RUN="pipenv run"
else
    echo "Using Pip..."
    pip install -r requirements.txt
    RUN=""
fi

rm -rf index.rst
cp ../Readme.rst index.rst
$RUN sphinx-build -b html . ../public
