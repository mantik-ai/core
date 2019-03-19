#!/usr/bin/env bash


#
# A Simple local integration test, which copies data from A to B
#

set -e
MYDIR=`dirname $0`
cd $MYDIR/..

./target/executor_sample_source -port 50001 &
SOURCE=$!
./target/executor_sample_sink -port 50002  &
SINK=$!

./target/coordinator sidecar -shutdown -port 50501 -url http://localhost:50001 &
SIDECAR1=$!

./target/coordinator sidecar -shutdown -port 50502 -url http://localhost:50002 &
SIDECAR2=$!


export COORDINATOR_PLAN='{"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}},"flows":[[{"node": "A", "resource": "out"}, {"node": "B", "resource": "in"}]]}'
export COORDINATOR_IP="127.0.0.1"

./target/coordinator coordinator -port 50503 &
COORDINATOR=$!

cleanup() {
    echo "Killing pending processes..."
    kill $SOURCE || true
    kill $SINK || true
    kill $SIDECAR1 || true
    kill $SIDECAR2 || true
    kill $COORDINATOR || true
    # kill children
}

trap cleanup EXIT


wait $COORDINATOR
wait $SIDECAR1
wait $SIDECAR2
