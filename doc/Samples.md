# Scala Samples

The samples are located in `examples/src/main/scala/com.example.examples` and can be directly run with IntelliJ.

## Environment

- You need a Kubernetes environment running (e.g. Minikube)
- You need to provide your Mantik Docker Repository Credentials, so that Kubernetes can find the necessary Docker images

  The easiest way (apart from overriding config values) is by setting Environment variables
  for our default repository mdocker.rcxt.de

  These are

  `SONATYPE_MANTIK_USERNAME`: Your username for mdocker.rcxt.de
  `SONATYPE_MANTIK_PASSWORD`: Your password for mdocker.rcxt.de

  The environment variables need to be present during executor run.

- You need a running Mantik Engine, e.g. `scripts/dev/start_engine_minikube.sh`
