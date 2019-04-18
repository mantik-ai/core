#!/usr/bin/env bash
# Update the requirements.txt file colleagues and docker likes
set -e
MYDIR=`dirname $0`
cd $MYDIR
pipenv lock -r > requirements.txt
