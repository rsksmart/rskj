# AGENT.md — co.rsk.peg (powpeg bridge)

Package-specific instructions for `co.rsk.peg`. This extends the repo-root
`/AGENT.md` — read that first for build/test/run commands, repo-wide coding
standards, and PR conventions. Everything below is specific to this package.

This package implements the **powpeg**: the Bitcoin↔RSK two-way peg bridge. It is
money-handling and **consensus-critical** — every RSK node must derive the exact
same result from the exact same inputs, forever, including against transactions
mined years before a given code change. Treat any behavioral change here as
high-risk by default.

## Entry point and wiring

`Bridge.java` is the precompiled contract invoked by the EVM at address
`0x0000000000000000000000000000000001000006`, registered in
`org.ethereum.vm.PrecompiledContracts`. `Bridge` is a thin ABI dispatcher: it uses
the `BridgeMethods` enum (ABI signature, gas cost, activation gate, permission
check, and executor function per method) to decode the call and delegates the
actual work to a `BridgeSupport` instance, built per-call via the injected
`BridgeSupportFactory`.

## Key classes

- **`Bridge`** — the precompile entry point (thin dispatcher only; "actual
  functionality is found in BridgeSupport" per its own javadoc).
- **`BridgeMethods`** — the ABI/dispatch table: one enum entry per contract method.
- **`BridgeSupportFactory`** — builds `BridgeSupport` with the right
  `BridgeConstants`/`ActivationConfig` for the active network.
- **`BridgeSupport`** (~3,500 lines) — the central business-logic class: all
  peg-in, peg-out, federation, and whitelist logic lives here. It is a known
  god-class by necessity of the domain, not an invitation to add more to it —
  prefer extracting cohesive logic into its own class/subpackage over growing
  this file further, and read broadly before editing since a change to one method
  can affect shared queues/state used elsewhere in the class.
- **`BridgeStorageProvider`** + **`BridgeSerializationUtils`** — the bridge's
  persistence layer: an RLP-based, **versioned** storage format (e.g.
  `FederationFormatVersion`) for federations, UTXOs, release requests, etc.
  Changing encode/decode logic here changes what every node reads from and
  writes to the repository trie — this is consensus-breaking unless gated behind
  a new format version and RSKIP activation.
- **`BridgeUtils`** — general BTC/RSK conversion and validation helpers.
- **`BridgeUtilsLegacy`**, **`PegUtilsLegacy`** — explicitly `@deprecated`,
  kept only to reproduce pre-hardfork consensus behavior. Do not "clean up" or
  delete these; do not reuse them for new code paths.
- **`PegUtils`**, **`PegTxType`**, **`PeginInformation`** — classify and describe
  an inbound BTC transaction (pegin/pegout/migration, parsed sender/protocol
  info).
- **`ReleaseTransactionBuilder`**, **`ReleaseRequestQueue`**,
  **`PegoutsWaitingForConfirmations`** — build outbound (release/pegout) BTC
  transactions and track them from request through BTC confirmation.
- **`WalletProvider`**, **`RskUTXOProvider`** — bitcoinj integration: supply a
  `Wallet` / `UTXOProvider` backed by RSK-tracked UTXOs.
- **`StateForFederator`**, **`StateForProposedFederator`** — RLP-encodable DTOs
  of pending-signature state exposed to federators.
- **`BridgeEvents`** — enum of Solidity event signatures the bridge logs
  (`lock_btc`, `pegin_btc`, `release_btc`, `commit_federation`, etc.).
- **`BridgeState`** — debug-only DTO for dumping bridge state; not production
  code.

## Subpackages

- **`bitcoin/`** — BTC script/tx plumbing: ERP/flyover redeem script builders,
  coin selectors, merkle branch/coinbase utilities.
- **`btcLockSender/`** — parses a BTC tx's input script to determine the sender's
  address/type (P2PKH, P2SH-multisig, P2SH-P2WPKH/WSH) for refund/attribution.
- **`constants/`** — `BridgeConstants` per network (Main/Test/RegTest).
- **`federation/`** — federation model and lifecycle (`Federation`,
  `ErpFederation`, `PendingFederation`, `FederationSupport(Impl)`, storage).
- **`feeperkb/`** — BTC tx fee-per-kb voting and storage (miners vote on fee
  rate); mirrors federation's Support/StorageProvider pattern.
- **`flyover/`** — flyover (fast bridge) federation info and response codes.
- **`lockingcap/`** — locking-cap (pegin value cap) voting and storage; same
  pattern as `feeperkb`.
- **`pegin/`** — pegin evaluation result/process-action/rejection-reason enums.
- **`pegininstructions/`** — parses the versioned, OP_RETURN-encoded pegin
  instruction protocol for the RSK destination address.
- **`storage/`** — generic `StorageAccessor` abstraction reused by federation,
  feeperkb, lockingcap, whitelist, and union storage providers.
- **`union/`** — a related but distinct sub-bridge ("Union Bridge") for RBTC
  transfer to/from another chain; not the core BTC peg.
- **`utils/`** — event logging (`BridgeEventLogger`), BTC tx/merkle format
  utilities, rejected-pegout/non-refundable reason enums.
- **`vote/`** — generic authorized-elector voting machinery
  (`AddressBasedAuthorizer`, `ABICallElection`/`ABICallSpec`), reused by
  federation, whitelist, fee-per-kb, and locking-cap changes.
- **`whitelist/`** — lock whitelist (one-off/unlimited whitelisted BTC
  addresses) voting and storage.

## Federation change process

A federation change is the vote-gated replacement of the active federation's
member set. Authorized federation members call ABI functions (`create`,
`addFederatorPublicKeyMultikey`, `commit`, `rollback`), dispatched through
`BridgeSupport.voteFederationChange(tx, callSpec)` →
`FederationSupportImpl.voteFederationChange`. This uses `vote.ABICallElection` to
tally votes on an `ABICallSpec` and `vote.AddressBasedAuthorizer` to check the
caller is an authorized voter; once enough votes agree, a `PendingFederation` is
built up and eventually committed as the new active `Federation`. The same voting
machinery backs whitelist, fee-per-kb, and locking-cap changes.

## RSKIP / activation gating

Peg logic is pervasively gated by hardfork activation checks — dozens of
`activations.isActive(RSKIPxxx)` call sites in `BridgeSupport` alone, well over a
hundred distinct RSKIP identifiers referenced package-wide, and `BridgeMethods`
gates whether entire ABI methods are even callable. **Do not assume a code path
applies universally** — check which activation guards a given branch before
changing or extending it, and expect that "the same" logical operation may have
multiple activation-gated implementations coexisting (a legacy one and a current
one).

## Tests

`rskj-core/src/test/java/co/rsk/peg` mirrors the subpackages above, plus:

- **`performance/`** — gas-cost/benchmarking tests measuring precompile
  execution cost, not correctness.
- **`resources/`** — shared test fixtures (e.g. `TestConstants`).
- **`simples/`** — hand-rolled test doubles (`SimpleBlockChain`,
  `SimpleRskTransaction`) used across peg test suites instead of full mocks;
  prefer reusing these over introducing new mocking patterns.

There is currently no `co.rsk.peg` package under `src/integrationTest` or
`src/fuzzTest` — peg is exercised only by unit tests. If that changes, use the
same `--tests` filtering shown below with `./gradlew integrationTest` /
`runAllFuzzTests` instead of `test` (see root `AGENT.md`).

### Running just this package's tests

From the repo root (all via the `./gradlew` wrapper, per root `AGENT.md`):

```bash
# Every unit test under co.rsk.peg, including all subpackages
./gradlew test --tests "co.rsk.peg.*"

# One subpackage only, e.g. federation
./gradlew test --tests "co.rsk.peg.federation.*"

# One test class
./gradlew test --tests "co.rsk.peg.BridgeSupportTest"

# One test method
./gradlew test --tests "co.rsk.peg.BridgeSupportTest.methodName"
```

`--tests` patterns match against the fully-qualified test class name, so `*`
spans subpackages too — `co.rsk.peg.*` runs everything under this package
tree, not just classes directly in `co.rsk.peg`. Note that this also picks up
`co.rsk.peg.performance` — those are gas-cost benchmarks, not correctness
tests, and can noticeably lengthen the run; scope to the specific subpackage
you're changing (e.g. `co.rsk.peg.federation.*`) for faster feedback, and run
`co.rsk.peg.performance.*` on its own when you actually want benchmark numbers.

## Extra scrutiny

On top of the repo-root sensitive-area list: `.github/CODEOWNERS` assigns this
package to `@rsksmart/rsk-fed`. Any change to money movement, federation change
flow, voting eligibility, or storage serialization format needs explicit
discussion of its RSKIP/activation gating in the PR description — see the root
`AGENT.md` for the "Requires Activation Code (Hard Fork)" PR checklist item.
