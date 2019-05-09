#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../scripts/ci/docker_help.sh

$DOCKER_CALL build -f ../../scripts/ci/Dockerfile.go_bridge_simple --build-arg input_executable=target/select_bridge_linux -t select_bridge .

docker_push select_bridge
