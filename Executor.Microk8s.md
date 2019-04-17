# Debugging Executor with Microk8s

## Installing Microk8s in Ubuntu

- Current version is [broken](https://github.com/ubuntu/microk8s/issues/402), but this works: `sudo snap install microk8s --edge --classic`
- You need the following extensions:
  
    ```
    microk8s.enable registry dns dashboard
    ```
  
- The dashboard URL can be found by calling `microk8s.kubectl get all --all-namespaces`
- Warning: Some reports suggest that Microk8s is listening on all network devices (this can open your computer!) however, at least
  on my machine it's listening on 127.0.0.1 only. Please test and be careful on public networks. 

## Environment Variables

This variables should work:

```
# For going through all testcases, 2GB will probably also enough
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx8G"
export SONATYPE_MANTIK_USERNAME=<your sonatype user>
export SONATYPE_MANTIK_PASSWORD=<your sonatype password>
export SKUBER_URL=http://localhost:8080
```

## Compiling Executor Helper Containers

- Use `executor/coordinator/build.sh` for building
- Use `executor/coordinator/create_docker_images.sh` for creating docker images
- Use `executor/scripts/dev/push_test_containers_to_microk8s.sh` to push the images to the Microk8s Registry.

## Starting Executor

- Start the class `Main` with `-Dconfig.resource=application_local_microk8s.conf` inside IntelliJ.

## Run the examples

- The examples should directly work.

