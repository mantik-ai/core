#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
source $MYDIR/prepare_run_flags.sh

$MYDIR/target/tfbridge "$@"
