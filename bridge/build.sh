#!/usr/bin/env bash
set -e

MYDIR=`dirname $0`
cd $MYDIR

./tf/saved_model/build.sh
