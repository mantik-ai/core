#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../scripts/ci/docker_help.sh

docker_build bridge.select -f ../../scripts/ci/Dockerfile.go_bridge_simple --build-arg input_executable=target/select_bridge_linux
docker_push bridge.select
