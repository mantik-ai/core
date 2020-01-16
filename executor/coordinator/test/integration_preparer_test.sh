#!/usr/bin/env bash

# Test that payload_preparer works

set -e

MYDIR=`dirname $0`
cd $MYDIR/..

MANTIKHEADER="IyBUaGlzIGlzIGEgZHVtbXkgZmlsZSBmb3IgdGVzdGluZyB0aGUgcHJlcGFyZXItQ29udGFpbmVyCm5hbWU6IEhlbGxvIFdvcmxkCnZlcnNpb246IDAuMQo="

# Base64 made at command line with base64 utility.
./target/payload_preparer -url file://test/hello1.zip -dir test/unpack_test -mantikHeader $MANTIKHEADER

if [ ! -f test/unpack_test/payload/hello1/HelloWorld.txt ]; then
    echo "Unpacked bundle does'nt exist"
    exit 1
fi

if [ ! -f test/unpack_test/MantikHeader ]; then
    echo "MantikHeader doesn't exist"
    exit 1
fi

if ! diff test/unpack_test/MantikHeader test/hello1_mantikheader.yaml; then
    echo "Found differences in unpacked MantikHeader"
    exit 1
fi

echo "Deleting temporary directory"
rm -r test/unpack_test

# 2nd test with just MantikHeader and no payload
./target/payload_preparer -dir test/unpack_test -mantikHeader $MANTIKHEADER
if [ ! -f test/unpack_test/MantikHeader ]; then
    echo "MantikHeader doesn't exist"
    exit 1
fi
echo "Deleting temporary directory"
rm -r test/unpack_test
