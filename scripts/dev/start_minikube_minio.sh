#!/usr/bin/env bash

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..

# Starting min.io on Minikube

# Note: you have to either install https://github.com/kubernetes/minikube/tree/master/deploy/addons/ingress-dns
# Or update hosts entry to let minio.minikube to your Minikube IP
# /etc/hosts
#

# We cannot use a sub path for Minio either, see https://github.com/minio/minio/issues/10162

# See https://github.com/helm/charts/tree/master/stable/minio
helm repo add minio https://helm.min.io/ # Ignores if already existing
helm install --create-namespace \
  --namespace minio \
  --set persistence.size=10Gi \
  --set accessKey=myaccesskey,secretKey=mysecretkey \
  --set ingress.enabled=true \
  --set ingress.hosts[0]=minio.minikube \
  --set ingress.annotations."nginx\.ingress\.kubernetes\.io/proxy-body-size"=500m \
  minio \
  minio/minio