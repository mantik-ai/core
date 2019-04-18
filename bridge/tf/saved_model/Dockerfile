FROM debian:9.6-slim
# Note: also tried Alpine, however the Go executable with linked Tensor flow seems not to start.
# The size difference is 200MB (Alpine) vs 300 MB (Debian) vs 247MB (Debian Slim)

# Bundles tfbridge into a Docker container
# The model is expected to be put into the volume /data

RUN groupadd -r mantik-bridge && useradd -r -g mantik-bridge mantik-bridge

ADD --chown=mantik-bridge:mantik-bridge tfbridge /opt/bridge/tfbridge
ADD --chown=mantik-bridge:mantik-bridge vendor/tensorflow_c /opt/bridge/tensorflow_c

# Data Directory
RUN mkdir /data && chown mantik-bridge:mantik-bridge /data

USER mantik-bridge:mantik-bridge

ENV LD_LIBRARY_PATH=/opt/bridge/tensorflow_c/lib
ENV CGO_LDFLAGS=/opt/bridge/tensorflow_c/lib

EXPOSE 8502
VOLUME ["/data"]
WORKDIR /opt/bridge
ENTRYPOINT ["./tfbridge", "serve", "/data"]