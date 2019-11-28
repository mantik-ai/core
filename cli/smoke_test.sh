#!/usr/bin/env bash
MYDIR=`dirname $0`
cd $MYDIR
set -e
set -x

# ** Some smoke tests against a running engine **
./target/mantik version
./target/mantik items

# ** Uploading **
# Item without payload
./target/mantik add --name user/calculation1 ../bridge/select/examples/calculation1/
# Item with payload
./target/mantik --debug add  --name mnist1 ../bridge/binary/test/mnist
./target/mantik item mnist1

# Tagging
./target/mantik tag mnist1 nob/mnist1
./target/mantik item nob/mnist1

# Downloading
rm -rf target/test
mkdir -p target/test

./target/mantik --debug extract -o target/test/mnist_download mnist1
./target/mantik --debug extract -o target/test/calculaion1_download user/calculation1

# You can enable this, if you have a running repo online with account nob/abcd
if [ -n "$ENABLE_REMOTE_TEST" ]; then
   echo "Doing remote tests"
  ./target/mantik login -u nob -p abcd
  ./target/mantik version # should show login state
  ./target/mantik push nob/mnist1
  ./target/mantik pull nob/mnist1
  ./target/mantik logout
else
  echo "Skipping remote tests"
fi
