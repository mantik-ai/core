#!/usr/bin/env bash


#
# A Simple local integration test, which test the possibility of cancelling a process
#

MYDIR=`dirname $0`
cd $MYDIR/..

# Case1 Cancelling Coordinator
export COORDINATOR_PLAN='{"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}},"flows":[[{"node": "A", "resource": "out"}, {"node": "B", "resource": "in"}]]}'
./target/coordinator coordinator -address localhost:50503 -port 50503 &
COORDINATOR=$!

echo "Coordinator running..."
echo "Send cancellation request"
./target/coordinator cancel -address localhost:50503
wait $COORDINATOR
STATUS=$?
if [ $STATUS -eq 4 ]; then
    echo "Coordinator exited as expected"
else
    echo "Coordinator returnd $STATUS and not 4"
    exit 1
fi

# Case 2 Cancelling Sidecar
./target/coordinator sidecar -url http://localhost/unreachable -port 50504 &
SIDECAR=$!

echo "Sidecar running..."
echo "Send cancellation request"
./target/coordinator cancel -address localhost:50504
wait $SIDECAR
STATUS=$?
if [ $STATUS -eq 4 ]; then
    echo "Sidecar exited as expected"
else
    echo "Sidecar returnd $STATUS and not 4"
    exit 1
fi