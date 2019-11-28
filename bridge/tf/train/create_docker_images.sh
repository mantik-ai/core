#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

export MANTIK_ROOT=./../../../
. $MANTIK_ROOT/scripts/ci/docker_help.sh

docker_build bridge.tf.train -f $MANTIK_ROOT/scripts/ci/Dockerfile.python_bridge_simple
