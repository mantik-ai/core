#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../scripts/ci/docker_help.sh

docker_build bridge.binary
docker_push bridge.binary
