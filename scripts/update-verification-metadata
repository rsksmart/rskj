#!/bin/sh
set -e

# remove the verification-metadata.xml file with old deps (if any), and replace it with the template
if [ -f ./gradle/verification-metadata.xml ]; then
  rm ./gradle/verification-metadata.xml
  cp ./scripts/verification-metadata-template.xml ./gradle/verification-metadata.xml
fi

# generate verification-metadata.xml
./gradlew --no-daemon --refresh-dependencies --write-verification-metadata sha256 assemble
