FROM eclipse-temurin:17-jdk@sha256:0613a19436dc8f745914b25235d43f3b0eddb8d432d19edce30ffaf2d2f95403 AS build

RUN apt-get update -y && \
    apt-get install -y git curl gnupg

RUN useradd -ms /bin/bash rsk
USER rsk

WORKDIR /home/rsk
COPY --chown=rsk:rsk . ./

RUN gpg --keyserver https://secchannel.rsk.co/SUPPORT.asc --recv-keys 1DC9157991323D23FD37BAA7A6DBEAC640C5A14B && \
    gpg --verify --output SHA256SUMS SHA256SUMS.asc && \
    sha256sum --check SHA256SUMS && \
    ./configure.sh && \
    ./gradlew --no-daemon clean build -x test && \
    file=rskj-core/src/main/resources/version.properties && \
    version_number=$(sed -n 's/^versionNumber=//p' "$file" | tr -d "\"'") && \
    modifier=$(sed -n 's/^modifier=//p' "$file" | tr -d "\"'") && \
    cp "rskj-core/build/libs/rskj-core-$version_number-$modifier-all.jar" rsk.jar

FROM eclipse-temurin:17-jre@sha256:38e0afc86a10bf4cadbf1586fb617b3a9a4d09c9a0be882e29ada4ed0895fc84
LABEL org.opencontainers.image.authors="ops@rootstocklabs.com"

RUN mkdir -p /var/lib/rsk && \
    chown -R 1000:1000 /var/lib/rsk

USER ubuntu

RUN mkdir -p /var/lib/rsk/.rsk && \
    chown -R ubuntu:ubuntu /var/lib/rsk && \
    chmod -R 755 /var/lib/rsk/.rsk

WORKDIR /var/lib/rsk
COPY --from=build --chown=rsk:rsk /home/rsk/rsk.jar ./

ENV DEFAULT_JVM_OPTS="-Xms4G"
ENV RSKJ_SYS_PROPS="-Drpc.providers.web.http.bind_address=0.0.0.0 -Drpc.providers.web.http.hosts.0=localhost -Drpc.providers.web.http.hosts.1=127.0.0.1 -Drpc.providers.web.http.hosts.2=::1"
ENV RSKJ_LOG_PROPS="-Dlogging.stdout=INFO"
ENV RSKJ_CLASS=co.rsk.Start
ENV RSKJ_OPTS=""

ENTRYPOINT ["/bin/sh", "-c", "exec java $DEFAULT_JVM_OPTS $RSKJ_SYS_PROPS $RSKJ_LOG_PROPS -cp rsk.jar $RSKJ_CLASS $RSKJ_OPTS \"${@}\"", "--"]

