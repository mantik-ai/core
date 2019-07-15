#!/usr/bin/env bash
set -e
MYDIR=`dirname $0`
cd $MYDIR
gofmt -w .

mkdir -p target

echo "Building main application"
go build -o target/coordinator cmd/coordinator/main.go

echo "Building Preparer"
go build -o target/payload_preparer cmd/payload_preparer/main.go

echo "Building Pipeline Controller"
go build -o target/pipeline_controller cmd/pipeline_controller/main.go

echo "Building Test Applications"
go build -o target/executor_sample_source cmd/sample_source/main.go
go build -o target/executor_sample_sink cmd/sample_sink/main.go
go build -o target/executor_sample_learner cmd/sample_learner/main.go
go build -o target/executor_sample_transformer cmd/sample_transformer/main.go


# Also generate linux main (cross plattform)
echo "Building Linux Artefact"
CGO_ENABLED=0 GOOS=linux go build -a  -o target/coordinator_linux cmd/coordinator/main.go

echo "Building Linux Helper Artefacts"
CGO_ENABLED=0 GOOS=linux go build -a  -o target/payload_preparer_linux cmd/payload_preparer/main.go

echo "Building Pipeline Controller"
CGO_ENABLED=0 GOOS=linux go build -a  -o target/pipeline_controller cmd/pipeline_controller/main.go

echo "Building Linux Sample Artefacts"
CGO_ENABLED=0 GOOS=linux go build   -o target/executor_sample_source_linux cmd/sample_source/main.go
CGO_ENABLED=0 GOOS=linux go build   -o target/executor_sample_sink_linux cmd/sample_sink/main.go
CGO_ENABLED=0 GOOS=linux go build   -o target/executor_sample_learner_linux cmd/sample_learner/main.go
CGO_ENABLED=0 GOOS=linux go build   -o target/executor_sample_transformer_linux cmd/sample_transformer/main.go