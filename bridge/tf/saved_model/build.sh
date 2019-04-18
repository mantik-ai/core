#!/usr/bin/env bash
set -e

MYDIR=`dirname $0`
cd $MYDIR

./pull_dependencies.sh

DIR_OVERRIDDEN=true
source ./prepare_run_flags.sh
echo "Reformatting..."
gofmt -w .
echo "Building..."
go build -o tfbridge
