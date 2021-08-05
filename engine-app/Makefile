SCALA_RESOURCES = $(shell git ls-files ../*.scala)

ifndef CACHE_DIR
  # $(info CACHE_DIR not set)
  SBT_CACHE_PARAMETER =
else
  $(info CACHE_DIR=$(CACHE_DIR))
  SBT_CACHE_PARAMETER = -Dsbt.ivy.home=$(CACHE_DIR)/ivy \
  -Divy.home=$(CACHE_DIR)/ivy \
  -Dsbt.global.base=$(CACHE_DIR)/sbt \
  -Dsbt.boot.directory=$(CACHE_DIR)/sbt_boot \
  -Dsbt.coursier.home=$(CACHE_DIR)/coursier
endif

SBT = cd ../; sbt $(SBT_CACHE_PARAMETER) -Dquill.macro.log=false -warn

# .make files are there to tell Make if something is going to be rebuild

.PHONY: build
build: target/build.make

DOCKER_IMAGE_NAME=engine
DOCKER_EXTRA_ARGS=--build-arg MANTIK_VERSION_TAG=$(CI_COMMIT_TAG)
include ../scripts/ci/Makefile.docker_single


target/build.make: ${SCALA_RESOURCES}
	$(SBT) package engineApp/stage scalaFnBridge/stage unidoc
	touch $@

.PHONY: test
test:
	$(SBT) test

.PHONY: install
install:
	$(SBT) publishLocal

.PHONY: publish
publish:
	$(SBT) publish

clean:
	$(SBT) clean
	# Also deleting compiled build.sbt
	rm -rf ../project/target
	rm -rf ../project/project/target
