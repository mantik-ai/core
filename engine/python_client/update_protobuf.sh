#!/usr/bin/env bash
MYDIR=`dirname $0`
cd $MYDIR

# Updates gRPC/Protobuf generators
# See https://grpc.io/docs/tutorials/basic/python/

# Note: the protobuf files lead to the python directory names.
# Due various bugs, it can't be simply changed via python_out/grpc_python_out
# See: https://github.com/grpc/grpc/issues/9575
#      https://github.com/grpc/grpc/issues/9450
#      https://github.com/architect-team/python-launcher/issues/1

pipenv run python -m grpc_tools.protoc \
    -I ../src/main/protobuf \
    --python_out . \
    --grpc_python_out . \
    ../src/main/protobuf/mantik/protobuf/engine/*.proto
