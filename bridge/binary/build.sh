#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR
gofmt -w .

mkdir -p target

echo "Building main application"
go build -o target/binary_bridge main.go

echo "Building Linux Application"
CGO_ENABLED=0 GOOS=linux go build -a -o target/binary_bridge_linux main.go
