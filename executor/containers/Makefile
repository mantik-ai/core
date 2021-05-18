MAKEFLAGS += --no-builtin-rules

build: target/format.make build_applications

include ../../scripts/ci/Makefile.docker_shared

# All Executables which are to be generated
EXECUTABLES = mnp_preparer \
	mnp_pipeline_controller

# Map-like structure which contains the name of the main go file
MAIN_FILE_mnp_preparer = cmd/mnp_preparer/main.go
MAIN_FILE_mnp_pipeline_controller = cmd/mnp_pipeline_controller/main.go

# Map-like structure which contains the name of the docker image
DOCKER_IMAGE_NAME_mnp_preparer = executor.mnp_preparer
DOCKER_IMAGE_NAME_mnp_pipeline_controller = executor.mnp_pipeline_controller

# Host platform targets
EXECUTABLE_TARGETS = $(patsubst %, target/%, $(EXECUTABLES))
# Linux targets
EXECUTABLE_LINUX_TARGETS = $(patsubst %, target/%_linux, $(EXECUTABLES))

GO_FILES = $(shell find . -name "*.go")

# Caching
ifdef CACHE_DIR
  export GOPATH=$(CACHE_DIR)/go
endif


.PHONY: clean
clean:
	rm -rf target

.PHONY: test
test: build
	go test -v ./...

target/format.make: $(GO_FILES)
	@mkdir -p target
	gofmt -w .
	touch $@

build_applications: $(EXECUTABLE_TARGETS) $(EXECUTABLE_LINUX_TARGETS)

target/%_linux: $(GO_FILES)
	@mkdir -p target
	CGO_ENABLED=0 GOOS=linux go build -mod=mod -a  -o $@ $(MAIN_FILE_$*)

target/%: $(GO_FILES)
	@mkdir -p target
	go build -mod=mod -o $@ $(MAIN_FILE_$*)

# Docker stuff

# Each executable deserves it's own docker image target
DOCKER_TARGETS=$(patsubst %, target/%_docker_image.make, $(EXECUTABLES))

.PHONY: docker
docker: | build docker-unchecked

.PHONY: docker-unchecked
docker-unchecked: $(DOCKER_TARGETS)


# Payload preparer has it's own dockerfile
target/payload_preparer_docker_image.make:
	$(eval DOCKER_IMAGE_NAME := $(DOCKER_IMAGE_NAME_payload_preparer))
	$(eval IMAGE_FULL_NAME := $(DOCKER_REPO)/$(DOCKER_IMAGE_NAME))
	$(eval EXECUTABLE := target/payload_preparer_linux)
	$(DOCKER) build -f Dockerfile_preparer -t $(IMAGE_FULL_NAME) --build-arg input_executable=$(EXECUTABLE) .

target/%_docker_image.make:
	$(eval DOCKER_IMAGE_NAME := $(DOCKER_IMAGE_NAME_$*))
	$(eval IMAGE_FULL_NAME := $(DOCKER_REPO)/$(DOCKER_IMAGE_NAME))
	$(eval EXECUTABLE := target/$*_linux)
	$(DOCKER) build -t $(IMAGE_FULL_NAME) --build-arg input_executable=$(EXECUTABLE) .

# Each Executable deserves it's own docker publish target
DOCKER_PUBLISH_TARGETS=$(patsubst %, target/%_docker_publish.make, $(EXECUTABLES))

docker-publish: $(DOCKER_PUBLISH_TARGETS)

target/%_docker_publish.make: docker-login
	$(eval DOCKER_IMAGE_NAME := $(DOCKER_IMAGE_NAME_$*))
	$(eval IMAGE_FULL_NAME := $(DOCKER_REPO)/$(DOCKER_IMAGE_NAME))
	$(eval REMOTE_IMAGE_NAME := $(REMOTE_REPO_NAME)/$(DOCKER_IMAGE_NAME):$(CI_COMMIT_REF_SLUG))
	$(DOCKER) tag $(IMAGE_FULL_NAME) $(REMOTE_IMAGE_NAME)
	$(DOCKER) push $(REMOTE_IMAGE_NAME)
