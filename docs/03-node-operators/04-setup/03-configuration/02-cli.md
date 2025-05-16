# Command Line Interface
Command line (CLI) arguments are arguments specified after the `RSK start` class. See [Config Options](/node-operators/setup/configuration/)

> New entry points (Java classes with static public main method) have been included from the [RSK Hop Release v4.3.0](https://github.com/rsksmart/rskj/releases/).

The CLI arguments have two forms; the parameter and the flag:

- **Parameter**
  - Has a name and an associated value, separated by a space
  - Starts with a dash
- **Flag**
  - It's a single text, without spaces
  - Starts with a double dash

Find below a list of CLI flags and parameters available:

## Parameters and Flags

### Network related

The following CLI flags determine which network the Rootstock (RSK) node will connect to.

- `--main`:
This indicates that the configuration for the Rootstock Mainnet (public network) should be used.
- `--testnet`:
This indicates that the configuration for the Rootstock Testnet (public network) should be used.
- `--regtest`:
This indicates that the configuration for the Rootstock Regtest (localhost network) should be used.
  - Example: `java -cp rsk-core-<VERSION>.jar co.rsk.start --regtest`

> - Only one of these three CLI flags should be specified.
> - When none of these are specified, **Rootstock Mainnet** is used by default.

### Database related

The Rootstock (RSK) node stores transactions, blocks,
and other blockchain state on disk.
This is known as the *Blockchain Database*.

- `--reset`:
This indicates that the block database should be erased, and should start from scratch,
i.e. from genesis block.
This is typically expected to be used when connecting to Rootstock Regtest,
or in select debugging scenarios.
It is also used when switching between different databases,
e.g. between `leveldb` and `rocksdb`.

- `--import`:
This indicates that the block database should be imported from an external source.
This is typically expected to be used when connecting to Rootstock Testnet or Rootstock Mainnet,
and when a reduction in "initial sync time" is desired.
It is also used when switching between different databases,
e.g. between `leveldb` and `rocksdb`.

### Configuration related

- `--verify-config`:
This indicates that the configuration file used by this run of the Rootstock node should be validated. By default this step is always performed.
- `--print-system-info`:
This indicates that the system information of the computer that the Rootstock node is running on should be output. By default, this is always output.
- `--skip-java-check`:
This indicates that the detection of the version of the Java Virtual Machine that the Rootstock node is running in is supported. By default, this check is always performed, to ensure that the Rootstock node is running in a compatible environment.
- `-base-path`:
This specifies the value of `database.dir`, where the blockchain database is stored.

> Example: `java -cp rsk-core-<VERSION>.jar co.rsk.start -base-path home/rsk/data`

- `-rpccors`
This specifies the value of `rpc.providers.web.cors` to control `cors configuration`.

> Example: `java -cp rsk-core-<VERSION>.jar co.rsk.start -rpccors *`

## Command Line Tools

It worth highlight that for some commands below that interacts with the database, you might and should set the network flag desired, like
`--regtest`, `--testnet` or `--main`. Otherwise, the default network will be used, which is the Rootstock Mainnet (public network).

### Database related commands

#### ExportState

The `ExportState` command is a tool for exporting the state at a specific block in the Rootstock blockchain to a file.

**Usage:**

- `java -cp rsk.jar co.rsk.cli.tools.ExportState -b <block_number> -f <file_path> --<network_flag>`

**Options:**

- `-b, --block`: The block number to export the state from.

- `-f, --file`: The path to a file to export the state to.

**Example:**

In this example, the state information of block 2000 will be exported to the file “test.txt” on the regtest network.

- `java -cp rsk.jar co.rsk.cli.tools.ExportState -b 2000 -f test.txt --regtest`

**Output:**

```shell
  INFO [clitool] [main]  ExportState started
  INFO [o.e.d.CacheSnapshotHandler] [main]  Loaded 194912 cache entries from 'unitrie/rskcache'
  INFO [clitool] [main]  ExportState finished
  INFO [o.e.d.CacheSnapshotHandler] [main]  Saved 194912 cache entries in 'unitrie/rskcache'
```

The “test.txt” should look like this:

```text
  5c0700caa04b0e28aa38d3d4a74a560332c38111cb1ac2292a89512d009658d2a7ed7d2cecc372b5c25799434b1cc5e49d795fc371db88e3d0c3635e273fc3c496b897fdc60c
  4ce5c4861124fac10fdda43f62df4cf8137136e4c654305a8e9e3572f76b46c9fd9ce59676
  …etc
```

#### ExportBlocks

The `ExportBlocks` command is a tool for exporting blocks from a specific block range to a file.
The tool retrieves the block range and exports each block in the range to a specified file.

**Usage:**

  ```java
    java -cp rsk.jar co.rsk.cli.tools.ExportBlocks -fb <from_block_number>  -tb <to_block_number> -f <file_path>
  ```

**Options:**

    > `-fb, --fromBlock`: The block number to start exporting from.
    > `-tb, --toBlock`: The block number to stop exporting.

**Example:**

In this example, the blocks from block 2000 to block 3000 will be exported to the specified file.

```java
  java -cp rsk.jar co.rsk.cli.tools.ExportBlocks -fb 2000 -tb 3000  -f   test-blocks.txt
```

**Output:**

```shell
  2023-04-24-16:23:25.0897 INFO [clitool] [main]  ExportBlocks started
  2023-04-24-16:23:26.0373 INFO [clitool] [main]  ExportBlocks finished
```

Blocks.txt should show the following:

```text
  50,d6c7a4388337d931d9478e742c34c276cefe0976e12b2f7077bd6664b6ecc163,0629fdd6…
  51,d4a9091304e64008f06c395b4f26da1c710bdd83f30f0d15666826b57c9b7a1e,0651fde4b…
  …
  99,92c333550ada4b2588d957155f1aa130ef0092b9499d575a3e4034e1f3f20926,0f271cc73…
  100,b445214290eb98e1e066713aa8a76ff4282c7890d232471d62be5932d21f25b8,0f57a…
```

### Configuration related commands

#### StartBootstrap

The `StartBootcamp` command starts a bootstrap node with one service, which only participates in the [peer discovery protocol](https://github.com/ethereum/devp2p/wiki/Discovery-Overview).

**Example:**

`java -cp rsk.jar co.rsk.cli.tools.StartBootstrap`

**Output:**

```shell
  2023-04-24-15:51:14.0793 INFO [fullnoderunner] [main]  Starting RSK
  2023-04-24-15:51:14.0794 INFO [fullnoderunner] [main]  Running orchid-testnet.json,
  core version: 5.1.0-SNAPSHOT
  ....
```

#### RewindBlocks

The `RewindBlocks` command is used to rewind the Rootstock blockchain to a specific block. It can also be used to find and print the minimum inconsistent block (a block with missing state in the states database).

**Example:**

```shell
  java -cp rsk.jar co.rsk.cli.tools.RewindBlocks [-b <BLOCK_NUMBER>]  [-fmi] (or) [-rbc] --<network_flag>
```

**Options:**

- `-b, --block=BLOCK_NUMBER_TO_REWIND_TO` block number to rewind blocks to.
- `-fmi, --findMinInconsistentBlock` flag to find a min inconsistent block. The default value is `false`.
- `-rbc, --rewindToBestConsistentBlock` flag to rewind to the best consistent block. The default value is `false`.
-
    > - An inconsistent block is the one with missing state in the states db (this can happen in case of improper node shutdown).
    > - `fmi` option can be used for finding a minimum inconsistent block number and printing it to stdout. It will print -1, if no such block is found.
    > - `rbc` option does two things: it looks for a minimum inconsistent block and, if there's such, rewinds blocks from top one till the found one.
    > - Note that all three options are mutually exclusive, you can specify only one per call.

**Example:**

`java -cp rsk.jar co.rsk.cli.tools.RewindBlocks -b 2000 —regtest`

**Output:**

```shell
  INFO [clitool] [main]  RewindBlocks started
  INFO [clitool] [main]  Highest block number stored in db: 49703
  INFO [clitool] [main]  Block number to rewind to: 2000
  INFO [clitool] [main]  Rewinding...
  INFO [clitool] [main]  Done
  INFO [clitool] [main]  New highest block number stored in db: 2000
  INFO [clitool] [main]  RewindBlocks finished
```

#### DbMigrate

The `DbMigrate` command is a tool for migrating between different databases such as leveldb and rocksdb.

**Usage:**

`java -cp rsk.jar co.rsk.cli.tools.DbMigrate -t <target_database>`

**Options:**

- `-t, --targetDb`: The target database to migrate to. (“leveldb” or “rocksdb”).

**Example:**

In this example, the current database will be migrated from leveldb to rocksdb.

`java -cp rsk.jar co.rsk.cli.tools.DbMigrate -t rocksdb`

**Output:**

```shell
  INFO [clitool] [main]  DbMigrate started
  INFO [clitool] [main]  DbMigrate finished
```

:::tip[Tip]

 If the target database is the same as the one working on the node, the node will throw an error: **Cannot migrate to the same database, current db is X_DB and target db is X_DB**.

:::

## Dev-related commands

#### ShowStateInfo

The `ShowStateInfo` command is a tool for displaying state information of a specific block in the Rootstock blockchain.

**Usage:**

- `java -cp rsk.jar co.rsk.cli.tools.ShowStateInfo -b <block_number>`

**Options:**

- `-b, --block`: The block number or "best" to show the state info.

**Example:**

- `java -cp rsk.jar co.rsk.cli.tools.ShowStateInfo -b 20000`

> In this example, the state information of block 20000 will be displayed.

**Output:**

```shell
    INFO [clitool] [main]  ShowStateInfo started
    INFO [clitool] [main]  Block number: 20000
    INFO [clitool] [main]  Block hash: 53fe6e9269d26a38d15f368a3b8b647ae6b66e4fe27bd6bd6ee5f4b675129753
    INFO [clitool] [main]  Block parent hash: 6f49a755c9d6b74d25e15787232887c9dba8e713a8455d9beed97b08bf900b17
    INFO [clitool] [main]  Block root hash: 587e0645e09d60af77ee04591ac843af14c99f6c498713aeb565f25f2d419cd0
    INFO [clitool] [main]  Trie nodes: 53
    INFO [clitool] [main]  Trie long values: 0
    INFO [clitool] [main]  Trie MB: 0.002902984619140625
    INFO [clitool] [main]  ShowStateInfo finished
```

#### IndexBlooms

The `IndexBlooms` is a tool for indexing block blooms for a specific block range.

**Usage:**

```shell
    java -cp rsk.jar co.rsk.cli.tools.IndexBlooms [-fb=<fromBlock>] [-tb=<toBlock>]
```

**Options:**

- `-fb, --fromBlock=<fromBlock>`: From block number (required)

- `-tb, --toBlock=<toBlock>`: To block number (required)

**Example:**

In this example we are indexing block blooms from block number 100 to 200.

`java -cp rsk.jar co.rsk.cli.tools.IndexBlooms -fb=100 -tb=200`

**Output:**

```
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 28% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 45% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 58% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 71% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 81% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 92% of blocks
  INFO [c.r.c.t.IndexBlooms] [main]  Processed 100% of blocks
```

> The `IndexBlooms` CLI tool indexes block blooms for a specific block range. The required arguments are `fromBlock` and `toBlock`, which specify the block range to be indexed.

#### ImportState

The `ImportState` command is a tool for importing state data from a file into the Rootstock blockchain.

**Usage:**

- `java -cp rsk.jar co.rsk.cli.tools.ImportState -f <file_path>`

**Options:**

- `-f, --file`: The path to the file to import state from.

**Example:**

In this example, the state data from the file located at `test.txt` will be imported into the Rootstock blockchain. Keep in mind that we are using the previously generated state in the `ExportState` example.

- `java -cp rsk.jar co.rsk.cli.tools.ImportState -f test.txt`

**Output:**

```text
  INFO [clitool] [main]  ImportState started
  INFO [clitool] [main]  ImportState finished
```

#### ImportBlocks

The `ImportBlocks` command is a tool for importing blocks from a file into the Rootstock blockchain. The tool reads a file containing blocks, decodes them and saves them to the Rootstock database.

**Usage:**

`java -cp rsk.jar co.rsk.cli.tools.ImportBlocks -f <file_path>`

**Options:**

- `-f, --file`: The path to a file to import blocks from.

**Example:**

In this example, blocks will be imported from the file `/path/to/blocks_file.txt`.

- `java -cp rsk.jar co.rsk.cli.tools.ImportBlocks -f /path/to/blocks_file.txt`

**Output:**

```text
  INFO [clitool] [main]  ImportBlocks started
  INFO [clitool] [main]  ImportBlocks finished
```

#### ExecuteBlocks

The `ExecuteBlocks` command is a tool for executing blocks for a specified block range. This command is useful for testing purposes, debugging or for analyzing the behavior of a blockchain in a given range of blocks.

**Usage:**

- `java -cp rsk.jar co.rsk.cli.tools.ExecuteBlocks -fb <from_block_number> -tb <to_block_number> --<network_flag>`

**Options:**

- `-fb, --fromBlock`: The starting block number.
- `-tb, --toBlock`: The ending block number.

**Example:**

In this example, blocks from 100000 to 200000 will be executed on the regtest network.

```shell
  java -cp rsk.jar co.rsk.cli.tools.ExecuteBlocks -fb 100000 -tb 200000 –regtest
```

**Output:**

```shell
  2023-04-24-16:27:58.0408 INFO [clitool] [main]  ExecuteBlocks started
  2023-04-24-16:28:02.0881 INFO [clitool] [main]  ExecuteBlocks finished
```

#### ConnectBlocks

The `ConnectBlocks` command is a tool for connecting blocks to a chain from an external source file.

**Usage:**
- `java -cp rsk.jar co.rsk.cli.tools.ConnectBlocks -f <file_path>`

**Options:**
- `-f, --file`: The path to the file containing the blocks to connect.

**Example:**

In this example, the blocks contained in the file located at `/path/to/blocks.txt` will be connected to the chain.

- `java -cp rsk.jar co.rsk.cli.tools.ConnectBlocks -f /path/to/blocks.txt`

#### GenerateOpenRpcDoc

The `GenerateOpenRpcDoc` command is a tool for generating an OpenRPC JSON doc file.

**Usage:**

```shell
  java -cp rsk.jar co.rsk.cli.tools.GenerateOpenRpcDoc -v <rskj_version> -d <work_dir_path> -o <output_file_path>
```

**Options:**

  - `-v, --version`: The RSKj version that will be present in the final docs

  - `-d, --dir`: The work directory where the JSON template and individual JSON files are present.

  - `-o, --outputFile`: The destination file containing the final OpenRPC JSON doc.

**Example:**

In this example, the tool will generate an OpenRPC JSON doc file located at `/path/to/output.json`.

```shell
  java -cp rsk.jar co.rsk.cli.tools.GenerateOpenRpcDoc -v 1.0.0 -d /path/to/workdir -o /path/to/output.json
```

**Output:**

```text
  2023-04-24-16:35:00.0617 INFO [c.r.c.t.GenerateOpenRpcDoc] [main]  Loading template...
  2023-04-24-16:35:00.0620 INFO [c.r.c.t.GenerateOpenRpcDoc] [main]  Loading file doc/rpc/template.json
  ...
```

**JSON output file**:

```json
  {"openrpc" : "1.2.6",
    "info" : {
    "version" : "5.0.0",
    "title" : "RSKj JSON-RPC",
    … etc
  }
```

## Configuration over CLI

Besides the parameters and flags, it's also possible to configure the node over the CLI using the JVM parameters, which starts with the prefix `-D` followed by the full path of the configuration like it is placed inside the configuration file.

**Example:**

```shell
  java -cp rskj-core-<VERSION>.jar -Ddatabase.dir=/home/rsk/data co.rsk.Start
```

Most of the configurable options or settings for RSKj are available
in "config". See [config reference](/node-operators/setup/configuration/reference/) for more details.

## Reference implementation

See the definition of the CLI flags in the RSKj codebase:
[`NodeCliFlags` in `NodeCliFlags.java`](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/java/co/rsk/config/NodeCliFlags.java)

See the definition of the CLI parameters in the RSKj codebase:
[`NodeCliOptions` in `NodeCliOptions.java`](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/java/co/rsk/config/NodeCliOptions.java)
