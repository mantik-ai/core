#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo "Creating Images for Bridge Code"

pushd bridge

pushd binary
./create_docker_images.sh
popd

pushd sklearn/simple_learn
./create_docker_images.sh
popd

pushd "select"
./create_docker_images.sh
popd


pushd tf/saved_model
./create_docker_images.sh
popd

pushd tf/train
./create_docker_images.sh
popd

popd

echo "Building Images for Coordinator"

pushd executor/coordinator
./create_docker_images.sh
popd
