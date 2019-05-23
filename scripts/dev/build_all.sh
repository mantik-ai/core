#!/usr/bin/env bash
# Build (but not test) everything

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo "Build Scala Code"
sbt test:compile

echo "Building Bridge Code"
pushd bridge

pushd binary
./build.sh
popd

pushd tf/saved_model
./build.sh
popd

pushd tf/train
./build.sh
popd

pushd sklearn/simple_learn
./build.sh
popd

pushd bridge_debugger
./build.sh
popd

pushd "select"
./build.sh
popd

popd

echo "Building Coordinator Code"
pushd executor/coordinator
./build.sh
popd




