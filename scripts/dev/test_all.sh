#!/usr/bin/env bash
# Test everything, assumed that it's build

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

echo "Test Scala Code"
sbt test

echo "Test Bridge Code"
pushd bridge

pushd binary
./test.sh
popd

pushd sklearn/simple_learn
./test.sh
popd

pushd tf/saved_model
./test.sh
popd

pushd tf/train
./test.sh
popd

pushd "select"
./test.sh
popd

popd

echo "Test Coordinator Code"
pushd executor/coordinator
./test.sh
popd




