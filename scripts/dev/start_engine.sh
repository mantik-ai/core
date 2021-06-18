#!/usr/bin/env bash
# Starts Mantik Engine
# Parameters are passed to it
# You can set env variable FAST and

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

if [[ ! -z "$FAST" ]]; then
  echo "Using Fast Start mode (without rebuilding)"
  ./engine-app/target/universal/stage/bin/engine-app $"$@"
else
  echo "Using Slow Start mode (with rebuilding), FAST is not set"
  sbt "$@" engineApp/run
fi

