# Note: the ScalaFn Bridge is built and tested using the general Scala Build & Test Process
# However the Docker Image is built here
ENGINE_APP_DIR=../../../engine-app

.PHONY: build
build:
	$(MAKE) -C $(ENGINE_APP_DIR) build

DOCKER_IMAGE_NAME=bridge.scala-fn
include ../../../scripts/ci/Makefile.docker_single

# Nothing to do, tested and published inside engine-app
.PHONY: api-doc test generated install publish clean




