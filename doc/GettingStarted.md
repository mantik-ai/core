# Getting Started

Hi new developer, welcome to Mantik Core! 

This document should help you in building and running a Mantik Engine and Application.

If you have any questions, feel free to ask and help us make Mantik better :)

## Prerequisites / Building

Mantik is not a trivial application. It consists of Scala, Go and Python source code. Its build artifacts contain
executable applications, Java Bytecode and Docker Images.

For a full documentation, please have a look into the [Building](Building.md) document.

Assuming you want to try it out locally, with Minikube, you should install the following:

- Minikube: As a local kubernetes environment
- A local Minio-Instance for running integration tests, reachable in http://minio.minikube (see `scripts/dev/start_minikube_minio.sh`)
- Java Development Kit 1.11: Needed for Scala
- Make: Needed for Building
- SBT (Simple Build Tool): Needed for Building Scala
- Python 3.7: Needed for Python Bridges, Python SDK and Examples
- pipenv: Needed for Python Package Management
- Go 1.13: Needed for Go Containers and Bridges
- Protobuf Compiler: Needed for gRpc
- Docker: Needed for Docker Container generation

The docker images will be build directly into the Docker registry which is built into Minikube.

The different build systems (SBT, go, pipenv) are all controlled using a multi-directory Makefile.

Build using:

- `make build`
  This creates the local artifacts
- `make docker-minikube`
  This creates the necessary docker images inside Minikube.

For more info about make targets, call `make help`

## IDE

We recommend a current version of IntelliJ to edit the Scala Source code. For the other parts, feel free to use
any IDE you want. The development took place with Jetbrains GoLand and PyCharme, but also IntelliJ Ultimate or Visual Studio Code should do it.

## Running Mantik Engine

As soon as Mantik is build, you can start it using

    ./scripts/dev/start_engine_minikube.sh

The Mantik Engine should now run and listens on Port 8087 for client applications. Output should look like this:

```
17:25:03.556 [run-main-0] INFO  a.m.p.r.impl.LocalFileRepository - Initializing Local File Repository in directory /home/nos/.local/share/mantik/files
17:25:03.563 [run-main-0] INFO  a.m.p.r.impl.LocalRepository - Initializing in /home/nos/.local/share/mantik/artifacts/items.db
17:25:03.574 [run-main-0] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Starting...
17:25:03.644 [run-main-0] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
17:25:04.462 [run-main-0] INFO  a.m.e.kubernetes.KubernetesExecutor - Initializing with kubernetes at address 192.168.99.105
17:25:04.462 [run-main-0] INFO  a.m.e.kubernetes.KubernetesExecutor - Docker Default Tag:  None
17:25:04.462 [run-main-0] INFO  a.m.e.kubernetes.KubernetesExecutor - Docker Default Repo: None
17:25:04.462 [run-main-0] INFO  a.m.e.kubernetes.KubernetesExecutor - Disable Pull:        true
17:25:05.092 [run-main-0] INFO  a.m.p.r.FileRepositoryServer - Choosing HostPort(192.168.99.1,8086) from Vector(/192.168.99.1, /192.168.1.92, /172.22.0.1, /172.17.0.1, /172.20.0.1, /172.21.0.1, /172.18.0.1, /172.19.0.1, /fe80:0:0:0:f4a4:2ff:febd:675b%vethdafb34b, /fe80:0:0:0:42:33ff:fef9:61d5%br-0a8108457220, /fe80:0:0:0:800:27ff:fe00:0%vboxnet0, /fe80:0:0:0:e6fe:8700:b906:b7a3%enp34s0)
17:25:05.092 [run-main-0] INFO  a.m.p.r.FileRepositoryServer - Listening on 0.0.0.0:8086, external HostPort(192.168.99.1,8086)
17:25:05.314 [run-main-0] INFO  ai.mantik.engine.server.EngineServer - Starting server at localhost:8087
```

If you want to inspect the current state of Kubernetes, call `minikube dashboard`.

### Configuration

When you look into the Start-Script above, you can see how the config of the Mantik Engine application is 
overriden with a new config file. Each Scala application derives the configuration of it's dependencies, and they all
define a default configuration called `reference.conf`.   The configuration of the application is defined in `application.conf`
This is done using the Lightbend Config module.
which overrides values from `reference.conf`. Using `-Dconfig.resource=abc.conf`, a specific main configuration file is loaded.

Feel free to have a look in the different configuratin files, to get a feeling about the configuration.

### Running in Docker Mode (inside Minikube)

An interesting thing of Minikube is, that it also provides an encapsulated docker environment. If you start Mantik using

    scripts/dev/start_engine_docker_minikube.sh

You can directly use Docker inside Minikube, instead of Kubernetes. This should work with the same docker images as kubernetes.

The config override is done exactly the same way as being discussed above.

### Running using local docker

For starters, we suggest Minikube as it is easier to use, and if something fails, you can just call `minikube delete; minikube start` to 
remove old images. Also building local docker images generates files which are owned by root, which are harder to remove.

If you want to try it anyway, you'll first have to build docker images on local docker, instead of Minikube:

    make docker
    
If this fails for permissions, try:

    DOCKER="sudo docker" make docker    

Then there is a complicated thing: Mantik can also not access local docker, as it (and should) not run as root. And you
should not make add your local user to the `docker` group for security reasons.

To circumvent this, we can forward TCP to Docker's local socket using socat:

    sudo socat -d TCP4-LISTEN:2375,fork,reuseaddr,bind=127.0.0.1 UNIX-CONNECT:/var/run/docker.sock
    
and in a separate terminal, start Mantik Engine now using:

    scripts/dev/start_engine_docker.sh
    
### Mantik Database

Mantik's local database (stored MantikArtifacts and files) is located in `~/.local/share/mantik`. If something goes wrong,
just delete the directory, and Mantik will start with a fresh new Database.

### Running Scala Examples

The Scala Examples reside in `examples/src/main/scala/com/example/examples` and should be directly executable from IntelliJ
once you have the engine running.

### Running Python Examples

- Go into the directory `python-sdk`
- Spawn a Pipenv shell using `pipenv shell`
- Run `python examples/mantik_train_algorithm.py`

### Running CLI (Command Line Interface)

The CLI resides (after building) in `cli/target/mantik`. If you have the engine running, you can investigate it's state
using: 

       $ ./mantik items
                             ITEM                     |   KIND    |           DEPLOYMENT            
       +----------------------------------------------+-----------+--------------------------------+
         mnist_train                                  | dataset   |                                 
         mnist_linear                                 | trainable |                                 
         mantik/tf.train                              | bridge    |                                 
         mantik/tf.saved_model                        | bridge    |                                 
         mantik/binary                                | bridge    |                                 
         mnist_test                                   | dataset   |                                 
         sample1                                      | dataset   |                                 
         @Y7NrCkX1Xz6HmGH1C2ovQgGeocB4oQP3BswSpoePW_w | bridge    |                                 
         mnist_annotated                              | algorithm | internal                        
                                                      |           | mnp://mantikfF:8502/deployed    
         nob/mnist_annotated                          | algorithm | internal                        
                                                      |           | mnp://mantikfF:8502/deployed    
         nob/mnist1                                   | pipeline  | internal http://mantik5:8502    
         multiply                                     | algorithm |                                 
         mantik/sklearn.simple                        | bridge    |                                 
         kmeans                                       | trainable |                                 
         mq/kmeans_trained_on_blobs                   | pipeline  |                                 


## Anatomy of a Scala Mantik Application

Scala Mantik applications are using an interface called `PlanningContext` to trigger their actions.
This context can be used to load `MantikItem`s from local registry's artifacts.

These Items can be combined (e.g. an `Algorithm` can be applied to a `DataSet`). All combiners generate new
`MantikItem`s.

Some `MantikItem`s have Actions (e.g. `fetch()` on DataSet). This actions can be sent to the `PlanningContext`
and will be executed by Mantik.

In practice the Action itself triggers any calculation. Before that, only DataTypes are calculated. There is a translation
step taking place, which is also meant to be the place to do any optimizations.

Once an action is executed, the result is transferred beging to the Client.

Actions can be 

- Fetching a (calculated) DataSet
- Deploying an Algorithm to Mantik
- Pushing / Pulling an Item to a remote Registry
- Saving a Mantik Item to the local registry

If you want to create your own Mantik Client application, you'll need the dependency to `engine`. Just have a look in `build.sbt`. 

## Anatomy of a Python Mantik Application

Python cannot directly access the `PlanningContext`, as the API would be too complex. Instead, it has a simplified
API which exposes all Combinators / Actions via gRpc-Calls. If you want to see the possible calls, look at the gRpc
interfaces defined in `engine/src/main/protobuf/mantik/engine`.

The Python SDK wrap this calls in simpler objects (gRpc directly looks a bit cumbersome).

# Generate own Mantik Artifacts

Mantik Artifacts can be DataSets, Bridges, Algorithms etc. They can be used as Items inside the Planning situation, see [Glossary](Glossary.md)

They can be directly generated from a directory containing a [MantikHeader](MantikHeader.md) and an optional `payload` directory.

In Scala they can be added using `PlanningContext.pushLocalMantikItem` or via CLI tool `add`. They can also be 
downloaded again using the CLI tool with `extract`.

The content of the MantikHeader is dependent from the type of Item.

Examples:

- A Bridge: `bridge/sklearn/simple_learn` (only MantikHeader is used)
- A DataSet: `bridge/binary/test/mnist`
- An Algorithm: `bridge/tf/saved_model/test/resources/samples/double_multiply`
- A trainable algorithm: `bridge/sklearn/simple_learn/example/kmeans`


It's also possible to create primitive DataSets (Constants) in Scala `DataSet.literal(..)`.

## A Note on Production

In Production, Mantik should run as a simple service locally on your machine and connect to a configured Kubernetes/Docker
instance. Docker Images should be pulled from Docker Hub, Mantik Artifacts from a remote Mantik Registry.

This is not yet ready, sorry about that. That's why you have to build docker images by hand for Kubernetes.

Also Client applications should use Mantik's client libraries directly from public repositories (e.g. Pypi, Maven) and should
not be integrated in the Build system. This is also not yet ready.