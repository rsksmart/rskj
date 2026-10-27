# RSKj threat model (for the OSS Scanner)

RSKj is the reference node implementation of Rootstock, a Bitcoin merge-mined sidechain with an
EVM-compatible execution layer. It is consensus-critical software: every node runs the same code
and must agree, block for block, on the same state. It also holds the Rootstock side of the
two-way peg to Bitcoin.

Report vulnerabilities through the process in [SECURITY.md](../SECURITY.md)
(<security@rootstocklabs.com>, and the Immunefi bug bounty).

## What an attacker gets out of RSKj

Ranked by what we actually worry about.

1. **Stealing pegged bitcoin.** The PowPeg Bridge (`co.rsk.peg`) is a precompiled contract that
   custodies the BTC backing RBTC. A bug that releases BTC to an attacker, credits RBTC without a
   matching peg-in, replays a peg-in, or corrupts federation/UTXO state is the worst outcome in
   this codebase.
2. **Consensus divergence.** Two honest nodes on the same chain reaching different state roots
   splits the network. This covers the VM (`org.ethereum.vm`), gas accounting
   (`org.ethereum.vm.GasCost`), the trie (`co.rsk.trie`), block/transaction validation
   (`co.rsk.validators`), activation-height gating (`org.ethereum.config.blockchain.upgrades.ActivationConfig`
   and the RSKIP flags), and
   anything whose result depends on map iteration order, locale, platform or wall-clock time.
3. **Attacks on merge mining.** `co.rsk.mine` builds blocks and validates the Bitcoin merged-mining
   proof. Forging a proof, or stalling `MinerServer` / `BlockToMineBuilder` so a node stops
   producing or accepting blocks, halts the chain.
4. **Remote denial of service.** The P2P layer (`co.rsk.net`, including `SnapshotProcessor` and
   `SyncProcessor`), the transaction pool and the JSON-RPC surface (`co.rsk.rpc.modules`) all parse
   attacker-controlled bytes. Unbounded allocation, quadratic parsing, uncaught exceptions that
   kill a thread, or an OOM reachable from a single peer or a single unauthenticated RPC call all
   matter here.
5. **Node-local compromise.** Path traversal, deserialization of untrusted data, command injection,
   and key material (`co.rsk.crypto`, `GenNodeKeyId`) leaking into logs or onto disk.

## Where untrusted input enters

* **Bitcoin data via the Bridge** — peg-in/peg-out transactions, BTC block headers and SPV proofs
  submitted by anyone through Bridge methods. Treat every argument of every method in
  `co.rsk.peg.BridgeMethods` as attacker-chosen.
* **The P2P protocol** — blocks, headers, transactions, status messages, snapshot-sync chunks, and
  the node-discovery packets decoded in `co.rsk.net.discovery`. Any connected peer is untrusted.
* **Transactions and contract bytecode** — anything the VM executes, including the precompiles in
  `org.ethereum.vm.PrecompiledContracts` and `co.rsk.pcc`.
* **JSON-RPC and the Web3 surface** — `co.rsk.rpc.modules`. Operators do expose this; assume a
  caller with no credentials. `eth_call` / `debug_*` / `trace_*` accept caller-chosen gas and
  depth, so resource exhaustion is in scope.
* **Configuration and CLI** — `co.rsk.config`, `co.rsk.cli`. Lower priority: an operator editing
  their own config is not an attacker.

## Severity, as we rate it

Rootstock's published scale is the Immunefi one in the
[bug bounty policy](https://immunefi.com/bug-bounty/rootstocklabs/information); that document, not
this file, is authoritative. As a working guide for this scanner:

* **Critical** — direct theft or permanent freezing of pegged BTC/RBTC; forging a merge-mining
  proof; a network-wide consensus split; remote code execution on a node.
* **High** — a consensus divergence reachable only under specific conditions; a remote crash or
  memory exhaustion triggerable by one peer or one unauthenticated RPC call; bypassing a Bridge
  authorization check (federation change, fee-per-kb, lock whitelisting) without a fund impact.
* **Medium** — a resource-exhaustion path that needs sustained effort or many connections; a
  validation gap with no demonstrated state impact; information disclosure of operator data.
* **Low / informational** — issues reachable only by the node operator or by already-trusted
  input, and hardening suggestions.

Two things we weigh heavily when reading a report:

* **Reachability beats pattern.** A dangerous-looking call that no untrusted input can reach is
  informational. Please trace the path from one of the entry points above and say so explicitly.
* **Activation heights matter.** Behaviour in this codebase is gated by RSKIP activation flags
  (`org.ethereum.config.blockchain.upgrades.ActivationConfig`). Changing behaviour *before* its
  activation height is a
  consensus bug even when the new behaviour is correct. Say which network and which activation
  state a finding applies to.

## Out of scope

* Anything under `src/test`, `src/integrationTest` or `src/fuzzTest` — test code is not shipped.
* `co.rsk.cli.tools` utilities run deliberately by an operator on their own node.
* Third-party dependency CVEs with no demonstrated path from an RSKj entry point. We track those
  separately through Dependabot and `dependency-review.yml`.
* Denial of service that needs privileged local access or control of the node's own config.
* Missing rate limits on an RPC endpoint an operator chose to expose to the public Internet,
  unless a single call is disproportionately expensive.

## Working in this image

The checkout is at `/src`, already built, with the Gradle cache warm. There is **no network**:
pass `--offline` to every Gradle command. The same notes are at
`/src/.oss-scanner/AGENT-NOTES.md`.

```
./gradlew --offline --no-daemon test --tests 'co.rsk.peg.BridgeSupportTest'   # unit tests
./gradlew --offline --no-daemon assemble                                      # rebuild
```

A standalone regtest node needs no peers, mines on its own, and serves JSON-RPC on
`127.0.0.1:4444` — usually the quickest way to reproduce something end to end:

```
java -Dlogging.stdout=INFO -Dpublic.ip=127.0.0.1 \
     -cp rskj-core/build/libs/rskj-core-*-all.jar co.rsk.Start --regtest
```

Keep `-Dpublic.ip`. Without it the node queries `checkip.amazonaws.com` for its public IP, which
offline stalls on DNS and logs an ERROR stack trace; it recovers by falling back to `127.0.0.1`,
so that error is noise rather than a fault worth reporting.

The audit VM has 2 CPUs and 8 GB, and the `test` task asks for `-Xmx4G`: keep `--tests` selections
narrow and do not use the `parallelTest` task. The `integrationTest` suite spawns real node
processes and binds ports; its dependencies are cached, but it is slow and is rarely needed.

A reproducer written as a JUnit test under `rskj-core/src/test/java` is the most useful form a
finding can take — it drops straight into our suite.
