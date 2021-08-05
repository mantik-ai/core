FROM openjdk:11-jre-slim

RUN useradd -ms /bin/bash mantikengine && \
    mkdir /app && \
    mkdir -p /data/mantik/logs && \
    mkdir -p /data/mantik/repo && \
    chown mantikengine:mantikengine /app && \
    chown -R mantikengine:mantikengine /data/mantik

ADD --chown=mantikengine:mantikengine target/universal/stage /app

USER mantikengine

# Note: Mantik runs unter user mantikengine (UID=GID=1000)

# Contains repository of Mantik Artifacts
# (Subdirectories: artifacts, files)
VOLUME /data/mantik/repo
# Contains logs
VOLUME /data/mantik/logs

ARG MANTIK_VERSION_TAG
ENV MANTIK_VERSION_TAG=$MANTIK_VERSION_TAG

ENTRYPOINT ["/app/bin/engine-app", "-Dconfig.resource=application_dockerimage.conf", "-Dlogback.configurationFile=logback_dockerimage.xml"]


