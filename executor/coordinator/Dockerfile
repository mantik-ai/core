# Note: multistage build!
# Note: Parametrized
# Idea: https://medium.com/@chemidy/create-the-smallest-and-secured-golang-docker-image-based-on-scratch-4752223b7324
FROM alpine:3.9 as builder
RUN apk update && apk add --no-cache ca-certificates

ARG input_executable=target/coordinator_linux


RUN adduser -D -g '' coordinator
RUN mkdir /opt/bin
ADD ${input_executable} /opt/bin/run

# Step 2, converting it into a scratch image
FROM scratch

COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
COPY --from=builder /opt/bin/run /opt/bin/run
COPY --from=builder /etc/passwd /etc/passwd

USER coordinator
EXPOSE 8503
ENTRYPOINT ["/opt/bin/run"]


