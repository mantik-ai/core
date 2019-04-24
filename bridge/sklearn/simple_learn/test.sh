#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR

pipenv install --dev
pipenv run pytest

