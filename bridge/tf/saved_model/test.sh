#!/usr/bin/env bash
set -e

MYDIR=`dirname $0`
cd $MYDIR

DIR_OVERRIDDEN=true
source ./prepare_run_flags.sh


go test -v ./...
