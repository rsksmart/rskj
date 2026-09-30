#  Configure Verbosity
You can configure the desired log verbosity for your Rootstock node installation according to your needs.
More information can be found at: [Logback Project](https://logback.qos.ch/index.html).

## Requirements

*  RSK Node Installed
*  SSH Access
*  SuperUser Access (`sudo`)

### Where to find RSK log files

Real time log:

```shell
/var/log/rsk/rsk.log
```

Compressed logs:

```shell
/var/log/rsk/rskj-YYYY-MM-DD.N.log.gz
```

## Log level options

When you configure your log level, all log items for that level and below get written to the log file. In the following table, the left column represents the possible values you can set in your configuration.

![Log Levels](https://i.stack.imgur.com/7o9Kk.png)

<!-- TODO fix this image, perhaps convert to a table -->

## Setting your desired verbosity configuration

You need to edit `logback.xml` file to set your desired level of verbosity.

```bash
sudo vi /etc/rsk/logback.xml
```

This file allows you to configure different log levels for different parts of the application.

```xml
    <logger name="execute" level="INFO"/>
    <logger name="blockvalidator" level="INFO"/>
    <logger name="blocksyncservice" level="TRACE"/>
    <logger name="blockexecutor" level="INFO"/>
    <logger name="general" level="DEBUG"/>
    <logger name="gaspricetracker" level="ERROR"/>
    <logger name="web3" level="INFO"/>
    <logger name="repository" level="ERROR"/>
    <logger name="VM" level="ERROR"/>
    <logger name="blockqueue" level="ERROR"/>
    <logger name="io.netty" level="ERROR"/>
    <logger name="block" level="ERROR"/>
    <logger name="minerserver" level="INFO"/>
    <logger name="txbuilderex" level="ERROR"/>
    <logger name="pendingstate" level="INFO"/>
    <logger name="hsqldb.db" level="ERROR"/>
    <logger name="TCK-Test" level="ERROR"/>
    <logger name="db" level="ERROR"/>
    <logger name="net" level="ERROR"/>
    <logger name="start" level="ERROR"/>
    <logger name="cli" level="ERROR"/>
    <logger name="txs" level="ERROR"/>
    <logger name="gas" level="ERROR"/>
    <logger name="main" level="ERROR"/>
    <logger name="trie" level="ERROR"/>
    <logger name="org.hibernate" level="ERROR"/>
    <logger name="peermonitor" level="ERROR"/>
    <logger name="bridge" level="ERROR"/>
    <logger name="org.springframework" level="ERROR"/>
    <logger name="rlp" level="ERROR"/>
    <logger name="messagehandler" level="ERROR"/>
    <logger name="syncprocessor" level="TRACE"/>
    <logger name="sync" level="ERROR"/>
    <logger name="BtcToRskClient" level="ERROR"/>
    <logger name="ui" level="ERROR"/>
    <logger name="java.nio" level="ERROR"/>
    <logger name="org.eclipse.jetty" level="ERROR"/>
    <logger name="wire" level="ERROR"/>
    <logger name="BridgeSupport" level="ERROR"/>
    <logger name="jsonrpc" level="ERROR"/>
    <logger name="wallet" level="ERROR"/>
    <logger name="blockchain" level="INFO"/>
    <logger name="blockprocessor" level="ERROR"/>
    <logger name="state" level="INFO"/>
    <logger name="messageProcess" level="INFO"/>

    <root level="DEBUG">
        <appender-ref ref="stdout"/>
        <appender-ref ref="FILE-AUDIT"/>
    </root>
```

* Save your changes
* RSK `logback.xml` config will watch and apply changes without restarting RSK Node.
  (The watcher can take up to 1 hour to notice the changes and reload the logging configuration)
* RSK logs with default installation will rotate on daily basis and/or when the log file reach 100MB

Using this configuration:

- Most areas of the application will **only** log `FATAL` and `ERROR` events for most areas of the application.
- The `execute`, `blockvalidator`, `blockexecutor`, `web3`, `minerserver`, `pendingstate`, `blockchain`, `messageProcess`, areas specify `INFO`, so those will **only** log `FATAL`, `ERROR`, `WARN`, and `INFO` events.
- The will be no `DEBUG`, `INFO`, and `TRACE` logs.

## Using logback configuration file

A logback configuration example can be downloaded from [here](https://github.com/rsksmart/artifacts/blob/master/rskj-ubuntu-installer/config/logback.xml).

#### Using logback with a installed node

If you're running a node [using the release jar file](/node-operators/setup/installation/java) use the following command:

```bash
java -cp rskj-core-7.0.0-LOVELL-all.jar -Dlogback.configurationFile=/full/path/to/logback.xml co.rsk.Start
```

#### Using logback with a compiled node

If you're running a node [by compiling the code](/node-operators/setup/installation/java) on VM Options *add*:

```shell
-Dlogback.configurationFile=/full/path/to/logback.xml
```

> Note: path should be written without abbreviations (full path)
