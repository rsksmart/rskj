# Setup node using Java

To setup a Rootstock node using Java, you need to:

- Ensure your system meets the [minimum requirements](/node-operators/setup/requirements/) for installing the Rootstock node.
- Install [Java 17 JDK](https://www.java.com/download/).

:::warning[Important]

Starting with [v6.4.0](/changelog/), the minimum supported Java LTS version is Java 17. Previous Java versions will no longer be supported.

:::

:::tip[For Mac M1 / M2 (Apple Chips) using x86 based software]

- Ensure you have `Rosetta` installed. This is typically pre-installed on recent macOS versions.
- Download an x86 JDK build, such as [Azul Zulu 17 (x86)](https://www.azul.com/downloads/?version=java-17-lts&os=macos&package=jdk#zulu), to ensure compatibility with x86 based software.

:::

## Video walkthrough

<Video url="https://www.youtube-nocookie.com/embed/TxpS6WhxUiU?cc_load_policy=1" thumbnail="/img/thumbnails/install-node-java-thumbnail.png" />

## Install the node using a JAR file

### Download and Setup

1. **Download the JAR**: Download the Fat JAR or Uber JAR from [RSKj releases](https://github.com/rsksmart/rskj/releases), or compile it [reproducibly](https://github.com/rsksmart/rskj/wiki/Reproducible-Build).

2. **Create Directory**: Create a directory for the node.
```jsx
mkdir rskj-node-jar
cd ~/rskj-node-jar
```
3. **Move the JAR**: Move or copy the just downloaded jar file to your directory.
```jsx
mv ~/Downloads/rskj-core-7.0.0-LOVELL-all.jar SHA256SUMS.asc /Users/{user}/rskj-node-jar/
```

<!-- ### Configuration
1. **Create Config Directory**: Create another directory inside `~/rskj-node-jar/config`
```jsx
  mkdir config
```
2. **Download Config File**: Get `node.conf` from [here](https://github.com/rsksmart/rif-relay/blob/main/docker/node.conf).
3. **Move Config File**: Move the `node.conf` file to the `config` directory. -->

### Run the Node

<Tabs>
  <TabItem value="1" label="Linux, Mac OSX" default>
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start
    ```
  </TabItem>
  <TabItem value="2" label="Windows">
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start
    ```
  </TabItem>
</Tabs>

:::tip[Tip]

Replace `<PATH-TO-THE-RSKJ-JAR>` with the actual path to your JAR file. For example, `C:/RskjCode/rskj-core-7.0.0-LOVELL-all.jar`.
:::

## Using Import Sync

Instead of the default synchronization, you can use import sync to import a pre-synchronized database from a trusted origin, which is significantly faster.

<Tabs>
  <TabItem value="3" label="Linux, Mac OSX" default>
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --import
    ```
  </TabItem>
  <TabItem value="4" label="Windows">
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --import
    ```
  </TabItem>
</Tabs>

### Resolving memory issues

**Memory Issues?** If you encounter memory errors and meet the [minimum hardware requirements](/node-operators/setup/requirements/), consider using `-Xmx4G` flag to allocate more memory as shown below:

<Tabs>
  <TabItem value="5" label="Linux, Mac OSX" default>
    ```shell
    java -Xmx4G -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --import
    ```
  </TabItem>
  <TabItem value="6" label="Windows">
    ```shell
    C:\> java -Xmx4G -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --import
    ```
  </TabItem>
</Tabs>

:::tip[Tip]

Replace `<PATH-TO-THE-RSKJ-JAR>` with your JAR file path. For configuration details, see [`database.import` setting](/node-operators/setup/configuration/reference#databaseimport).
:::

## Check the RPC

:::info[Info]

After starting the node, if there's no output, this means it's running correctly.
:::

1. To confirm, open a new console tab (it is important you do not close this tab or interrupt the process) and test the node's RPC server. A sample cURL request:

<Tabs>
  <TabItem value="7" label="Linux, Mac OSX" default>
    ```shell
    curl http://localhost:4444 -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":67}'
    ```
  </TabItem>
  <TabItem value="8" label="Windows">
    ```shell
    curl http://localhost:4444 -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"web3_clientVersion","params":[],"id":67}'
    ```
  </TabItem>
</Tabs>

Output:

```shell
{"jsonrpc":"2.0","id":67,"result":"RSKj/6.6.0/Mac OS X/Java17/SNAPSHOT-95a8f1ab84"}
```

2. To check the block number:

<Tabs>
  <TabItem value="9" label="Linux, Mac OSX" default>
     ```shell
    curl -X POST http://localhost:4444/ -H "Content-Type: application/json" --data '{"jsonrpc":"2.0", "method":"eth_blockNumber","params":[],"id":1}'
    ```
  </TabItem>
  <TabItem value="10" label="Windows">
    ```windows-command-prompt
    curl -X POST http://localhost:4444/ -H "Content-Type: application/json" --data '{"jsonrpc":"2.0", "method":"eth_blockNumber","params":[],"id":1}'
    ```
  </TabItem>
</Tabs>

Output:
```jsx
{"jsonrpc":"2.0","id":1,"result":"0x3710"}
```

:::success[Success]
Now, you have successfully setup a Rootstock node using the jar file.
The `result` property represents the latest synced block in hexadecimal.
:::

## Switching networks

To change networks on the RSKj node, use the following commands:

- Mainnet
    ```bash
    java -cp <PATH-TO-THE-RSKJ-FATJAR> co.rsk.Start
    ```
- Testnet
    ```bash
    java -cp <PATH-TO-THE-RSKJ-FATJAR> co.rsk.Start --testnet
    ```
- Regtest
    ```bash
    java -cp <PATH-TO-THE-RSKJ-FATJAR> co.rsk.Start --regtest
    ```

:::tip[Tip]
Replace `<PATH-TO-THE-RSKJ-FATJAR>` with the actual path to your jar file. For example: `C:/RskjCode/rskj-core-7.0.0-LOVELL-all.jar`.
:::