#!/usr/bin/env bash
MYDIR=`dirname $0`
cd $MYDIR
gofmt -w .
go build

