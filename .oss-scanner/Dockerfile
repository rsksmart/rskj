# Build environment for Anthropic's OSS Scanner (https://github.com/anthropics/oss-scanner).
#
# The scanner builds this file with the repository checkout as the build context, with network
# access. It then adds its own layer (Claude Code, compilers, debuggers), moves the image to a
# network with no Internet access, and opens a root shell in /src. Everything the agent needs to
# build, re-build and test RSKj has to be fetched here, while the network is still up.
#
# Contract this file has to honour:
#   * the checkout lives at /src and is already built there (`COPY . /src` + WORKDIR /src);
#   * the final stage keeps a package manager (eclipse-temurin is Ubuntu-based, so apt-get is
#     present) -- the scanner's layer fails on scratch/distroless images;
#   * the whole build fits in 45 minutes on 16 CPUs / 64 GB, so the full test suite is out and
#     only a smoke subset runs here;
#   * the audit itself runs on 2 CPUs / 8 GB, which is why .oss-scanner/AGENT-NOTES.md tells the
#     agent to use `--offline` and to stay away from `parallelTest`.
#
# This is deliberately NOT the repository's root Dockerfile: that one is the published node image
# (a slim JRE runtime holding only rsk.jar, built by .github/workflows/docker-release-*.yml) and
# has neither the sources nor the Gradle cache an offline audit needs.

# Same digest-pinned JDK the root Dockerfile builds with.
FROM eclipse-temurin:17-jdk@sha256:0613a19436dc8f745914b25235d43f3b0eddb8d432d19edce30ffaf2d2f95403

# LANG, not JAVA_TOOL_OPTIONS: the latter makes every JVM print "Picked up JAVA_TOOL_OPTIONS" to
# stderr, which would contaminate the test output and node logs the auditing agent has to read.
ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

# GRADLE_USER_HOME is set explicitly rather than left at ~/.gradle: the build below runs as root,
# but it is an ENV so the cache is still found whatever user the scanner's layer or the agent ends
# up as. Without this a `~` change silently re-points Gradle at an empty, un-downloadable cache.
ENV GRADLE_USER_HOME=/opt/gradle-home \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

# `curl ca-certificates git gnupg` are what the build itself needs. The rest are the packages the
# scanner's own layer tries to install anyway -- putting them here means the audit still has them
# if that best-effort step is ever trimmed.
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        file \
        git \
        gnupg \
        jq \
        less \
        procps \
        python3 \
        unzip \
        xxd \
    && rm -rf /var/lib/apt/lists/*

COPY . /src
WORKDIR /src

# configure.sh fetches gradle/wrapper/gradle-wrapper.jar (not committed) and checks it against a
# pinned SHA-256. Verify configure.sh itself first, exactly as .github/workflows/build_and_test.yml
# and the reproducible-build recipe do, so a tampered checkout cannot smuggle in a wrapper.
RUN curl -sSL https://secchannel.rsk.co/SUPPORT.asc | gpg --import - \
 && gpg --verify --output SHA256SUMS SHA256SUMS.asc \
 && sha256sum --check SHA256SUMS \
 && rm -f SHA256SUMS \
 && ./configure.sh

# Compile everything, including all three test source sets, and build the fat jar (`assemble`
# covers it via `artifacts { archives fatJar }`). This is the step that must not fail: if RSKj does
# not compile there is nothing to audit.
RUN ./gradlew --no-daemon --stacktrace assemble testClasses integrationTestClasses fuzzTestClasses

# Force every remaining configuration -- test/integrationTest/fuzzTest runtime classpaths, the
# JaCoCo agent, the Checkstyle tool -- into the cache. See .oss-scanner/prefetch.gradle.
RUN ./gradlew --no-daemon -I .oss-scanner/prefetch.gradle ossScannerPrefetch

# Checkstyle and Spotless resolve some of their classpath lazily, at task execution, so `check`
# has to actually run once to warm it. Its *result* is deliberately not a gate: a style regression
# on master must not cost the project an entire security scan.
RUN ./gradlew --no-daemon check -x test \
    || echo "WARNING: ./gradlew check reported failures; caches are warm, continuing"

# Run a couple of cheap, deterministic unit tests. This is what pulls the JUnit platform launcher,
# the JUnit 5 engine and the JaCoCo agent (`test` is finalizedBy jacocoTestReport) into the cache:
# compiling test code alone does not. Not a gate either -- a flaky test is not a reason to drop the
# audit.
RUN ./gradlew --no-daemon test \
        --tests 'co.rsk.util.HexUtilsTest' \
        --tests 'org.ethereum.util.ByteUtilTest' \
    || echo "WARNING: the smoke test run reported failures; caches are warm, continuing"

# The acceptance checks for this file, both hard gates. They run with the network still available,
# so a gap shows up here -- where it can be fixed -- instead of silently crippling the audit.
#
#   1. every configuration resolves from the cache alone (-PossScannerStrict fails on any that
#      does not), which covers integrationTest and fuzzTest without running either;
#   2. a real test actually executes offline, which covers the JUnit launcher and the JaCoCo agent.
RUN ./gradlew --no-daemon --offline -I .oss-scanner/prefetch.gradle ossScannerPrefetch -PossScannerStrict \
 && ./gradlew --no-daemon --offline test --tests 'co.rsk.util.HexUtilsTest'

# Notes for the auditing agent are committed at .oss-scanner/AGENT-NOTES.md and arrive with the
# COPY above: how to build offline, how to start a regtest node, and what resources this VM has.

CMD ["bash"]
