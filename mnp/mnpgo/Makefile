NAME = mnpgo
ENABLE_LINUX = false
MAIN_FILE = handler.go
EXTRA_DEPS=protos

include ../../scripts/ci/Makefile.go

GO_PATH ?= $(HOME)/go


# Nothing
.PHONY: docker
docker:

.PHONY: docker-publish
docker-publish:

clean::
	rm -rf protos

protos: protos/proto.make

PROTOBUF_DIR=../protocol/protobuf

protos/proto.make: $(shell find $(PROTOBUF_DIR) -name "*.proto")
	@mkdir -p protos
	PATH=$(PATH):$(GO_PATH)/bin; protoc -I $(PROTOBUF_DIR) $^ --go_out=plugins=grpc,import_path=protos/mnp:protos
	@touch $@
