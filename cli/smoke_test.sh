#!/usr/bin/env bash
MYDIR=`dirname $0`
cd $MYDIR
set -e

# ** Some smoke tests against a running engine **
./target/mantik version
./target/mantik items

# ** Uploading **
# Item without payload
./target/mantik add --name user/calculation1 ../bridge/select/examples/calculation1/
# Item with payload
./target/mantik --debug add  --name mnist1 ../bridge/binary/test/mnist
./target/mantik item mnist1

# Downloading
rm -rf target/test
mkdir -p target/test

./target/mantik --debug extract -o target/test/mnist_download mnist1
./target/mantik --debug extract -o target/test/calculaion1_download user/calculation1
