#!/usr/bin/env bash
# Starts Mantik Engine
# Parameters are passed to it

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

./engine-app/target/universal/stage/bin/engine-app $"$@"

