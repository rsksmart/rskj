# Experimental Features

Below you can find all the experimental features released with RSKj. The experimental features are not enabled by default, 
and you need to explicitly enable them in your configuration file. These are features that are still being tested and may not be fully stable, 
we decide to release them so the community can start to use it and provide feedback about it. So we can also validate, improve and iterate 
on them before they are released as stable features in the future.

You will also find the version and height in which they were introduced, there are instructions on how you can configure, customize
and start using them.

## Snapshot Sync

**TL;DR** Snapshot Sync is an experimental feature that allows RSKj nodes to synchronize using a
snapshot of the state of the blockchain, rather than downloading the whole blocks starting from
genesis. This can significantly reduce the time it takes to synchronize a node, especially for large
blockchains.

### When was it released?

Snapshot Sync was released in RSKj version LOVELL-7.1.0, if you are using a version higher than this, you can enable and use this feature.

Full Long Sync is the standard method for getting a new RSKj node to match up with the existing  blockchain network. The goal is to make sure 
the new node downloads and checks every transaction, contract, and block, starting from the very first one. This approach uses up quite a bit of time and
computer resources.

Snapshot Sync is designed to make the sync process faster but still safe: Instead of getting the full blockchain history, state data is downloaded up 
to a certain point where there's enough information to rebuild the whole state quickly.

The main goal of Snapshot Sync is to have a new RSKj node ready to use in a shorter time in comparison to using the default Full Long Sync method.

### How to Configure Snap-Capable Nodes

Nodes can be configured as Snap servers and/or clients by adding a new `snapshot` config block to the `sync` configurations.

#### Snap Server

A node can be configured as a Snap Server by adding the following to the `sync` configuration block:

```yaml
snapshot = {
	server = {
		enabled = <bool>
	}
}
```

#### Snap Client

Snap client configurations must also be added to the `sync` configuration block, they allows for many options to be tweaked:

```yaml
snapshot = {
	client = {
		enabled = <bool>
		checkHistoricalHeaders = <bool>
		parallel = <bool>
		chunkSize = <chunkSize>
		limit = <limit>
		snapBootNodes = [
			{
				nodeId = <id>
				ip = <ip>
				port = <port>
			}
		]
	}
}
```

#### Snap-Capable Bootstrap Nodes

To simplify testing of the experimental SnapSync feature introduced in RSKj LOVELL-7.1.0, Rootstock operates a set of publicly accessible
Snap-capable Boot Nodes. These can be plugged in `snapshot.client.snapBootNodes` configuration to kick off fast state synchronization.

The following are public Snap-capable Bootstrap Nodes provided by Rootstock:

_**Mainnet**_

- Bootstrap 1:
  - Host: `snapshot-sync-use1-1.mainnet.rskcomputing.net`
  - Node ID: `e3a25521354aa99424f5de89cdd2e36aa9b9a96d965d1f7f47d876be0cdbd29c7df327a74170f6a9ea44f54f6ab8ae0dae28e40bb89dbd572a617e2008cfc215`


- Bootstrap 2:
  - Host: `snapshot-sync-euw2-1.mainnet.rskcomputing.net`
  - Node ID: `f0093935353f94c723a9b67d143ad62464aaf3c959dc05a87f00b637f9c734513493d53f7223633514ea33f2a685878620f0d002cabc05d7f37e6c152774d5da`

_**Testnet**_

- Bootstrap 1:
  - Host: `snapshot-sync-euw1-1.testnet.rskcomputing.net`
  - Node ID: `137eb4328a7c2298e26dd15bba4796a7cc30b5097f8a14b384c8dc78caab49fac7a897c39a5a7e87838ac6dc1a80b94891d274a85ac76e7342d66e8a9ed26bf5`


- Bootstrap 2:
  - Host: `snapshot-sync-usw2-1.testnet.rskcomputing.net`
  - Node ID: `fcbfbfce93671320d32ab36ab04ae1564a31892cba219f0a489337aad105dcfc0ebe7d7c2b109d1f4462e8e80588d8ef639b6f321cc1a3f51ec072bed3438105`

**Mainnet Configuration Example**

```yaml
sync {
  enabled = true
  snapshot {
    client {
      enabled = true
      checkHistoricalHeaders = true
      parallel = true
      chunkSize = 50
      limit = 10000
      snapBootNodes = [
        {
          nodeId = "f0093935353f94c723a9b67d143ad62464aaf3c959dc05a87f00b637f9c734513493d53f7223633514ea33f2a685878620f0d002cabc05d7f37e6c152774d5da"
          ip     = "snapshot-sync-euw2-1.mainnet.rskcomputing.net"
          port   = 5050
        },
        {
          nodeId = "e3a25521354aa99424f5de89cdd2e36aa9b9a96d965d1f7f47d876be0cdbd29c7df327a74170f6a9ea44f54f6ab8ae0dae28e40bb89dbd572a617e2008cfc215"
          ip     = "snapshot-sync-use1-1.mainnet.rskcomputing.net"
          port   = 5050
        }
      ]
    }
  }
}
peer {
  # Boot node list
  # Use to connect to specific nodes
    discovery {
        enabled = false
    }
    active = [{
        url = "enode://e3a25521354aa99424f5de89cdd2e36aa9b9a96d965d1f7f47d876be0cdbd29c7df327a74170f6a9ea44f54f6ab8ae0dae28e40bb89dbd572a617e2008cfc215@snapshot-sync-use1-1.mainnet.rskcomputing.net:5050"
    },{
        url = "enode://f0093935353f94c723a9b67d143ad62464aaf3c959dc05a87f00b637f9c734513493d53f7223633514ea33f2a685878620f0d002cabc05d7f37e6c152774d5da@snapshot-sync-euw2-1.mainnet.rskcomputing.net:5050"
    }]
}
```

For `testnet`, use the port 50505 and the nodeId and ip shared above for `testnet`.

```yaml
sync {
  enabled = true
  snapshot {
    client {
      enabled = true
      checkHistoricalHeaders = true
      parallel = true
      chunkSize = 50
      limit = 10000
      snapBootNodes = [
        {
          nodeId = "f0093935353f94c723a9b67d143ad62464aaf3c959dc05a87f00b637f9c734513493d53f7223633514ea33f2a685878620f0d002cabc05d7f37e6c152774d5da"
          ip     = "snapshot-sync-euw2-1.mainnet.rskcomputing.net"
          port   = 5050
        },
        {
          nodeId = "e3a25521354aa99424f5de89cdd2e36aa9b9a96d965d1f7f47d876be0cdbd29c7df327a74170f6a9ea44f54f6ab8ae0dae28e40bb89dbd572a617e2008cfc215"
          ip     = "snapshot-sync-use1-1.mainnet.rskcomputing.net"
          port   = 5050
        }
      ]
    }
  }
}
peer {
  # Boot node list
  # Use to connect to specific nodes
    discovery {
        enabled = false
    }
    active = [{
        url = "enode://e3a25521354aa99424f5de89cdd2e36aa9b9a96d965d1f7f47d876be0cdbd29c7df327a74170f6a9ea44f54f6ab8ae0dae28e40bb89dbd572a617e2008cfc215@snapshot-sync-use1-1.mainnet.rskcomputing.net:5050"
    },{
        url = "enode://f0093935353f94c723a9b67d143ad62464aaf3c959dc05a87f00b637f9c734513493d53f7223633514ea33f2a685878620f0d002cabc05d7f37e6c152774d5da@snapshot-sync-euw2-1.mainnet.rskcomputing.net:5050"
    }]
}
```

#### ⚠️ Important Notes

- The minimal `sync` configuration for Snap Sync **MUST** include the `enabled` property set to `true` beside the node being configured as a Snap-Capable server or client, like this:

```yaml
sync {
	enabled = true
	<SNAP_SYNC_CONF>
}
```

- There's an extra option added to peer configuration section that can be used to further tweak the Snap Sync process:

```yaml
peer {
	...
	messageQueue {
		...
		# Reject peer's messages over this threshold
    # It's calculated using exponential moving average
		thresholdPerMinutePerPeer = <int>
	}
}
```

### Snap-Related RSKCli Tool Options

The `RskCli` tool accepts two options regarding Snap Sync:

`--sync-mode` can be used to set the synchronization mode. It accepts one string containing `full` or `snap` as values.

`--snap-nodes` can be used to set a list of Snap-Capable peers to connect to. It accepts a string list of nodes in the format `enode://PUBKEY@HOST:PORT`

### Monitoring

In order to know if the process is working, *snapshotprocessor* logs may be filtered out by executing `tail -f /path/to/rsk/logs/rsk.log | grep “snapshotprocessor”`, log messages will be different depending on the node being a Snap server or client.

#### Snap Server Logs

The first *snapshotprocessor* related log line will appear once a client start asking for Snap messages, and looks like this:

```
2025-04-01-01:22:00.195 DEBUG [snapshotprocessor]  Processing snapshot status request, checkpointBlockNumber: 220000, bestBlockNumber: 221583
```

And then, messages like these are going to be repeated until there's no more clients asking for messages:

```
2025-04-01-01:27:39.793 DEBUG [snapshotprocessor]  Processing state chunk request from node NodeID{9d8946b40c529eb77602704a63fe8c9b072e633f2b3025bfeb3593d691a2d797e4eb4a4cc3665dcb2ebc58a208e5c96b1370c05204d6e56ab96c4a99ddaee87b}. From 54425600 to calculated 54476800 being chunksize 50
2025-04-01-01:27:39.793 DEBUG [snapshotprocessor]  Sending state chunk from 54425600 to 54476800
2025-04-01-01:27:39.805 DEBUG [snapshotprocessor]  Sending state chunk from 54425600 of 56935 bytes to node NodeID{9d8946b40c529eb77602704a63fe8c9b072e633f2b3025bfeb3593d691a2d797e4eb4a4cc3665dcb2ebc58a208e5c96b1370c05204d6e56ab96c4a99ddaee87b}, totalTime 12ms
2025-04-01-01:27:39.823 DEBUG [snapshotprocessor]  Processing state chunk request from node NodeID{9d8946b40c529eb77602704a63fe8c9b072e633f2b3025bfeb3593d691a2d797e4eb4a4cc3665dcb2ebc58a208e5c96b1370c05204d6e56ab96c4a99ddaee87b}. From 54476800 to calculated 54528000 being chunksize 50
2025-04-01-01:27:39.823 DEBUG [snapshotprocessor]  Sending state chunk from 54476800 to 54528000
2025-04-01-01:27:39.844 DEBUG [snapshotprocessor]  Sending state chunk from 54476800 of 62776 bytes to node NodeID{9d8946b40c529eb77602704a63fe8c9b072e633f2b3025bfeb3593d691a2d797e4eb4a4cc3665dcb2ebc58a208e5c96b1370c05204d6e56ab96c4a99ddaee87b}, totalTime 21ms
2025-04-01-01:27:39.867 DEBUG [snapshotprocessor]  Processing state chunk request from node...
```

#### Snap Client Logs

In the Snap client side, the first *Snapshotprocessor* related line will look like this:

```
2025-04-01-01:22:00.179 INFO [snapshotprocessor]  Starting Snap sync
```

Followed by a stream of messages similar to these:

```
2025-04-01-01:22:02.341 DEBUG [snapshotprocessor]  Processing snap blocks response. Receiving from block 217600 to block 217999 Objective: 214000.
2025-04-01-01:22:02.584 DEBUG [snapshotprocessor]  SnapBlock - nexChunk : 217600 - lastRequired 214000, missing 3600
2025-04-01-01:22:02.585 DEBUG [snapshotprocessor]  Sending request: [SNAP_BLOCKS_REQUEST_MESSAGE] with id: [7] to: [NodeID{cb3db6d3187550a304b16329b9dfcf15abf0d5aa817d28ea0a0f4547b7eefdc1badb38e800db276ae2a6aa2bb81fbf4459606d10d3aad7be4934a748ecd9fae8}]
2025-04-01-01:22:02.711 DEBUG [snapshotprocessor]  Processing snap blocks response. Receiving from block 217200 to block 217599 Objective: 214000.
2025-04-01-01:22:02.915 DEBUG [snapshotprocessor]  SnapBlock - nexChunk : 217200 - lastRequired 214000, missing 3200
2025-04-01-01:22:02.915 DEBUG [snapshotprocessor]  Sending request: [SNAP_BLOCKS_REQUEST_MESSAGE] with id: [8] to: [NodeID{cb3db6d3187550a304b16329b9dfcf15abf0d5aa817d28ea0a0f4547b7eefdc1badb38e800db276ae2a6aa2bb81fbf4459606d10d3aad7be4934a748ecd9fae8}]
...
```

Until the whole process ends, and the following logs will look something like these:

```
2025-04-01-01:27:39.975 INFO [snapshotprocessor]  Recovering trie...
2025-04-01-01:27:41.879 INFO [snapshotprocessor]  State final validation OK!
2025-04-01-01:27:42.437 INFO [snapshotprocessor]  Setting last block as best block...
2025-04-01-01:27:42.437 INFO [snapshotprocessor]  Snap sync finished successfully
```

## Fiat Stable MinGasPrice

In the context of **RSKj** and **cryptocurrency**, **Fiat Stable MinGasPrice** usually refers to a way of **stabilizing the minimum gas price** based on the value of a **fiat currency** (like USD, EUR, etc.).

### When was it released?
Fiat Stable MinGasPrice was released in RSKj version LOVELL-7.1.0, if you are using a version higher than this, you can enable and use this feature.

**Important Definitions:**

- **Gas Price**: In RSKj, when you send a transaction or interact with a smart contract, you pay a *gas fee* to the network. The *gas price* is how much you pay per unit of gas.
- **Fiat Stable**: This suggests trying to **peg** (or stabilize) the *minimum gas price* to a **fixed value in fiat currency**, even though RSKj's currency (RBTC) is volatile.
- **MinGasPrice**: This would be the **minimum** allowed gas price — the lowest cost you can set for your transaction to be accepted or processed.

**How it works:**

**Fiat Stable MinGasPrice** means setting a minimum gas price that, no matter how much RBTC's price goes up or down, tries to represent a *fixed real-world value* (like "always costing about 0.01 USD worth of gas minimum").

### Why does it matter?

- If RBTC price suddenly spikes, gas fees could become ridiculously high in dollar terms.
- By using a **fiat-stable minimum**, systems can ensure fair and predictable pricing for users and maintain network security and validator incentives.

### Motivation and context for the changes

Currently, RSK miners can set the minimum gas price (MinGasPrice) using the miner.minGasPrice configuration, which is denominated in WEI and inherently linked to the Bitcoin price. This linkage causes transaction costs to fluctuate with Bitcoin's value, potentially leading to increased costs when Bitcoin appreciates, thereby adversely affecting user experience.

To address this issue, the proposed feature aims to empower miners with the ability to specify and configure the minimum gas price in fiat currency. This change would stabilize transaction costs in fiat terms, regardless of cryptocurrency market variations, and is intended to enhance predictability and user satisfaction. The system used to support only one configuration parameter for setting the MinGasPrice. See an example here:

```yaml
miner {
	# The default gas price
	minGasPrice = 60000000
	server.enabled = true
	
	client {
	    enabled = true
	    delayBetweenBlocks = 0 seconds
	    delayBetweenRefreshes = 1 seconds
	}
	
	coinbase.secret = regtest_miner_secret_please_change
	
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
	
	    minStableGasPrice = 476190000000 # 0.00000426528 USD per gas unit
	
	    # # The time the miner client will wait to refresh price from the source.
	    # # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
	    refreshRate = 10 minutes
	}
}
```

The goal is to give miners a capability to specify / configure min gas price value denominated in a fiat currency.

#### PR that added the changes

[https://github.com/rsksmart/rskj/pull/2310](https://github.com/rsksmart/rskj/pull/2310)

### 🔗 HTTP Call (Off-Chain Method)

This approach uses a **centralized or semi-centralized API** to fetch the current **RBTC-to-fiat price** and then calculate a gas price based on that.

#### How it works:

- The system (like a wallet, dApp backend, or node) sends an **HTTP request** to a price oracle or API like:
    - CoinGecko
    - Chainlink's external adapters
    - Custom backend service
- It retrieves the current **RBTC/USD price**, then calculates:
  `minGasPrice = fiatMinPrice / ethPrice`
- Example: If you want the gas price to represent $0.01, and RBTC = $2000, then:
  `minGasPrice = 0.01 / 2000 = 0.000005 RBTC (or 5 gwei)`

##### ✅ Pros:

- Simple, flexible, easy to integrate.
- Can pull from a wide range of data sources.

##### ❌ Cons:

- **Centralization risk** – you're trusting the HTTP source.
- **Not trustless** – the blockchain doesn't verify the result.
- **Latency and reliability** – dependent on network and endpoint availability.

### ⛓️ On-Chain Oracle Call (Decentralized/Trustless Method)

This approach fetches the **RBTC-to-fiat price from a smart contract**, often powered by a **decentralized oracle network** like **Chainlink**.

#### How it works:

- Smart contracts call an **on-chain oracle** (e.g., Chainlink RBTC/USD price feed).
- The gas price is derived using the oracle's latest reported price.
- This is done **inside smart contracts**, ensuring consistency and trust.

##### ✅ Pros:

- **Trustless and secure** – all nodes agree on the same value.
- **Tamper-resistant** – oracle data is resistant to manipulation.
- **Fully on-chain** – compatible with DeFi, DAO governance, etc.

##### ❌ Cons:

- **Gas cost** – reading from an oracle contract uses gas.
- **Oracle update frequency** – price may not always be up to the second.
- **Complexity** – needs integration with oracle networks and possibly fallback logic.

### Summary Table:

| Method | Trust Level | Cost | Speed | Use Case |
| --- | --- | --- | --- | --- |
| HTTP API (Off-chain) | Centralized | Low | Fast | Backend services, UI, analytics |
| On-chain Oracle | Decentralized | Moderate | Near real-time | Smart contracts, DeFi, DAOs |

### RSK Configuration

Miner configurations depends on the desired method used to request the min gas price. An [example miner configuration can be seen here](https://github.com/rsksmart/rskj/pull/2310/files#diff-242a8c6b5d9003504710db696af1f94412a52cdbd3369df55cff4be2ea8697eaR204-R234)

**HTTP CALL**

Configurations for using HTTP to request the min gas price

```
miner {
    stableGasPrice {
        enabled = false

        source {
	        # method options: HTTP_GET | ETH_CALL
            # Examples:
            method = "HTTP_GET"
            params {
                url = "https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd"
                # response: {"ethereum":{"usd":1848.79}}
                jsonPath = "ethereum/usd"
                timeout = 2 seconds # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
            }
        }
				
        minStableGasPrice = 4265280000000 # 0.00000426528 USD per gas unit
        # The time the miner client will wait to refresh price from the source.
        # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
        refreshRate = 6 hours
    }
}
```

Parameters for the `source`:
* `method`: will choose the way of getting the min gas price, in this case through HTTP CALL.
* `url`: is the HTTP url that will be used to get the min gas price.
* `jsonPath`: assuming the response type we get from the API is JSON, this is the path to extract the information from the JSON response.
* `timeout`: wait time to get the response. If the response takes longer than the amount set, an exception will be thrown.


**ETH CALL**

Configurations for using ETH CALL to request the min gas price

```
miner {
    stableGasPrice {
        enabled = false

        source {
             method = "ETH_CALL"
             params {
                from: "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd825",
                to: "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826",
                data: "0x8300df49"
             }
        }

        minStableGasPrice = 4265280000000 # 0.00000426528 USD per gas unit
        # The time the miner client will wait to refresh price from the source.
        # Value's type is `Duration`, e.g. 1.1 seconds, 2.5 minutes, 3 hours, 4 days.
        refreshRate = 6 hours
    }
}
```

Parameters for the `source`:

* `method`: will choose the way of getting the min gas price, in this case through ETH CALL, using the smart contracts.
* `from`: technically not required by the EVM to read state, but it's needed for:
    * **Contextual computation**: Some smart contracts behave differently based on the caller (`msg.sender`)
    * **Simulating gas costs**: `eth_call` can simulate how much gas a given sender would need.
    * **Fallback behavior**: Some contracts restrict who can call them.
* `to`: is required because you're calling a specific smart contract. Without to, RSKj wouldn’t know which contract to interact with.
* `data`: this is the function that we want to call from the contract.
