SCALA_RESOURCES = $(shell find */src/main -name "*.scala")
SCALA_TEST_RESOURCES = $(shell find */src/test -name "*.scala")

# .make files are there to tell Make if something is going to be rebuild

.PHONY: scala-build

scala-build: engine-app/target/build.make

engine-app/target/build.make: ${SCALA_RESOURCES}
	sbt -warn package
	touch $@

.PHONY: scala-test
scala-test:
	sbt -warn test
	touch $@

.PHONY: scala-install
scala-install:
	sbt -warn publishLocal

.PHONY: scala-publish
scala-publish:
	sbt -warn publish
