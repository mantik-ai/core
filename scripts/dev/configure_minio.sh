set -e
set -x

# Assuming Minio is already integrated into mc

# See https://docs.min.io/docs/minio-multi-user-quickstart-guide.html

# The alias under which minio is registered
ALIAS_NAME=minio
# The bucket name (must match permission)
BUCKET_NAME=mantiktest

# User name for mantik containers (aka Access key)
USERNAME=mantikruntime
# Password for mantik containers (aka secret key)
PASSWORD=mantikruntimepassword


# 0. Create bucket
mc mb --ignore-existing $ALIAS_NAME/$BUCKET_NAME

# 1. Create a new Policy
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

# 2. Make files within public/ public readable
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

# 3. Create user
mc admin user add $ALIAS_NAME $USERNAME $PASSWORD

# 4. Give permission
mc admin policy set $ALIAS_NAME getdeleteput user=$USERNAME
