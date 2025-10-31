# Mining Simulation Feature Implementation Summary

## Overview

We have successfully implemented a new timed mining feature for RSK nodes that creates blocks at intervals following an exponential distribution with a configurable median time. This feature is similar to the existing automine feature but operates on a time-based schedule rather than transaction-based triggers.

## What Was Implemented

### 1. New TimedMinerClient Class
- **Location**: `rskj/rskj-core/src/main/java/co/rsk/mine/TimedMinerClient.java`
- **Purpose**: Implements the `MinerClient` interface with exponential distribution-based timing
- **Key Features**:
  - Uses `ScheduledExecutorService` for timing management
  - Implements exponential distribution with configurable median
  - Automatically reschedules mining operations after each block
  - Includes proper error handling and graceful shutdown
  - Enforces minimum delay (100ms) to prevent excessive CPU usage
  - Dynamically scales the median interval based on observed network difficulty
  - Builds and submits merged-mining Bitcoin blocks
  - Optional PoW nonce search; can be disabled via `miner.server.skipPowValidation`

### 2. Configuration Properties
- **Location**: `rskj/rskj-core/src/main/resources/reference.conf`
- **Properties**:
  - `miner.client.timedMine`: Boolean flag to enable/disable timed mining
  - `miner.client.medianBlockTime`: Duration specifying median time between blocks

- **Related Property**:
  - `miner.server.skipPowValidation`: When `true`, TimedMinerClient skips PoW validation (useful for dev/regtest)

### 3. Configuration Integration
- **Location**: `rskj/rskj-core/src/main/java/co/rsk/config/RskSystemProperties.java`
- **Methods used/config keys**:
  - `minerClientTimedMine()`
  - `minerClientMedianBlockTime()`
  - `minerServerSkipPowValidation()`

### 4. RskContext Integration
- **Location**: `rskj/rskj-core/src/main/java/co/rsk/RskContext.java`
- **Changes**: Updated `getMinerClient()` method to prioritize timed mining over automine
- **Priority Order**:
  1. Timed Mining (`timedMine = true`) - highest priority
  2. Auto Mining (`autoMine = true`) - medium priority
  3. Standard Mining (default) - lowest priority
 - **Additional**: Passes `miner.server.skipPowValidation` to `TimedMinerClient` constructor

### 5. Test Coverage
- **Location**: `rskj/rskj-core/src/test/java/co/rsk/mine/`
- **Test Files**:
  - `TimedMinerClientTest.java`: Basic functionality tests
  - `TimedMinerClientMiningTest.java`: Comprehensive mining tests
  - `TimedMinerClientSimpleTest.java`: Simple unit tests

### 6. Documentation and Examples
- **Location**: `rskj/rskj-core/src/main/resources/`
- **Files**:
  - `TIMED_MINING_README.md`: Comprehensive feature documentation
  - `config/timed-mining.conf`: Example configuration file
  - `IMPLEMENTATION_SUMMARY.md`: This summary document

## How It Works

### Exponential Distribution Implementation
```java
// Generate exponential-distributed delay; adapt median by observed difficulty
double scaledMedianMs = medianBlockTime.toMillis() * difficultyScale; // difficultyScale = current / baseline
double mean = scaledMedianMs / Math.log(2.0);
long delayMillis = (long) (-mean * Math.log(1.0 - random.nextDouble()));
delayMillis = Math.max(delayMillis, 100); // ensure a minimum delay
```

### Scheduling Mechanism
1. When `start()` is called, the first mining operation is scheduled
2. After each successful mining operation, the next one is automatically scheduled
3. Uses exponential distribution to determine the delay between operations
4. Adapts the median delay based on difficulty (`difficultyScale`)
5. Continues until `stop()` is called


## Configuration Example

```hocon
miner {
  server {
    enabled = true
    skipPowValidation = true  # enable for development/regtest to avoid CPU-intensive nonce search
  }

  client {
    enabled = true
    timedMine = true
    medianBlockTime = 10 seconds
  }
}
```

## Use Cases

1. **Development and Testing**: Create blocks at predictable intervals
2. **Simulation**: Simulate mining behavior with controlled timing
3. **Regtest Networks**: Maintain consistent block production
4. **Performance Testing**: Test block processing at various intervals


## Deploy on Boton

Use Boton to initialize a new instance, then follow these steps to deploy the node and run it in regtest with the mining simulation build.

1. Stop the running node on the instance:

```bash
sudo service rsk stop
```

2. Copy the fat JAR (the one ending in `-all.jar`, e.g., `rskj-core-8.1.0-SNAPSHOT-all.jar`) to the instance and replace the service JAR:

```bash
scp rskj-core-8.1.0-SNAPSHOT-all.jar ubuntu@<ip>:/usr/share/rsk/rsk.jar
```

3. Copy the Boton node configuration to the instance:

```bash
scp node-boton.conf ubuntu@<ip>:/etc/rsk/node.conf
```

4. On the instance, edit the service configuration at `/etc/sysconfig/rsk`:

```bash
sudo vim /etc/sysconfig/rsk
```

Set the following values:

```bash
JAVA_OPTS=-Xms3G -Xmx5G -Dlogback.configurationFile=/etc/rsk/logback.xml -Dkeyvalue.datasource=rocksdb
-Ddatabase.dir=/var/lib/rsk/database/regtest -Dlogging=DEBUG -Drsk.conf.file=/home/ubuntu/node.conf

RSKJ_CLASS=co.rsk.Start
RSKJ_OPTS=--regtest
```

Note: Ensure `-Drsk.conf.file` points to the actual location of your `node.conf` (e.g., `/etc/rsk/node.conf`).

5. Clean logs and database (optional but recommended for a clean start):

```bash
sudo rm -rf rsk.log /var/lib/rsk/database/regtest/
```

6. Start the node:

```bash
sudo service rsk start
```
