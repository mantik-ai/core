#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR
go test -v ./...

echo "Starting integration tests..."
./test/integration_test.sh
./test/integration_error_return_test.sh
./test/integration_cancellation_test.sh
./test/integration_learn_test.sh
./test/integration_transformer_test.sh
