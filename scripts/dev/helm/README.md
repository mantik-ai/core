# Mantik helm chart

The chart in `mantik-engine` can be used to deploy the containerized mantik engine to a kubernetes cluster via `helm install mantik-engine . --namespace mantik-engine --create-namespace`.

## Gotchas

 - No image pull secrets are supplied. For local development you can pull or build the images into your minikube docker daemon.
 - `.Values.mantik.args.executor.kubernetes.nodeAddress` is tricky. For local development in minikube, set it to the value of `minikube ip`.

## Local development

For testing in minikube, use the scripts from `core` repo (`scripts/dev`). You need to

 - Start minikube and install minio helm chart (`start_minikube.sh`)
 - Build or pull all mantik images into minikube docker daemon
 - Forward minio to `localhost:9000` and add `minio.minikube` to `/etc/hosts` (it needs to resolve to the minio instance)
 - `./configure_minio.sh`
 - Install this helm chart (see above).
 - Use `minikube tunnel` to expose the `service/mantik-engine` IP.
 - Connect the mantik client with `service/mantik-engine` external IP and port `8087`.

