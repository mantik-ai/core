# No Built in Rules
MAKEFLAGS += --no-builtin-rules

build: scala-build build-subprojects
include scripts/ci/Makefile.core

SUB_PROJECTS=\
	mnp/mnpgo\
	mnp/mnppython\
	go_shared\
	bridge/binary\
	bridge/bridge_debugger\
	bridge/select\
	bridge/sklearn/simple_learn \
	bridge/tf/saved_model \
	bridge/tf/train \
	executor/coordinator \
	executor/tinyproxy \
	cli \
	python_sdk \
	doc \


.PHONY: help
help:
	@echo "Mantik Makefile build system"
	@echo "---------------------------------------------"
	@echo "Available Goals:"
	@echo ""
	@echo "build             Build the code"
	@echo "test              Run all unittests"
	@echo "local-install     Install non-docker artifacts locally"
	@echo "publish           Publish non-docker artifacts to the repository"
	@echo "docker            Build Docker Images."
	@echo "                  Note: if your docker needs' sudo you can override the docker executable"
	@echo "                  export DOCKER=\"sudo docker\""
	@echo "docker-minikube   Build Docker Images on a running Minikube instance"
	@echo "docker-publish    Upload Docker Images to the docker repository"
	@echo "integration-test  Run integration tests on minikube"

test: scala-test test-subprojects

docker: docker-subprojects

docker-publish: docker-publish-subprojects

.PHONY: docker-minikube
docker-minikube:
	$(eval EXTRA_ARGS:=$(shell minikube docker-env | grep export | cut -d' ' -f2 | xargs))
	# Call own Makefile again
	$(MAKE) docker $(EXTRA_ARGS)

.PHONY: local-install
# Only scala needs an install method
local-install: scala-install

.PHONY: publish
# Only scala needs a publish method
publish: scala-publish

.PHONY: clean
clean:
	rm -rf `find -not -path "./cache/*" -name "target" | xargs`

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
