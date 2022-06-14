FROM openjdk:11-jdk-slim-buster AS build

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
    ./gradlew --no-daemon clean build -x test

RUN f=rskj-core/src/main/resources/version.properties && \
    version_number=$(sed -n 's/^versionNumber=//p' "$f" | tr -d "\"'") && \
    modifier=$(sed -n 's/^modifier=//p' "$f" | tr -d "\"'") && \
    cp "rskj-core/build/libs/rskj-core-$version_number-$modifier-all.jar" rsk.jar

FROM openjdk:11-jre-slim-buster

RUN useradd -ms /bin/bash rsk
USER rsk

WORKDIR /home/rsk
COPY --from=build /home/rsk/rsk.jar ./

ENTRYPOINT ["java", "-cp", "rsk.jar", "co.rsk.Start"]
