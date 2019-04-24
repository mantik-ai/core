#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t binary_bridge .

docker_push binary_bridge
