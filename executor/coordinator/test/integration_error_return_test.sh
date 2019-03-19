#!/usr/bin/env bash


#
# A Simple local integration test, which tests return codes
#

set -e
MYDIR=`dirname $0`
cd $MYDIR/..

# Case1 Unreachable side car
export COORDINATOR_PLAN='{"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}},"flows":[[{"node": "A", "resource": "out"}, {"node": "B", "resource": "in"}]]}'
if ./target/coordinator coordinator -address localhost:50503 -port 50503 -connectTimeout 1s; then
    echo "Coordinator should return != 0"
    exit 1
else
    echo "Coordinator failed as expected"
fi


# Case2 Coordinator who times out
if ./target/coordinator sidecar -url http://localhost/unreachable -waitForWebServiceReachable 1s; then
    echo "SideCar should return != 0"
    exit 1
else
    echo "SideCar failed as expected"
fi
