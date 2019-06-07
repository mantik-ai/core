#!/usr/bin/env bash
set -e
if [ -x "$(command -v pipenv)" ]; then
    echo "Using pipenv..."
    pipenv install # automatically uses requirements.txt
    RUN="pipenv run"
else
    echo "Using Pip..."
    pip install -r requirements.txt
    RUN=""
fi

pip install -r requirements.txt
rm -rf index.rst
cp ../Readme.rst index.rst
$RUN sphinx-build -b html . ../public
