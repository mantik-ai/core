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

root=mantik/engine/_stubs
include=../engine/src/main/protobuf/
rm -rf $root
mkdir -p $root
pipenv run python -m grpc_tools.protoc \
    -I $include \
    --python_out $root \
    --grpc_python_out $root \
    $include/mantik/engine/*.proto

# make all python imports from mantik.engine relative
sed -i -E 's/from mantik.engine/from ./g' $root/mantik/engine/*.py