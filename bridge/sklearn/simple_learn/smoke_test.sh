#!/usr/bin/env bash

set -e
set -x

MYDIR=`dirname $0`
cd $MYDIR

rm -rf example/kmeans/code/model.pickle

pipenv run python main.py example/kmeans/ &
SERVER_PID=$!

cleanup(){
    kill $SERVER_PID || true
}
trap cleanup EXIT

sleep 2 # wait for startup

echo "Testing accessors"
curl localhost:8502
curl localhost:8502/type
curl localhost:8502/training_type
curl localhost:8502/stat_type

echo "Training..."
curl -f -X POST -d '[[[1,1]],[[1,2]]]' localhost:8502/train

echo "Collecting result"
curl -f -H "Accept: application/x-mantik-bundle-json" localhost:8502/stats
curl -f -H "Accept: application/x-mantik-bundle" localhost:8502/stats # This was once crashing
curl -f localhost:8502/result > /dev/null

echo "Using trained mode"
curl -f -X POST -d '[[[1,2]]]' localhost:8502/apply

echo "Shutting down"
curl -f  -X POST localhost:8502/admin/quit

wait $SERVER_PID