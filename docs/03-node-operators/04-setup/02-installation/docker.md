# Setup node on Docker

Before installing Docker, ensure your system meets the [minimum requirements](/node-operators/setup/requirements/) before installing the Rootstock node (RSKj).
If you already have docker installed. See how to [Install the RSKj node using Docker](#install-rskj-using-docker).

## Install Docker Desktop Client

[Docker Desktop](https://www.docker.com/products/docker-desktop/) provides an easy and fast way for running containerized applications on various operating systems.


<Tabs>
  <TabItem value="mac" label="Mac OSX, Windows" default>
    - [Download](https://www.docker.com/products/docker-desktop) and install
    - Start the Docker Desktop client
    - Login with a Docker Hub free account
  </TabItem>
  <TabItem value="linux" label="Linux">
   - Install [Docker Engine Community](https://docs.docker.com/install/linux/docker-ce/ubuntu/)
   - Note that you will need to use `sudo` for all docker commands, by default. To avoid this [additional steps](https://docs.docker.com/install/linux/linux-postinstall/) are required.
  </TabItem>
</Tabs>

:::tip[For Mac M1 / M2 (Apple Chips) using x86 based software]

- Ensure you have `Rosetta` installed. This is typically pre-installed on recent macOS versions.
- Download an x86 JDK build, such as [Azul Zulu 17 (x86)](https://www.azul.com/downloads/?version=java-17-lts&os=macos&package=jdk#zulu), to ensure compatibility with x86 based software.

:::

Ensure that docker is running by running the following command - it should run without any errors.

```shell
docker ps
```

You should see the following response:

```text
CONTAINER ID   IMAGE     COMMAND   CREATED   STATUS    PORTS     NAMES
```

More information about Docker install [here](https://docs.docker.com/install/).

## Install RSKj Using Docker

To install a RSKj node using Docker, visit the [Docker Hub](https://hub.docker.com/r/rsksmart/rskj) for installation instructions or use the [Reproducible Build](/node-operators/setup/reproducible-build).

## Logging in RSKj

By default, logs are exclusively directed to a single file. However, if you wish to enable the logging output to STDOUT, you can specify this system property via the command line using `-Dlogging.stdout=<LOG_LEVEL>`. That command should look something like this:

```java
java -Dlogging.stdout=INFO -cp <classpath> co.rsk.Start --reset --<RSK network>
```

Regarding the RSKj Docker containers, logs are printed to STDOUT by default, making it easy to view the logs while the container is running. In order to modify this, you can run the Docker container with the environment variable set to a different LOG_LEVEL (For example, DEBUG log level). That command should follow this structure:

```bash
docker run -e RSKJ_LOG_PROPS=DEBUG <container-name>
```