# Gradle building
## Setup instructions for gradle build in docker container

This is a deterministic build process used to build Rootstock node JAR file. It provides a way to be reasonably sure that the JAR is built from the [GitHub RSKj repository](https://github.com/rsksmart/rskj/releases). It also makes sure that the same tested dependencies are used and statically built into the executable.

It's highly recommended to follow the steps by yourself to avoid contamination of the process.

:::warning[Important]

Starting with [v6.4.0](/changelog/), the minimum supported Java LTS version is Java 17. Previous Java versions will no longer be supported. The following example for Linux OS is showing instructions for v6.3.1, and thus uses jdk 8, this must be replaced by jdk 17 for v6.4.0 and up.

:::

## Install Docker

Depending on your OS, you can install Docker following the official Docker guide:

- [Mac](https://docs.docker.com/docker-for-mac/install/)
- [Windows](https://docs.docker.com/docker-for-windows/install/)
- [Ubuntu](https://docs.docker.com/engine/installation/linux/ubuntu/)
- [CentOS](https://docs.docker.com/engine/installation/linux/centos/)
- [Fedora](https://docs.docker.com/engine/installation/linux/fedora/)
- [Debian](https://docs.docker.com/engine/installation/linux/debian/)
- [Others](https://docs.docker.com/engine/installation/#platform-support-matrix)

:::info[Info]

See how to [Setup and Run RSKj using Java](/node-operators/setup/installation/java/).
:::

## Build Container

Create a ```Dockerfile``` to setup the build environment.

<Tabs>
  <TabItem value="linux" label="Linux" default>
      ```bash
        // FROM ubuntu:16.04
        apt-get update -y && \
          apt-get install -y git curl gnupg-curl openjdk-8-jdk && \
          rm -rf /var/lib/apt/lists/* && \
          apt-get autoremove -y && \
          apt-get clean
        gpg --keyserver https://secchannel.rsk.co/release.asc --recv-keys 1A92D8942171AFA951A857365DECF4415E3B8FA4
        gpg --finger 1A92D8942171AFA951A857365DECF4415E3B8FA4
        git clone --single-branch --depth 1 --branch LOVELL-7.0.0 https://github.com/rsksmart/rskj.git /code/rskj
        git clone https://github.com/rsksmart/reproducible-builds
        CP /Users/{$USER}/reproducible-builds/rskj/7.0.0-lovell/Dockerfile  /Users/{$USER}/code/rskj
        WORKDIR /code/rskj
        gpg --verify SHA256SUMS.asc
        sha256sum --check SHA256SUMS.asc
        ./configure.sh
        ./gradlew clean build -x test
    ```
  </TabItem>
  <TabItem value="mac" label="Mac OSX">
      ```bash
        brew update && \
        brew install git gnupg openjdk@8 && \
          rm -rf /var/lib/apt/lists/* && \
          brew autoremove && \
          brew cleanup
        gpg --keyserver https://secchannel.rsk.co/release.asc --recv-keys 1A92D8942171AFA951A857365DECF4415E3B8FA4
        gpg --finger 1A92D8942171AFA951A857365DECF4415E3B8FA4
        git clone --single-branch --depth 1 --branch LOVELL-7.0.0 https://github.com/rsksmart/rskj.git ./code/rskj
        git clone https://github.com/rsksmart/reproducible-builds
        CP /Users/{$USER}/reproducible-builds/rskj/7.0.0-lovell/Dockerfile  /Users/{$USER}/code/rskj
        cd ./code/rskj
        gpg --verify SHA256SUMS.asc
        sha256sum --check SHA256SUMS.asc
        ./configure.sh
        ./gradlew clean build -x test
      ```
  </TabItem>
</Tabs>

**Response:**

You should get the following as the final response,
after running the above steps:

```bash
BUILD SUCCESSFUL in 55s
14 actionable tasks: 13 executed, 1 up-to-date
```

:::tip[command not found: sha256sum]

If you get the error: zsh: command not found: sha256sum

Run the command  `brew install coreutils`
:::

If you are not familiar with Docker or the ```Dockerfile``` format: what this does is use the Ubuntu 16.04 base image and install ```git```, ```curl```, ```gnupg-curl``` and ```openjdk-8-jdk```, required for building the Rootstock node.


## Run build

To create a reproducible build, run the command below in the same directory:

```bash
docker build -t rskj/7.0.0-lovell .
```

:::danger[Error]

if you run into any problems, ensure you're running the commands on the right folder and also ensure docker daemon is running is updated to the recent version.  See how to [Setup node on Docker](/node-operators/setup/installation/docker/)

:::

This may take several minutes to complete. What is done is:
- Place in the RSKj repository root because we need Gradle and the project
- Runs the [secure chain verification process](/node-operators/setup/security-chain/)
- Compile a reproducible RSKj node
- `./gradlew clean build -x test` builds without running tests


## Verify Build

The last step of the build prints the `sha256sum` of the files, to obtain `SHA-256` checksums, run the following command in the same directory as shown above:

```bash
docker run --rm rskj/7.0.0-lovell sh -c 'sha256sum * | grep -v javadoc.jar'
```

## Check Results

After running the build process, a JAR file will be created in ```/rskj/rskj-core/build/libs/```, into the docker container.

You can check the SHA256 sum of the result file and compare it to the one published by Rootstock for that version.

```bash
604b75665d9750da216ddc9849cb2276a06192321b3c6829685600e1f2d534fb  rskj-core-7.0.0-LOVELL-all.jar
05fb616708088a6c65326c01d7e79b2c332d5f2ca83246c9075f65e5fa2781fe  rskj-core-7.0.0-LOVELL-sources.jar
8d131bbc8d1d346ec4a91ce4eb9db59f6c7649bb7698b11bc1abbaf33f75caaa  rskj-core-7.0.0-LOVELL.jar
e85d0783b39ef93fda5f98588f7e4ae5d57784096ce9b3a1f43eb3d99a49d275  rskj-core-7.0.0-LOVELL.module
d651adc77b82046a976bf5c7e858b741443bc8ffa8372b8e5bac9b92dc8c294d  rskj-core-7.0.0-LOVELL.pom
```

For SHA256 sum of older versions check the [releases page](https://github.com/rsksmart/rskj/releases).

If you check inside the JAR file, you will find that the dates of the files are the same as the version commit you are using.

More Resources
==============

* [Install Rootstock Node](/node-operators/setup/installation/)
* See [Reproducible builds](https://github.com/rsksmart/reproducible-builds/tree/master/rskj)
* Check out the [latest rskj releases](https://github.com/rsksmart/rskj/releases)