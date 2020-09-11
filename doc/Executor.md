# Executor

The executor is responsible for running MNP based containers on some backend and abstracting away
differences.

* Running MNP Payload
* Installing permanent (deployed) MNP Services
* Querying MNP Payload
* Communicating with MNP Payload

There are two executors, however the design is open for more implementations

- Kubernetes Executor
- Docker Executor

There are multiple packages:

- `executor/api` The Interface of the Executor
- `executor/common` Common Source code for the Executor
- `executor/kubernetes` Kubernetes Implementation
- `executor/docker` Docker Implementation.

## Helper Containers

There are some helper containers which are used by the Executor:

- `executor/containers/cmd/mnp_pipeline_controller.go`: Responsible for running deployed Pipelines, implementing an HTTP
Protocol which is independent from a running Mantik Instance
- `executor/containers/cmd/mnp_preparer.go`: Responsible for booting a session in deployed Pipelines
- `executor/tinyproxy` a Dockerfile for [TinyProxy](https://tinyproxy.github.io/) which is used
  for communicating with MNP Nodes on Network Borders (Kubernetes)
- [Traefik](https://docs.traefik.io/) is used for implementing Ingres support on Docker.

## Docker Executor

The Docker Executor is communicating directly with a Docker Daemon to do it's job.

### Using Minikube Docker

Using minikube docker has the advantage, that permission stuff is needed and the host docker system is not tainted.

To start the engine using Minikube Docker use the following configuration

```
-Dconfig.resource=application_local_docker_minikube.conf
```

### Using local Docker

The executor can not directly connect the unix domain socket of docker yet, this has two reasons

- It would take some workarounds to get Akka to connect to it
- It would either give the Engine Root permissions or the regular user permissions to start docker. This is dangerous.

Instead it tries to connect to docker on localhost:2375.

Using `socat` it is trivial to enable a temporary proxy from `localhost:2375` to docker:

```
sudo socat -d TCP4-LISTEN:2375,fork,reuseaddr,bind=127.0.0.1 UNIX-CONNECT:/var/run/docker.sock
```

Be careful, anybody who can call docker has basically root permissions.

