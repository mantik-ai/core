#!/usr/bin/env bash

# Prepares the target directory

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

export MANTIK_ROOT=./../../../

. $MANTIK_ROOT/scripts/ci/python_help.sh

python_build_standard
