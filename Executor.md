Pipeline Executor
=================

The executor executes pipelines in Kubernetes.

**Note:** The spec is not yet stable and subject of changes.


Input:

A simple DAG Description which processes are to be started. This is transformed into Kubernetes Job and Pods.

The executor should be stateless.

The main control is offloaded into Go Applications called coordinator/sidecar.
Each container is put into a pod together with a sidecar, which executes the inter-node communication.

The coordinator tells the sidecars what to do and waits for the final result.


Execution is done on the pull principle, so we are pulling data on the sinks. The coordinator tells
the sidecar where to put data around.

Interface
---------
- The executor is implemented as as HTTP-Based Service
- There is a client and a server implementation (see `executor/api`)
- The communication is done via a JSON-Serialized Graph model.
- The protocol is not yet fixed, as Executor builds as a part of Mantik-Core and is subject of change.


Structure
---------
- `executor/api` contains the Model classes used by the Rest Server
- `executor/app` contains the executor server
- `executor/coordinator` contains the Golang Coordiantor Tool, Sidecar, payload provider and some test containers

Helper Go Containers
--------------------

- `coordinator`. The coordinator tells the side cars what there is to do and waits for their response.
- `sidecar`. The side car is a sidecar container (hence their name) for Mantik Containers translating HTTP Resources into
  streams and automatically executing this streams. They are designed in a way, that user data is not streamed via the coordinator
  if thats not necessary.
- `payload_preparer` A simple init-container, which downloads the initial payload of a container for a bridge container
  together with it's Mantikfile and places it in the `/data`-Directory.
     
Running
-------
- Skuber (Scala Kubernetes Client Library) will look for a local Kubernetes Configuration in `$HOME/.kube/config` which should be present in the case of 
  Minikube.
- Otherweise you need to configure environment variables for Skuber, see the [Manual](https://github.com/doriordan/skuber/blob/master/docs/Configuration.md)
  (E.g. `SKUBER_URL`)
- Application Values (in application.conf) can be configured as described in [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md)
- Example running on local `minikube`:
    ```
    # Start Proxy, disable host verification
    kubectl proxy --accept-hosts='.*' # Listens on localhost:8001 per default
    # Start the Executor process
    # Note host.docker.internal seems to only work on Mac
    docker run -it -p 8080:8080 --rm -e SKUBER_URL=http://host.docker.internal:8001 executor
    ```
- Note: In order to do something useful, images must be reachable for the executor.
  This means that images must be pushed to the registry available from Kubernetes.
  For Microk8s see [Executor.Microk8s.md](Executor.Microk8s.md).

Todo
----
- Error handling is crude and needs complete redesign
- There is no real isolation. It knows namespaces, but they are not yet protected.
- Tracking of Jobs is very rudimentary.
- A lot of Testcases are missing.
- Periodic cleanup from the Executor could be extended.

Executing Integration Tests
---------------------------

Integration Testcases are marked with `@KubernetesIntegrationTest`-Annotation and are not part of regular
SBT test run. Their execution is currently only possible by hand. 

Images must be available from Kubernetes.


Problems
--------

### Problem with Pod Management

Pods keep in a stupid hanging state if their image is not found.

ExecutorImpl is periodically looking at such containers and kills them.

### Problem with Container quitting

Sidecars  try to quit their webservice by calling `POST admin/quit`. If the webservice doesn't quit
there is nothing what sidecars can do (however it could be removed by periodic cleanup).

There is also an issue in kubernetes documentation: https://github.com/kubernetes/kubernetes/issues/25908
