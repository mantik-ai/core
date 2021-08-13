# Common Code for building Golang applications
# Set NAME first

MAKEFLAGS += --no-builtin-rules
# Can be disabled, if the CGO-Disabled linux build doesn't make sense (e.g. when linking to C-Code)
ENABLE_LINUX ?= true

# Empty be default, can be overriden by the user
# Extra targets which act as a dependency for the main target
EXTRA_DEPS ?=
MAIN_FILE ?= main.go
APP_VERSION := $(shell git describe --always --dirty)
GOPATH ?= $(HOME)/go

ifeq ($(ENABLE_LINUX),true)
	LINUX_DEPENDENCY = target/${NAME}_linux
else
	LINUX_DEPENDENCY = $()
endif

# Caching
ifdef CACHE_DIR
  export GOPATH=$(CACHE_DIR)/go
endif

# Can be set, and then protobuf files will be built
ifdef PROTOBUF_DIR
  EXTRA_DEPS:=$(EXTRA_DEPS) protos
endif


build: target/${NAME} $(LINUX_DEPENDENCY)

.PHONY: clean

clean::
	rm -f target/${NAME}
	rm -f target/${NAME}_linux

test: target/${NAME}
	go test -v ./...

# Empty
api-doc::

target/${NAME}: $(shell find -not -path "./target/*" -name "*.go") $(EXTRA_DEPS)
	gofmt -w .
	go build -mod=mod -o $@ -ldflags="-X main.AppVersion=${APP_VERSION}" $(MAIN_FILE)

target/${NAME}_linux: target/${NAME} $(EXTRA_DEPS)
	CGO_ENABLED=0 GOOS=linux go build -mod=mod -a -o $@ -ldflags="-X main.AppVersion=${APP_VERSION}" $(MAIN_FILE)

# If a protobuf_dir is set, it will be build extra
ifdef PROTOBUF_DIR

clean::
	rm -rf protos

protos: protos/proto.make

# There is no publish for go projects yet
.PHONY: publish

protos/proto.make: $(shell find $(PROTOBUF_DIR) -name "*.proto")
	# Pinning go protoc gen
	go get github.com/golang/protobuf/protoc-gen-go@v1.5.2
	@mkdir -p protos
	PATH=$(GOPATH)/bin:$(PATH); protoc -I $(PROTOBUF_DIR) $^ --go_out=plugins=grpc:protos
	@touch $@
endif
