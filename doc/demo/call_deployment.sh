set -e
MYDIR=`dirname $0`
cd $MYDIR
set -x
IP=`minikube ip`
DEPLOYMENT=http://$IP/mnist
curl -X POST $DEPLOYMENT/apply -F "image=@2.png"


