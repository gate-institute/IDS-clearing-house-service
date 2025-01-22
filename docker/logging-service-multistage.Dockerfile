FROM rust:1.72 AS builder
WORKDIR /app
COPY LICENSE clearing-house-app ./
RUN cargo build --release

FROM ubuntu:22.04

RUN apt-get update \
&& echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections \
&& apt-get --no-install-recommends install -y -q ca-certificates gnupg2 libssl3 libc6

RUN mkdir /server
WORKDIR /server

COPY --from=builder /app/target/release/logging-service .
COPY docker/entrypoint.sh .

ENTRYPOINT ["/server/entrypoint.sh"]
CMD ["/server/logging-service"]
