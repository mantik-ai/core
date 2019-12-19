NAME=mantik
EXTRA_DEPS=protos
include ../scripts/ci/Makefile.go

GO_PATH ?= $(HOME)/go

clean::
	rm -rf protos

protos: protos/proto.make

protos/proto.make: $(shell find ../engine/src/main/protobuf -name "*.proto")
	@mkdir -p protos
	PATH=$(PATH):$(GO_PATH)/bin; protoc -I ../engine/src/main/protobuf ../engine/src/main/protobuf/mantik/engine/*.proto --go_out=plugins=grpc,import_path=protos/engine:protos
	@touch $@

.PHONY: docker
docker:
	@# Nothing to do
