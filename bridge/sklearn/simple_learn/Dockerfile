FROM python:3.7-slim
RUN groupadd -r mantik-bridge && useradd -r -g mantik-bridge mantik-bridge

ADD --chown=mantik-bridge:mantik-bridge target /opt/bridge/target

WORKDIR /opt/bridge
RUN pip install --no-cache-dir -r target/requirements.txt


ADD --chown=mantik-bridge:mantik-bridge *.py /opt/bridge/

# Data Directory
RUN mkdir /data && chown mantik-bridge:mantik-bridge /data

USER mantik-bridge:mantik-bridge

EXPOSE 8502
VOLUME ["/data"]
ENTRYPOINT ["python", "main.py", "/data"]
