FROM openjdk:11-jre-slim

RUN useradd -ms /bin/bash mantik && \
    mkdir /app && \
    chown mantik:mantik /app

ADD --chown=mantik:mantik target/universal/stage /app

USER mantik

ENTRYPOINT ["/app/bin/scala-fn-bridge"]


