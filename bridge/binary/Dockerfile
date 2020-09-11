FROM alpine:3.9 as builder
RUN apk update && apk add --no-cache ca-certificates

ARG input_executable=target/binary_bridge_linux

RUN adduser -D -g '' bridge
RUN mkdir /data; chown bridge /data

RUN mkdir /opt/bin
ADD ${input_executable} /opt/bin/run

# Step 2, converting it into a scratch image
FROM scratch

COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
COPY --from=builder /opt/bin/run /opt/bin/run
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group
COPY --chown=bridge --from=builder /tmp /tmp
COPY --chown=bridge --from=builder /data /data

USER bridge
EXPOSE 8502
VOLUME ["/data"]
ENTRYPOINT ["/opt/bin/run", "mnp"]
