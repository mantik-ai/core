#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

export MANTIK_ROOT=./../../../
. $MANTIK_ROOT/scripts/ci/python_help.sh
. $MANTIK_ROOT/scripts/ci/docker_help.sh


python_prepare_pip_build

$DOCKER_CALL build -t bridge.sklearn.simple .
