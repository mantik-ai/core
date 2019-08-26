Docker Executor
===============

The docker executor is an alternative implementation of the Executor, running jobs and services directly on Docker.

Using Minikube Docker
---------------------
Using minikube docker has the advantage, that permission stuff is needed and the host docker system is not tainted.

To start the engine using Minikube Docker use the following configuration

```
-Dconfig.resource=application_local_docker_minikube.conf
```

Using local Docker
------------------

The executor can not directly connect the unix domain socket of docker yet, this has two reasons

- It would take some workarounds to get Akka to connect to it
- It would either give the Engine Root permissions or the regular user permissions to start docker. This is dangerous.

Instead it tries to connect to docker on localhost:2375.

Using `socat` it is trivial to enable a temporary proxy from `localhost:2375` to docker:

```
sudo socat -d TCP4-LISTEN:2375,fork,reuseaddr,bind=127.0.0.1 UNIX-CONNECT:/var/run/docker.sock
```

Be careful, anybody who can call docker has basically root permissions.

