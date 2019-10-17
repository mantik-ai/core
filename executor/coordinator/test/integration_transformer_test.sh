#!/usr/bin/env bash


#
# A Simple local integration test, which does a learn transform network
#

set -e
MYDIR=`dirname $0`
cd $MYDIR/..

./target/executor_sample_source -port 50001 &
SOURCE=$!

./target/executor_sample_sink -port 50002  &
SINK=$!

./target/executor_sample_transformer -port 50003 &
TRANSFORMER=$!



./target/coordinator sidecar -shutdown -port 50501 -url http://localhost:50001 &
SOURCE_SIDECAR=$!

./target/coordinator sidecar -shutdown -port 50502 -url http://localhost:50002 &
SINK_SIDECAR=$!

./target/coordinator sidecar -shutdown -port 50503 -url http://localhost:50003 &
TRANSFORMER_SIDECAR=$!

export COORDINATOR_IP="127.0.0.1"

./target/coordinator coordinator -port 50505 -plan @test/transformer_plan.json &
COORDINATOR=$!

trap "pkill -P $$ || true" EXIT # Kills children processes at the end


wait $COORDINATOR
wait $SINK_SIDECAR
wait $SOURCE_SIDECAR
wait $TRANSFORMER_SIDECAR
