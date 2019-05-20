#!/usr/bin/env bash
set -e

MYDOR=`dirname $0`
cd $MYDOR

./tf/saved_model/test.sh
./binary/test.sh
./bridge_debugger/test.sh
./select/test.sh
