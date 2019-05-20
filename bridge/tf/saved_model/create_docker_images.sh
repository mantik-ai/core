#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

. ../../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t bridge.tf.saved_model .

docker_push bridge.tf.saved_model
