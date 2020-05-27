# Tinyproxy docker image for proxying gRpc calls to MNP Nodes
FROM alpine:3.9
RUN apk update && apk add --no-cache tinyproxy

ADD tinyproxy.conf /etc/tinyproxy/tinyproxy.conf

# Service is changing user account automatically
EXPOSE 8080
CMD ["tinyproxy", "-d"]
