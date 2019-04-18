#!/usr/bin/env bash
MYDIR=`dirname $0`
cd $MYDIR

go test -v ./...
