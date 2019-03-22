#!/usr/bin/env bash

# Test that payload_preparer works

set -e

MYDIR=`dirname $0`
cd $MYDIR/..

# Base64 made at command line with base64 utility.
./target/payload_preparer -url file://test/hello1.zip -dir test/unpack_test -pdir sub -mantikfile "IyBUaGlzIGlzIGEgZHVtbXkgZmlsZSBmb3IgdGVzdGluZyB0aGUgcHJlcGFyZXItQ29udGFpbmVyCm5hbWU6IEhlbGxvIFdvcmxkCnZlcnNpb246IDAuMQo="

if [ ! -f test/unpack_test/sub/hello1/HelloWorld.txt ]; then
    echo "Unpacked bundle does'nt exist"
    exit 1
fi

if [ ! -f test/unpack_test/Mantikfile ]; then
    echo "Mantikfile doesn't exist"
    exit 1
fi

if ! diff test/unpack_test/Mantikfile test/hello1_mantikfile.yaml; then
    echo "Found differences in unpacked Mantikfile"
    exit 1
fi

echo "All fine, deleting temporary directory"
rm -r test/unpack_test
