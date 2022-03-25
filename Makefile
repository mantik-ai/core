# No Built in Rules
MAKEFLAGS += --no-builtin-rules
CACHE_DIR ?= ${PWD}/cache
export CACHE_DIR

build: build-subprojects

# Note: the order of Subprojects is important as some need the output of others
SUB_PROJECTS=\
	ui/client\
	engine-app\
	mnp/mnpgo\
	mnp/mnppython\
	go_shared\
	python_sdk \
	bridge/binary\
	bridge/csv\
	bridge/select\
	bridge/sklearn/simple_learn \
	bridge/tf/saved_model \
	bridge/tf/train \
	bridge/scalafn/bridge \
	executor/containers \
	executor/tinyproxy \
	cli \
	doc \


.PHONY: help
help:
	@echo "Mantik Makefile build system"
	@echo "---------------------------------------------"
	@echo "Available Goals:"
	@echo ""
	@echo "build             Build the code"
	@echo "test              Run all unittests"
	@echo "generated         Update static generated files"
	@echo "clean             Remove results"
	@echo "api-doc           Build API Documentation"
	@echo "local-install     Install non-docker artifacts locally"
	@echo "publish           Publish non-docker artifacts to the repository"
	@echo "docker            Build Docker Images."
	@echo "                  Note: if your docker needs' sudo you can override the docker executable"
	@echo "                  export DOCKER=\"sudo docker\""
	@echo "docker-unchecked  Build Docker Images, assuming that the build finished (workaround for CI)"
	@echo "docker-minikube   Build Docker Images on a running Minikube instance"
	@echo "docker-publish    Upload Docker Images to the docker repository"
	@echo "integration-test  Run integration tests on minikube"

test: test-subprojects

generated: generated-subprojects

docker: docker-subprojects

docker-unchecked: docker-unchecked-subprojects

docker-publish: docker-publish-subprojects

.PHONY: docker-minikube
docker-minikube:
	$(eval EXTRA_ARGS:=$(shell minikube docker-env | grep export | cut -d' ' -f2 | xargs))
	# Call own Makefile again
	$(MAKE) docker $(EXTRA_ARGS)

.PHONY: local-install
# Only scala needs an install method
local-install:
	$(MAKE) -C engine-app install

.PHONY: publish
publish: publish-subprojects

.PHONY: clean
clean: clean-subprojects

.PHONY: api-doc
api-doc: api-doc-subprojects

# Pattern rule which executes % on every subproject
%-subprojects:
	@for dir in $(SUB_PROJECTS); do \
		$(MAKE) -C $$dir $* || exit 1 ; \
	done

# ******************** INTEGRATION TEST STUFF ***********************

.NOTPARALLEL: integration-test
.PHONY: integration-test
integration-test: executor-kubernetes-integration-test \
	executor-docker-integration-test \
	planner-integration-test \
	engine-integration-test \
	python-integration-test

executor-kubernetes-integration-test: docker-minikube
	sbt executorKubernetes/it:test

executor-docker-integration-test: docker-minikube
	sbt executorDocker/it:test

planner-integration-test: docker-minikube
	sbt planner/it:test

engine-integration-test: docker-minikube
	sbt engine/it:test

python-integration-test: docker-minikube
	./scripts/dev/run_python_integration_test.sh
