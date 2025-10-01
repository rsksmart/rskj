# Peer Scoring System
The main objective of the **Peer Scoring System** is to protect the [Rootstock node's](/node-operators/setup/) resources from abusive or malicious peers.
For this, the Rootstock's node keeps track of
a reputation for each peer that connects to it
and disconnects those whose current reputation
falls below acceptable levels.

## Actions

There are three possible actions that the Peer Scoring system can perform:

- Record an event
- Automatic penalisation of peer nodes
- Manually ban and unban peer nodes

### Recording an event

All event-firing scenarios will start by the node receiving a message from a peer.

All messages are automatically recorded as events.
However, for the current version,
only some of them have a negative impact
on the peer’s reputation.
These events are:

- **`INVALID_NETWORK`**: This occurs when the received network ID is different from the network ID of the node.
- **`INVALID_BLOCK`**: This happens when the received block is not valid as per RSK block validation requirements.
- **`INVALID_MESSAGE`**: This happens when the message received is not valid as per RSK message validation requirements.

If one of these events is recorded,
the peer’s reputation will be marked accordingly,
and the penalisation process will start automatically.
This occurs only in nodes where the peer scoring feature is enabled.

### Automatic Penalisation of Peer Nodes

A peer with a bad reputation will be punished,
meaning that when the node receives a message from it,
the peer will be automatically disconnected
and the received message will be discarded.
Penalties are applied at two levels:

- The peer’s `address`, and
- the peer’s `nodeID`.

The default duration for the first punishment is 10 minutes.
This can be changed via the RSK node configuration:

- `scoring.addresses.duration` for `address` level
- `scoring.nodes.duration` for `nodeID` level

These values are specified in minutes.

It is worth mentioning that penalty duration
will be incremented by a percentage value
each time a penalty is applied to a peer.
The default increase rate is 10%.
This can be changed via the RSK node configuration:

- `scoring.addresses.increment` for `address` level
- `scoring.nodes.increment` for `nodeID` level

It is possible to define a maximum time for a node to stay in a penalised state,
which defaults to 7 days for `address` level and 0 (unlimited) for `nodeID` level.
This can be changed via the RSK node configuration:

- `scoring.addresses.maximum` for `address` level
- `scoring.nodes.maximum` for `nodeID` level

These values are specified in minutes.

### Manual Banning of Peer Nodes

A banned peer is considered as a peer with a bad reputation.
Therefore, it will be disconnected the next time a message is received,
and its messages will be discarded.
However, the action of banning a peer,
unlike the Rootstock nodes’s automatic reputation tracking,
is a manual action.

In order to manually ban or unban a peer, this can be done by address,
the following [RPC methods](/node-operators/json-rpc/methods/) should be used:

- `sco_banAddress(String address)`: 
    - Removes an address or block to the list of banned addresses.
- `sco_unbanAddress(String address)`: 
    - Removes an address or block of addresses from the list of banned addresses.

To check what addresses are banned, use the following method:

- `sco_bannedAddresses()`: 
    - Returns the list of banned addresses and blocks

> Warning: These methods should be used with caution.

### Feature config

This can be changed via the RSK node configuration:

- `scoring.punishmentEnabled`

This value is specified as a boolean.

> Warning: The recommendation is to keep the defaults, this is a complex and powerful feature that should be used with caution.

### RPC methods

The following related RPC methods are available.
- `sco_banAddress(String address)`: see [Manually banning of peer nodes](#manual-banning-of-peer-nodes).
- `sco_unbanAddress(String address)`: see [Manually banning of peer nodes](#manual-banning-of-peer-nodes).
- `sco_bannedAddresses()`: see [Manually banning of peer nodes](#manual-banning-of-peer-nodes).
- `sco_peerList()`: Returns the collected peer scoring information
- `sco_reputationSummary()`: Returns the reputation summary of all the peers connected
- `sco_clearPeerScoring(String id)`: Clears scoring for the received id (firstly tried as an address, used as a nodeID otherwise).

**Please note the default and recommended config is to NOT expose these methods publicly**.
