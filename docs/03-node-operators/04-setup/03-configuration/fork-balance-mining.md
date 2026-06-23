# Fork-balance mining (header v3 / RSKIP555)

After **RSKIP555** is active, merge-mined blocks may use **header version 3** with a `forkBalanceProof` in the header extension. Mining pools must supply parent Bitcoin block data when building work; validating nodes do not need Bitcoin RPC.

## Who needs what

| Node role | `miner.server.enabled` | `miner.forkBalance.btcRpc.url` |
| --- | --- | --- |
| Validating / full node | `false` (default) | Leave empty (default) |
| Merge-mining pool | `true` | **Required** — pool `bitcoind` JSON-RPC URL |

RSKj refuses to start the miner on mainnet/testnet when RSKIP555 is active at the next block height and no Bitcoin RPC URL is configured (regtest is exempt; it seeds BTC parents locally).

## Bitcoin Core (`bitcoind`) setup

1. Run a **Bitcoin Core** instance on the same network as merge-mining (mainnet or testnet), reachable from the RSKj host.
2. Enable JSON-RPC in `bitcoin.conf` (mainnet example below; testnet typically uses port **18332**):

```ini
server=1
rpcbind=127.0.0.1
rpcallowip=127.0.0.1
rpcuser=<user>
rpcpassword=<password>
```

Testnet RPC URL example: `http://127.0.0.1:18332/`

3. If RPC is authenticated, include credentials in the URL:

```text
http://<rpcuser>:<rpcpassword>@127.0.0.1:8332/
```

4. Ensure the node can serve:
   - `getbestblockhash` — current chain tip for work-build
   - `getblockheader` — parent height for FAC cache
   - `getblock` (verbosity `0`) — full parent block bytes for fork-balance proofs

RSKj uses these calls only when the local FAC cache does not already hold the parent block. A minimum delay (`requestDelayMs`, default 500 ms) is enforced between consecutive RPC requests.

## RSKj configuration

Add to your network config file (see commented templates in `config/main.conf` and `config/testnet.conf`):

```hocon
miner {
    server.enabled = true
    forkBalance {
        btcRpc {
            url = "http://127.0.0.1:8332"
            requestDelayMs = 500
            timeout = 10 seconds
        }
        btcBlockCache {
            maxHeights = 12
        }
        facCache {
            delayParameterSeconds = 60
        }
    }
}
```

| Setting | Purpose |
| --- | --- |
| `btcRpc.url` | Bitcoin Core JSON-RPC endpoint (empty disables RPC; appropriate for validators) |
| `btcRpc.requestDelayMs` | Minimum spacing between RPC calls |
| `btcRpc.timeout` | HTTP connect/read timeout per request |
| `btcBlockCache.maxHeights` | How many distinct BTC heights to retain for parent lookup |
| `facCache.delayParameterSeconds` | Extra retention beyond the RSKIP-179 window when evicting FAC rows |

Defaults and full field list: [`reference.conf`](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/resources/reference.conf) and [`expected.conf`](https://github.com/rsksmart/rskj/blob/master/rskj-core/src/main/resources/expected.conf).

## Operational notes

- Point `btcRpc.url` at the **same** `bitcoind` your pool uses for merge-mining work (the BTC chain tip must match what miners extend).
- After a successful submit, RSKj caches the merge-mined BTC block locally; subsequent work can use the cache without RPC until the tip advances.
- If RPC is unreachable, work-build falls back to the highest complete block in the local cache; startup still requires a configured URL for non-regtest miners when header v3 is active.
- **Regtest** does not require `bitcoind`; RSKj chains synthetic BTC blocks internally.
- Parent Bitcoin blocks used for fork-balance proofs must contain **at least two transactions** (coinbase plus at least one other). Single-transaction BTC blocks produce an empty coinbase Merkle proof and cannot satisfy fork-balance validation.

## JSON-RPC `forkSafe` (FAC fork-safe head)

After RSKIP555, JSON-RPC supports an RSK-specific **FAC fork-safe** head distinct from Ethereum’s `safe` tag (which maps to the canonical best block).

| Tag / parameter | Meaning |
| --- | --- |
| `"safe"` | Canonical chain tip (Ethereum-compatible) |
| `"forkSafe"` | FAC `lastSafeBlock` relative to the query anchor (usually the chain tip) |
| `forkSafe: true` on block methods | Same FAC resolution for the given block hash or number |
| `requireForkSafe: true` (EIP-1898 object) | FAC resolution for the referenced block |

Supported on `eth_blockNumber`, `eth_getBlockByNumber`, `eth_getBlockByHash`, `eth_call`, `eth_getBalance`, `eth_getCode`, `eth_getStorageAt`, `eth_getTransactionCount`, and `rsk_getStorageBytesAt`.

**Fallback when FAC metadata is missing** (e.g. immediately after startup before the FAC tracker has backfilled):

| Method | Behavior without `lastSafeBlock` |
| --- | --- |
| `eth_blockNumber(forkSafe)` | Returns the **best block number** |
| `eth_getBlockByNumber` / `eth_getBlockByHash` with `forkSafe` | Returns the **anchor block** (tip or requested block) |
| State queries with `"forkSafe"` / `requireForkSafe` | Use state at the anchor block number |

## Related configuration

- General miner options: [Configuration reference — miner](./03-reference.md#miner)
- RSKIP555 activation: `blockchain.config.consensusRules.rskip555`
