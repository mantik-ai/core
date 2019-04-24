#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t bridge.sklearn.simple .

docker_push bridge.sklearn.simple
