MAKEFLAGS += --no-builtin-rules

build: target/format.make build_applications

include ../../scripts/ci/Makefile.docker_shared

# All Executables which are to be generated
EXECUTABLES = coordinator \
	payload_preparer \
	pipeline_controller \
	executor_sample_source \
	executor_sample_sink \
	executor_sample_learner \
	executor_sample_transformer

# Map-like structure which contains the name of the main go file
MAIN_FILE_coordinator = cmd/coordinator/main.go
MAIN_FILE_payload_preparer = cmd/payload_preparer/main.go
MAIN_FILE_pipeline_controller = cmd/pipeline_controller/main.go
MAIN_FILE_executor_sample_source = cmd/sample_source/main.go
MAIN_FILE_executor_sample_sink = cmd/sample_sink/main.go
MAIN_FILE_executor_sample_learner = cmd/sample_learner/main.go
MAIN_FILE_executor_sample_transformer = cmd/sample_transformer/main.go

# Map-like structure which contains the name of the docker image
DOCKER_IMAGE_NAME_coordinator = executor.coordinator
DOCKER_IMAGE_NAME_payload_preparer = executor.payload_preparer
DOCKER_IMAGE_NAME_pipeline_controller = executor.pipeline_controller
DOCKER_IMAGE_NAME_executor_sample_source = executor.sample_source
DOCKER_IMAGE_NAME_executor_sample_sink = executor.sample_sink
DOCKER_IMAGE_NAME_executor_sample_learner = executor.sample_learner
DOCKER_IMAGE_NAME_executor_sample_transformer = executor.sample_transformer

# Host platform targets
EXECUTABLE_TARGETS = $(patsubst %, target/%, $(EXECUTABLES))
# Linux targets
EXECUTABLE_LINUX_TARGETS = $(patsubst %, target/%_linux, $(EXECUTABLES))

GO_FILES = $(shell find . -name "*.go")

.PHONY: clean
clean:
	rm -rf target

.PHONY: test
test:
	go test -v ./...

.PHONY: integration_test
integration_test:
	./test/integration_test.sh
	./test/integration_error_return_test.sh
	./test/integration_cancellation_test.sh
	./test/integration_learn_test.sh
	./test/integration_transformer_test.sh
	./test/integration_preparer_test.sh

target/format.make: $(GO_FILES)
	@mkdir -p target
	gofmt -w .
	touch $@

build_applications: $(EXECUTABLE_TARGETS) $(EXECUTABLE_LINUX_TARGETS)

target/%_linux: $(GO_FILES)
	@mkdir -p target
	CGO_ENABLED=0 GOOS=linux go build -a  -o $@ $(MAIN_FILE_$*)

target/%: $(GO_FILES)
	@mkdir -p target
	go build -o $@ $(MAIN_FILE_$*)

# Docker stuff

# Each executable deserves it's own docker image target
DOCKER_TARGETS=$(patsubst %, target/%_docker_image.make, $(EXECUTABLES))

.PHONY: docker
docker: $(DOCKER_TARGETS)

target/%_docker_image.make: target/%_linux
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
