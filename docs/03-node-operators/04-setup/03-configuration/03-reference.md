# Rootstock Node Configuration Reference
See [CLI flags](/node-operators/setup/configuration/cli/) for command line flag options.

:::info[Important Notice]
From [RSKj HOP v4.2.0](https://github.com/rsksmart/rskj/releases/), RocksDB is no longer experimental. See guide on [using RocksDB](/node-operators/merged-mining/configure-mining#using-rocksdb)
:::

## Advanced Configuration

For advanced configuration requirements, please refer to this
[expected configuration file](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/resources/expected.conf).
This contains all possible configuration fields parsed by RSKj.

The default values for the config file are defined in this
[reference config file](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/resources/reference.conf);
and are "inherited" and varied based on the
[selected network](https://github.com/rsksmart/rskj/tree/master/rskj-core/src/main/resources/config).

## Guide

The following detail the most commonly used configuration fields parsed by RSKj.

- [`peer`](#peer)
- [`database`](#database)
- [`database.import`](#databaseimport)
- [`vm`](#vm)
- [`sync`](#sync)
- [`rpc`](#rpc)
- [`wallet`](#wallet)
- [`scoring`](#scoring)
- [`miner`](#miner)
- [`miner.stableGasPrice`](#minerstablegasprice)
- [`blockchain.config.name`](#blockchainconfigname)
- [`bind_address`](#bind_address)
- [`public.ip`](#publicip)
- [`genesis`](#genesis)
- [`transaction.outdated`](#transactionoutdated)
- [`transaction.gasPriceCalculatorType`](#transactiongaspricecalculatortype)
- [`play.vm`](#playvm)
- [`hello.phrase`](#hellophrase)
- [`details.inmemory.storage.limit`](#detailsinmemorystoragelimit)
- [`solc.path`](#solcpath)

## peer

Describes how your node peers with other nodes.

* `peer.discovery.enabled = [true/false]`
  enables the possibility of being discovered by new peers.
* `peer.discovery.ip.list = []`
  is a list of the peers to start the discovering.
  These peers must have the `discovery.enabled` option set to true.
  These are the list of some of RSK bootstrap nodes:

  |  | `ip.list` |
  | - | -
  | Regtest | Not applicable |
  | Testnet |`"bootstrap02.testnet.rsk.co:50505","bootstrap03.testnet.rsk.co:50505","bootstrap04.testnet.rsk.co:50505","bootstrap05.testnet.rsk.co:50505"` |
  | Mainnet | `"bootstrap01.rsk.co:5050","bootstrap02.rsk.co:5050","bootstrap03.rsk.co:5050","bootstrap04.rsk.co:5050","bootstrap05.rsk.co:5050","bootstrap06.rsk.co:5050","bootstrap07.rsk.co:5050","bootstrap08.rsk.co:5050","bootstrap09.rsk.co:5050","bootstrap10.rsk.co:5050","bootstrap11.rsk.co:5050","bootstrap12.rsk.co:5050","bootstrap13.rsk.co:5050","bootstrap14.rsk.co:5050","bootstrap15.rsk.co:5050","bootstrap16.rsk.co:5050"` |
* `peer.active = []`
  is used to connect to specific nodes.
  It can be empty.
* `peer.trusted = []`
  is a list of peers whose incoming connections are always accepted from.
  It can be empty.
* `peer.port` is the port used to listen to incoming connections.
  Default port by each RSK network:

  |  | `peer.port` |
  | - | - |
  | Regtest | 50501 |
  | Testnet | 50505 |
  | Mainnet | 5050 |
* `peer.connection.timeout = number (seconds)`
  specifies *in seconds* the timeout for trying to connect to a peer.
  Suggested value: `2`.
* `channel.read.timeout = number (seconds)`
  specifies *in seconds* how much time you will wait for a message to come before closing the channel.
  Suggested value: `30`.
- `peer.privateKey = hash`
  is a securely generated private key unique for your node that **must** be set.
* `peer.networkId = int`
  is the number of the network to connect to.
  It's important to maintain these numbers.
  It identifies the network you are going to connect to.
  For a Regtest private network, you should always use the same id.
  RSK networks IDs:

  |  | `peer.networkId` |
  | - | - |
  | Regtest | 7771 |
  | Testnet | 8100 |
  | Mainnet | 775 |
* `peer.maxActivePeers = int`
  is the max number of active peers that your node will maintain.
  Suggested value: `30`.
* `peer.p2p.eip8 = bool`
  forces peer to send handshake message in format defined by
  [EIP-8](https://github.com/ethereum/EIPs/blob/master/EIPS/eip-8.md).

## database

Describes where the blockchain database is saved.

* `database.dir = path`
  is the place to save physical storage files.
* `database.reset = [true/false]`
  resets the database when the application starts when set to *true*.

## database.import

Options related to experimental import sync v0.1.

* `database.import.url = URL`
  is the URL to the S3 bucket that hosts the database.
* `database.import.trusted-keys = []`
  list of trusted public keys to validate legit source.
* `database.import.enabled = [true/false]`
  enable the import sync.

## keyvalue.datasource (experimental)

Selects the database that will be used to store the information.
Possible options are:

* `leveldb`
* `rocksdb` (default)

If you wish to switch between the different storage options,
for example from `leveldb` to `rocksdb` or vice versa,
you must **restart** the node with the import option each time you do so.

## vm

Enabling the `vm.structured` will log all the calls to the VM in the local database.
This includes all the contract executions (opcodes).
When testing, using this module is the only way to see exceptions.

* `trace = [true/false]`
  enables the `vm.structured`.
* `dir = foldername`
  the directory where calls are saved.
* `compressed = [true/false]`
  compress data when enabled.
* `initStorageLimit = int`
  the storage limit.

An example:

```
vm.structured {
    trace = false
    dir = vmtrace
    compressed = true
    initStorageLimit = 10000
}
```

## sync

Options related to syncing the blockchain:

* `sync.enabled = [true/false]`
  enables the blockchain synchronization.
  Should be set to `true` to keep the node updated.
* `sync.max.hashes.ask = int`
  determines the max amount of blocks to sync in a same batch.
  The synchronization is done in batches of blocks.
  Suggested value: `10000`.
* `sync.peer.count = int`
  minimum peers count used in syncing.
  Sync may use more peers than this value but always trying to get at least this number from discovery.
  However, it will continue syncing if it's not reached.
* `sync.timeoutWaitingPeers = int (seconds)`
  timeout to start to wait for syncing requests.
* `sync.timeoutWaitingRequests = int (seconds)`
  expiration time in minutes for peer status.
* `sync.maxSkeletonChunks = int`
  maximum amount of chunks included in a skeleton message.
* `sync.chunkSize = int`
  amount of blocks contained in a chunk,
  **must be** 192 or a divisor of 192:
  * 1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 192

An example:

```
sync {
    enabled = true
    max.hashes.ask = 10000
    peer.count = 10
    expectedPeers = 5
    timeoutWaitingPeers = 1
    timeoutWaitingRequest = 30
    expirationTimePeerStatus = 10
    maxSkeletonChunks = 20
    chunkSize = 192
}
```

## rpc

Describes the configuration for the RPC protocol.

* `rpc.providers = []`
  lists the different providers (up to this moment, just `web`).
  `rpc.providers.web` has a global setting for every web provider,
  `cors`.
  * `rpc.providers.web.cors = domain`
    restricts the RPC calls from other domains.
    Default value is `localhost`.
* `rpc.providers.web` options:
  * `rpc.providers.web.http`
    defines HTTP configuration:
    * `rpc.providers.web.http.enabled = [true/false]`
      enables this web provider. Default value is `true`.
    * `rpc.providers.web.http.port = port`
      is the HTTP-RPC server listening port.
      By default RSK uses `4444`.
    * `rpc.providers.web.http.bind_address = address`
      is the HTTP-RPC server listening interface.
      By default RSK uses `localhost`.
    * `rpc.providers.web.http.hosts = []`
      is the list of node's domain names or IPs.
      Check [restrictions on valid host names](https://en.wikipedia.org/wiki/Hostname#Restrictions_on_valid_host_names).
    * **NOTE**: For details on how to connect over HTTP,
      see [HTTP Transport Protocol](/node-operators/json-rpc/transport-protocols#http-transport-protocol "Rootstock JSON-RPC - HTTP").
  * `rpc.providers.web.ws`
    defines WebSocket configuration:
    * `rpc.providers.web.ws.enabled = [true/false]`
      enables this web provider.
      Default value is `true`.
    * `rpc.providers.web.ws.port = port`
      is the WS-RPC server listening port.
      By default RSK uses `4445`.
    * `rpc.providers.web.ws.bind_address = address`
      is the WS-RPC server listening interface.
      By default RSK uses `localhost`.
    * **NOTE**: For details on how to connect over WebSockets,
      see [Websockets Transport Protocol](/node-operators/json-rpc/transport-protocols#websockets-transport-protocol "Rootstock JSON-RPC - WebSockets").
* `rpc.modules` lists of different RPC modules.
  If a module is not in the list and enabled,
  its RPC calls are discarded.

Examples:

```json
modules = [
    { name: "eth", version: "1.0", enabled: "true" },
    { name: "net", version: "1.0", enabled: "true" },
    { name: "rpc", version: "1.0", enabled: "true" },
    { name: "web3", version: "1.0", enabled: "true" },
    { name: "evm", version: "1.0", enabled: "true" },
    { name: "sco", version: "1.0", enabled: "true" },
    { name: "txpool", version: "1.0", enabled: "true" },
    { name: "personal", version: "1.0", enabled: "true" }
]
```

It is possible to enable/ disable particular methods in a module.

```shell
{
    name: "evm",
    version: "1.0",
    enabled: "true",
    methods: {
        enabled: ["evm_snapshot", "evm_revert"],
        disabled: [ "evm_reset", "evm_increaseTime"]
    }
}
```

To use the [RPC miner module](/node-operators/json-rpc/methods/)
you must include:

```
{ name: "mnr", version: "1.0", enabled: "true" }
```

> Important Info:
  - RPC methods for each module can be found in the [JSON-RPC documentation](/node-operators/json-rpc/).
  - See the [JSON-RPC Configurable limits](/node-operators/json-rpc/configuration-limits/)

## wallet

You can store your accounts on the node to use them to sign transactions. However, it is **not secure** to use a wallet in a public node.

```shell
wallet {
    enabled = true
    accounts = [
        {
            "publicKey" : "<PUBLIC_KEY>"
            "privateKey" : "<PRIVATE_KEY>"
        }
    ]
}
```

## scoring

Scoring is the way the node 'punishes' other nodes when bad responses are sent. Punishment can be done by node ID or address.

* `scoring.nodes.number = int`
  number of nodes to keep scoring.
* `scoring.nodes.duration` and `scoring.addresses.duration`
  initial punishment duration (in minutes).
  Default is `10`.
* `scoring.nodes.increment` and `scoring.addresses.increment`
  punishment duration increment (in percentage).
  Default is `10`.
* `scoring.nodes.maximum`
  maximum punishment duration (in minutes).
  Default is `0`.
* `scoring.addresses.maximum`
  maximum punishment duration (in minutes).
  Default is `1 week`.

An example:

```shell
scoring {
    nodes {
        number: 100
        duration: 12
        increment: 10
        maximum: 0
    }
    addresses {
        duration: 12
        increment: 10
        maximum: 6000
    }
}
```

## miner

Check out [Configure RSKj node for mining](/node-operators/merged-mining/configure-mining)
for detailed information about the `miner` configuration.

## miner.stableGasPrice

The `stableGasPrice` feature allows miners to set a predictable gas price for transactions, establishing a minimum rate that is less affected by market volatility and better aligned with fiat currency values like USD or EUR. This enhances cost management and improves overall transaction efficiency.

The source configuration supports two methods: `ETH_CALL` and `HTTP_GET`.

- **ETH_CALL**: This method retrieves the gas price directly from a specified smart contract, allowing for real-time adjustments based on the contract's logic.
- **HTTP_GET**: This method fetches gas price data from an external API, providing flexibility to pull market data from trusted sources.

**Example of ETH_CALL**
```plaintext
stableGasPrice {
    enabled = true

    source {
        method = "ETH_CALL"
        params {
            from = "0x0000000000000000000000000000000000000000"
            to = "0xCA41630b09048b6f576459C34341cAB8Dc2cBE7E"
            data = "0xdf16e52d"
        }
    }

    minStableGasPrice = 476190000000 # Minimum gas price
    refreshRate = 6 hours          # How often to check for updates
}
```

- **Source**: Retrieves the gas price from a specific smart contract
- **Minimum Gas Price**: Ensures the miner uses a baseline price to avoid setting it too low
- **Refresh Rate**: Updates the gas price

**Example of HTTP_GET**
```plaintext
stableGasPrice {
    enabled = true

    source {
        method = "HTTP_GET"
        params {
            url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
            jsonPath = "/bitcoin/usd"
            timeout = 5 seconds
        }
    }

    minStableGasPrice = 476190000000 # Minimum gas price
    refreshRate = 6 hours          # How often to check for updates
}
```

## blockchain.config.name

A string that describes the name of the configuration.
We use:

|  | `blockchain.config.name` |
| - | - |
| Regtest | regtest |
| Testnet | testnet |
| Mainnet | main |

## bind_address

The network interface with *wire protocol* and *peer discovery*.

This is the last resort for public IP address when it cannot be obtained by other ways.

## public.ip

The node's public IP address.

If it is not configured, defaults to the IPv4 returned
by `http://checkip.amazonaws.com`.

## genesis

It is the path to the genesis file.
The folder *resources/genesis* contains several versions of genesis
configuration according to the network the peer will run on. Either one of these,
or an absolute path to a file within the filesystem may be used.

|  | `genesis` |
| - | - |
| Regtest | rsk-dev.json |
| Testnet | orchid-testnet.json |
| Mainnet | rsk-mainnet.json |
| Custom | /home/username/rskj_config/genesis.json |

## transaction.outdated

It defines when a transaction is outdated:

* `transaction.outdated.threshold = int`
  is the number of blocks that should pass before pending transaction is removed.
  Suggested value: `10`.
* `transaction.outdated.timeout = int`
  is the number of seconds that should pass before pending transaction is removed.
  Suggested value: `100` (10 blocks * 10 seconds per block).

## transaction.gasPriceCalculatorType

It defines the type of gas price calculator being used.

Possible Values: `WEIGHTED_PERCENTILE`, `PLAIN_PERCENTILE` (default)

Setting this value to `WEIGHTED_PERCENTILE` allows for the gas used by the tx to
be taken into account.

## play.vm

A boolean that invokes VM program on message received.
If the VM is not invoked, the balance transfer occurs anyway.
Suggested value: `true`.

## hello.phrase

A string that will be included in the hello message of the peer.

## details.inmemory.storage.limit

Specifies when exactly to switch managing storage of the account
on autonomous DB.
Suggested value: `1`.

If the in-memory storage of the contract exceeds the limit,
only deltas are saved.

## solc.path

The path to the [Solidity](http://solidity.readthedocs.io) compiler.
This is set when you may want to use the node to compile your smart contracts.
If you don't have Solidity installed, you can use `/bin/false` as value.
