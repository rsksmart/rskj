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

:::info[Important Notice]
- Starting from [RSKj HOP v4.2.0](https://github.com/rsksmart/rskj/releases/tag/HOP-4.2.0), RocksDB is no longer experimental. As of the most recent version, RocksDB has now been made the default storage library, replacing LevelDB. This change was made to tackle maintainability and performance issues of LevelDB.
- Previously, RSKj ran using [LevelDB](https://dbdb.io/db/leveldb) by default, with the option to switch to [RocksDB](http://rocksdb.org/). Now, RocksDB is the default storage option, aiming to enable higher performance within the RSKj nodes.
:::

#### Get Started

RSKj nodes run using RocksDB by default (See important info section). To switch back to LevelDB, modify the relevant RSKj config file (`*.conf`) and set the config: `keyvalue.datasource=leveldb`.

The `keyvalue.datasource` property in the config
may only be either `rocksdb` or `leveldb`.

> If you wish to switch between the different storage options,
for example from `leveldb` to `rocksdb` or vice versa, 
you must **restart** the node with the import option.

The following sample command shows how to do this when
the RSKj node was previously running the default (`leveldb`),
and wants to run with `rocksdb` next.

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

Nodes that were already running on LevelDB will continue to use LevelDB, and the same applies to RocksDB. However, all nodes setup from scratch will use RocksDB by default.

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