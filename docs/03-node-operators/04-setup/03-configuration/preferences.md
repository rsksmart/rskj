# Set Config Preferences
The Rootstock node can be started with different
[CLI flags](/node-operators/setup/configuration/cli/).

## Setting config preferences

See how to set your config:

- [Using Ubuntu or Docker](#using-ubuntu-or-docker)
- [Using the `java` command](#using-java-command)

&hellip; to run the node.

> Remember:
> You need to **restart** the node if you've changed any configuration option.

### Using Ubuntu or Docker

Your node's config file is in `/etc/rsk`.
Default configurations are defined there and they are the same as [the config](https://github.com/rsksmart/artifacts/tree/master/rskj-ubuntu-installer/config).

You should edit the config related with the network you are using (`mainnet.conf`, `testnet.conf`, `regtest.conf`).
Check [reference](/node-operators/setup/configuration/reference) all the configuration options you could change.

### Using Windows

For other operating systems, including Windows, please use the `-Drsk.conf.file` option as specified below.


### Using `java` command

#### 1. Create a `.conf` file

You can create a file with the configuration options that you want to replace from the default.
Default configurations are defined in the [Config](https://github.com/rsksmart/rskj/tree/master/rskj-core/src/main/resources/config).

The extension of the file must be `.conf`.
Check [here](/node-operators/setup/configuration/reference/) for all the configuration option.

As an example, if you want to change the default `database directory`, your config file should only contain:

``` conf
database {
    dir = /new/path/for/database
    reset = false
}
```

#### 2. Specify your config file path

To apply your configuration options, you need to set your own config file's path when you run your node.

This can be done in two ways:

- Running the node with the `java` command, add `-Drsk.conf.file=path/to/your/file.conf`
- Compiling the node with IntelliJ, add to VM options: `-Drsk.conf.file=path/to/your/file.conf`

### Using RocksDB

:::warning[LevelDB is deprecated — migrate to RocksDB]

RocksDB is the default and the recommended storage engine. **LevelDB is deprecated and will be removed in a future release.** A node opening a LevelDB database logs a warning to that effect on every start.

If your node still runs on LevelDB, migrate it. Do not set `keyvalue.datasource=leveldb` on a new node.

:::

Starting from [RSKj HOP v4.2.0](https://github.com/rsksmart/rskj/releases/tag/HOP-4.2.0), RocksDB is no longer experimental, and it replaced [LevelDB](https://dbdb.io/db/leveldb) as the default storage library — a change made to address LevelDB's maintainability and performance issues.

#### Get Started

RSKj nodes run using RocksDB by default, so a node set up from scratch needs no configuration here. The `keyvalue.datasource` property may only be either `rocksdb` or `leveldb`.

#### Migrating an existing node to RocksDB

An existing database cannot be reopened under a different engine, so switching means starting the node against an empty database directory. There are two ways to get there:

- **[`DbMigrate`](/node-operators/setup/configuration/cli/#dbmigrate)** — converts the database you already have into the new engine, keeping your synced history and downloading nothing.
- **[Import sync](/node-operators/setup/import-sync/)** — restarting with `--import` erases the database and rebuilds it from published bootstrap data under the new engine. Faster than re-syncing, but it discards your current database and comes back at the published height, so the node has to sync the difference again.

The following sample command switches a node that was previously running on `leveldb` over to `rocksdb` using import sync:

> Note the use of the `--import` flag, which resets and re-imports the database.

```java
java -Dkeyvalue.datasource=rocksdb -jar ./rskj-core/build/libs/rskj-core-*-all.jar --testnet --import
```

#### Advantages:

* RocksDB uses a log structured database engine, written entirely in C++, for maximum performance. Keys and values are just arbitrarily-sized byte streams.
* RocksDB is optimized for fast, low latency storage such as flash drives and high-speed disk drives. RocksDB exploits the full potential of high read/write rates offered by flash or RAM.
* RocksDB is adaptable to different workloads. From database storage engines such as [MyRocks](https://github.com/facebook/mysql-5.6) to [application data caching](http://techblog.netflix.com/2016/05/application-data-caching-using-ssds.html) to embedded workloads, RocksDB can be used for a variety of data needs.
* RocksDB provides basic operations such as opening and closing a database, reading and writing to more advanced operations such as merging and compaction filters.

### Switching between DB Kinds**

Switching between different types of databases in your system requires you to modify configuration files, drop the existing database, and restart your node so the node will start syncing from scratch using the new db kind.

:::warning[Warning]

A node keeps whatever engine its existing database was created with — an upgrade does not move a LevelDB node to RocksDB on its own. Nodes set up from scratch use RocksDB by default. If yours is still on LevelDB, see [Migrating an existing node to RocksDB](#migrating-an-existing-node-to-rocksdb).

:::

### Gas Price Setting

The value returned by `eth_gasPrice` can be modified by setting a multiplier to
be used while calculating the aforementioned gas price.

This can be done by setting a numeric value on `rpc.gasPriceMultiplier` in the
configuration file. Default value is `1.1`.

### Troubleshooting

#### UDP port already in use

If you see the following error message,
it means that RSKj is unable to bind to a particular port number,
because prior to this, another process has already bound to the same port number.

```
Exception in thread "UDPServer" co.rsk.net.discovery.PeerDiscoveryException: Discovery can't be started.
        at co.rsk.net.discovery.UDPServer$1.run(UDPServer.java:65)
Caused by: java.net.BindException: Address already in use: bind
```

To rectify this,
change the value of `peer.port` in the config file,
or add a `peer.port` flag to the command when you start RSKj.

<Tabs>
  <TabItem value="mac" label="Linux, Mac OSX" default>
    ```shell
      $ java -Dpeer.port=50505 -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start
    ```
  </TabItem>
  <TabItem value="windows" label=" Windows">
    ```shell
      C:\> java -Dpeer.port=50505 -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start
    ```
  </TabItem>
</Tabs>