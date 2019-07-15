#!/usr/bin/env bash


#
# A Simple local integration test, which does a learn like network
#

set -e
MYDIR=`dirname $0`
cd $MYDIR/..

./target/executor_sample_source -port 50001 &
SOURCE=$!

./target/executor_sample_sink -port 50002  &
SINK1=$!

./target/executor_sample_sink -port 50003  &
SINK2=$!

./target/executor_sample_learner -port 50004 &
LEARNER=$!


./target/coordinator sidecar -shutdown -port 50501 -url http://localhost:50001 &
SOURCE_SIDECAR=$!

./target/coordinator sidecar -shutdown -port 50502 -url http://localhost:50002 &
SINK1_SIDECAR=$!

./target/coordinator sidecar -shutdown -port 50503 -url http://localhost:50003 &
SINK2_SIDECAR=$!

./target/coordinator sidecar -shutdown -port 50504 -url http://localhost:50004 &
LEARNER_SIDECAR=$!


export COORDINATOR_IP="127.0.0.1"

./target/coordinator coordinator -port 50505 -plan @test/learn_plan.json &
COORDINATOR=$!

cleanup() {
    echo "Killing pending processes..."
    kill $SOURCE || true
    kill $SINK1 || true
    kill $SINK2 || true
    kill $LEARNER|| true
    kill $SOURCE_SIDECAR || true
    kill $SINK1_SIDECAR || true
    kill $SINK2_SIDECAR || true
    kill $LEARNER_SIDECAR || true
    kill $COORDINATOR || true
}

trap cleanup EXIT


wait $COORDINATOR
wait $SINK1_SIDECAR
wait $SINK2_SIDECAR
wait $LEARNER_SIDECAR
