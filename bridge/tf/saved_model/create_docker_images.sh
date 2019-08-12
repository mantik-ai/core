#!/usr/bin/env sh

set -e

MYDIR=`dirname $0`
cd $MYDIR
echo "MYDIR $PWD"

# We can't use the local build executable, as we need Tensorflow C-Libraries
# and they are not buildable on Mac for Linux.
# So we are rebuilding on docker

# Step 1 Preparing a Build directory
rm -rf docker_build
mkdir -p docker_build
cp -r ../../../go_shared docker_build

. ../../../scripts/ci/docker_help.sh

$DOCKER_CALL build -t bridge.tf.saved_model .

docker_push bridge.tf.saved_model
