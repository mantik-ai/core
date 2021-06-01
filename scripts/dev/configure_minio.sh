set -e
# set -x

# See https://docs.min.io/docs/minio-multi-user-quickstart-guide.html

# The alias under which minio is registered
ALIAS_NAME=minio
# The bucket name (must match permission)
BUCKET_NAME=mantiktest

# User name for mantik containers (aka Access key)
USERNAME=mantikruntime
# Password for mantik containers (aka secret key)
PASSWORD=mantikruntimepassword

ADMIN_ACCESSKEY=myaccesskey
ADMIN_SECRETKEY=mysecretkey

# Preparation Configuring mc
mc alias set $ALIAS_NAME http://minio.minikube $ADMIN_ACCESSKEY $ADMIN_SECRETKEY --api s3v4

echo "Checkinng Connectivity... "
if mc admin info minio; then
  echo "Minio seems present"
else
  echo "Cannot reach minio.minikube"
  echo "Do you have minio.minikube entry in your /etc/hosts file?"
  MINIKUBE_IP=`minikube ip`
  echo "If not add the following entry to your /etc/hosts"
  echo "$MINIKUBE_IP minio.minikube"
  exit 1
fi


echo "Create Bucket $BUCKET_NAME"
mc mb --ignore-existing $ALIAS_NAME/$BUCKET_NAME

echo "Creating Policy for $BUCKET_NAME"
cat > permissions.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:*"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:s3:::mantiktest/*"
      ],
      "Sid": ""
    }
  ]
}
EOF

mc admin policy add $ALIAS_NAME getdeleteput permissions.json
rm permissions.json

echo "Make files within public/ publicly readable"
cat > public.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:GetObject"
      ],
      "Effect": "Allow",
      "Principal": {
        "AWS": [
          "*"
        ]
      },
      "Resource": [
        "arn:aws:s3:::mantiktest/public/*"
      ],
      "Sid": ""
    }
  ]
}
EOF

mc policy set-json public.json $ALIAS_NAME/$BUCKET_NAME
rm public.json

echo "Create User $USERNAME"
mc admin user add $ALIAS_NAME $USERNAME $PASSWORD

echo "Giving Permission to $USERNAME"
mc admin policy set $ALIAS_NAME getdeleteput user=$USERNAME
