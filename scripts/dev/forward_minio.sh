#############
# This script makes minio pod accessible on the system. The steps are those recommended and shown after first minio startup.
# - Add ingresses to /etc/hosts
# - Set minio alias
# - Port-forward minio
############

#!/bin/bash

set -e

INGRESSES=$(kubectl --context=minikube --all-namespaces=true get ingress -o jsonpath='{.items[*].spec.rules[*].host}')
MINIKUBE_IP=$(minikube ip)
HOSTS_ENTRY="$MINIKUBE_IP $INGRESSES"
if grep -Fq "$MINIKUBE_IP" /etc/hosts > /dev/null; then sudo sed -i "s/^$MINIKUBE_IP.*/$HOSTS_ENTRY/" /etc/hosts;     echo "Updated hosts entry"; else     echo "$HOSTS_ENTRY" | sudo tee -a /etc/hosts;     echo "Added hosts entry"; fi
 
SECRET_KEY=$(kubectl get secret minio -o jsonpath="{.data.secretkey}" --namespace minio | base64 --decode)
ACCESS_KEY=$(kubectl get secret minio -o jsonpath="{.data.accesskey}" --namespace minio | base64 --decode)

mc alias set minio http://localhost:9000 "$ACCESS_KEY" "$SECRET_KEY" --api s3v4
 
export POD_NAME=$(kubectl get pods --namespace minio -l "release=minio" -o jsonpath="{.items[0].metadata.name}")
kubectl port-forward $POD_NAME 9000 --namespace minio
