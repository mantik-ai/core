#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR

source ././../../scripts/ci/golang_help.sh
golang_build binary_bridge