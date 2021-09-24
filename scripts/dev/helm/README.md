# Mantik helm chart

The chart in `mantik-engine` can be used to deploy the containerized mantik engine to a kubernetes cluster via `helm install mantik-engine . --namespace mantik-engine --create-namespace`.

## Gotchas

 - `.Values.mantik.args.executor.kubernetes.nodeAddress` is tricky. For local development in minikube, set it to the value returned by `minikube ip`.

## Summary of the Chart

- `deployment`: Deployment of the Mantik Engine image. Make sure to configure `securityContext` properly:
  the engine runs as user`mantikengine` (UID=GID=1000). Set `runAsUser=1000`, `runAsGroup=1000` and `fsGroup=1000`
  (the last option referring to mounted volume ownership). This is already hard-coded in the chart.
- `persistent volume claims`: Two claims for mantik engine repo and mantik engine logs,
  used for mounting the paths `/data/mantik/repo` and `/data/mantik/logs`
- `clusterrole`, `clusterrolebinding`, `serviceaccount`: Used for proper permissions for engine service
- `service`: Exposes mantik grpc port and UI as LoadBalancer.

### Values

 - `replicaCount`: # Number of replicas for the deployment

 - `imagePullSecrets`: # (optional) Image Pull secrets passed to docker daemon on EKS
 - `nameOverride`: "" #TODO
 - `fullnameOverride`: "" #TODO



 - `image:` # Engine image
   - `pullPolicy: Never` # Pull Policy for the engine
   - `name: mdocker.rcxt.de/engine` # Engine image name 
   - `tag: master` # Engine image tag

 - `engine`: # General values that cannot be attributed to a template
   - `grpcPort: 8087` # Internal grpc port
   - `uiPort: 4040` # Internal UI port
   - `labels`:
     - `app: engine`

 - `service`: # Expose engine
   - `name: mantik-engine-service`
   - `type: LoadBalancer` # Load Balancer exposes services to public IPs
   - `grpcPort: 8080` # Port to expose for grpc (client) connections
   - `uiPort: 4040` # Port to expose for UI access

 - `deployment`:
   - `name: mantik-engine`
   - `mantik:` # mantik specific configuration
     - `args:` # Arguments passed as -Dmantik... or -Dakka to mantik app configuration
       - `executor:`
         - `docker:`
           - `defaultImageTag: "latest"` # Do not load mantik bridges from a specific tag
         - `behaviour:`
           - `disablePull: "true"` # Do not pull images (they are already there)
         - `kubernetes:`
           - `behavior:`
             - `defaultTimeout: "10 minutes"`
           - `nodeAddress: null`
         - `s3Storage:`
           - `endpoint: "http://minio.minikube"`
           - `region: "eu-central-1"`
           - `bucket: "mantiktest"`
           - `accessKeyId: "mantikruntime"`
           - `secretKey: "plain:mantikruntimepassword"`
           - `aclWorkaround: "true"`
   - `akka:`
     - `loglevel: DEBUG`
          
 - `volumes:` # Docker volumes for logs and mantik engine repo
   - `logs:`
     - `size: 1Gi`
   - `repo:`
     - `size: 1Gi`

## Local development

For testing in minikube, use the scripts from `core` repo (`scripts/dev`). You need to

 - Start minikube and install minio helm chart (`start_minikube.sh`)
 - Build or pull all mantik images into minikube docker daemon
 - Forward minio to `localhost:9000` and add `minio.minikube` to `/etc/hosts` (it needs to resolve to the minio instance)
 - `./configure_minio.sh`
 - Install this helm chart (see above).
 - Use `minikube tunnel` to expose the `service/mantik-engine` IP.
 - Connect the mantik client with `service/mantik-engine` external IP and port `8087`.

## Deployment on AWS

This Helm Chart is suitable for deployment of the mantik engine on AWS (and, in principle, any cloud provider). For 
that, you need to configure the following resources:

 - `S3Storage`: Provides storage for mantik `ExecutionPayloadProvider`
 - `IAM user`: Enable access to `S3 bucket` for mantik engine
 - `EKS` cluster: We recommend at least two nodes of type `m5.large`