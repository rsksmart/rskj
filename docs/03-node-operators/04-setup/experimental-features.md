Below you can find all the experimental features released with RSKj, which
version and height is the feature enabled, how can you enable it and the benefit
of the feature.

## Snapshot Synchronisation Feature
The Snapshot Synchronization feature is an experimental feature that allows RSK nodes to synchronize 
using a snapshot of the state of the blockchain, rather than downloading all blocks from the genesis block. 
This can significantly reduce the time it takes to synchronize a node, especially for large blockchains.

### How to Configure Snap-Capable Nodes
Nodes can be configured as Snap servers and/or clients by adding a new `snapshot` config block to the `sync` configurations.

#### Snap Server
A node can be configured as a Snap Server by simply adding the following to the `sync` configuration block:

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