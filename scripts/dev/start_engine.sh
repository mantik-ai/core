#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

sbt engine/run