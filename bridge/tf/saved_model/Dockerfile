FROM golang:1.16.0-buster AS build

RUN groupadd -r mantik-bridge && useradd -r -g mantik-bridge mantik-bridge

# Work directory
RUN mkdir /opt/bridge && chown mantik-bridge:mantik-bridge /opt/bridge

USER mantik-bridge:mantik-bridge

WORKDIR /opt/bridge

# Downloading C++ Libraries

ENV CLIBDIR=/opt/bridge/vendor/tensorflow_c

RUN mkdir -p $CLIBDIR
RUN curl https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-cpu-linux-x86_64-1.15.0.tar.gz | tar xvz -C $CLIBDIR

# Bulding Tensorflow Bridge
ADD *.go .
ADD *.mod .
ADD *.sum .
ADD pb_vendor pb_vendor
ADD services services

# Shared Stuff (will be linked statically)
RUN mkdir /tmp/go_shared
ADD target/docker_build/go_shared /tmp/go_shared
ADD target/docker_build/mnp /tmp/mnp

# Patching Build file, so that it find shared go code
RUN sed -i 's!../../../go_shared!/tmp/go_shared!' go.mod
RUN sed -i 's!../../../mnp/mnpgo!/tmp/mnp/mnpgo!' go.mod

ENV LD_LIBRARY_PATH=/opt/bridge/vendor/tensorflow_c/lib
ENV CGO_LDFLAGS=-L/opt/bridge/vendor/tensorflow_c/lib


RUN mkdir /tmp/go_cache
ENV GOCACHE=/tmp/go_cache

RUN go build -mod=mod -o tfbridge

# Build final image
FROM debian:buster-slim

COPY --from=build /etc/passwd /etc/passwd
COPY --from=build /etc/group /etc/group

RUN mkdir /opt/bridge && chown mantik-bridge:mantik-bridge /opt/bridge
RUN mkdir /data && chown mantik-bridge:mantik-bridge /opt/bridge

USER mantik-bridge:mantik-bridge
RUN mkdir -p /opt/bridge/vendor/tensorflow_c
COPY --from=build /opt/bridge/tfbridge /opt/bridge/tfbridge
COPY --from=build /opt/bridge/vendor/tensorflow_c /opt/bridge/vendor/tensorflow_c

ENV LD_LIBRARY_PATH=/opt/bridge/vendor/tensorflow_c/lib

EXPOSE 8502
VOLUME ["/data"]
WORKDIR /opt/bridge
ENTRYPOINT ["./tfbridge", "mnp"]