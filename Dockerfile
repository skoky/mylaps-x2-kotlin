FROM debian:buster-slim

RUN apt-get update \
    && apt-get install -y libzmq3-dev
