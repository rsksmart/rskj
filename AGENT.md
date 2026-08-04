# AGENT.md

Instructions for AI coding agents working in this repository. Trust this document;
fall back to a focused repo search only when a step is missing here or is proven
wrong by direct observation.

## Repository summary

RSKj is the Java implementation of the Rootstock node — an EVM-compatible sidechain
that is merge-mined with Bitcoin and operates a Bitcoin↔RSK two-way peg
("**powpeg**"). The repository is a single-module Gradle build; the module is
`rskj-core` and the entry point is `co.rsk.Start`. Java 17 source/target. Gradle
wrapper 8.6. License: GNU LGPL v3.0.

## Versioning and release tags

Published releases are tagged `<CODENAME>-<MAJOR>.<MINOR>.<PATCH>` (e.g.
`VETIVER-9.0.3`, `REED-8.1.0`). The codename is the network-upgrade name and rotates
with each major version. In `rskj-core/src/main/resources/version.properties`,
`modifier` holds that codename on a release and is **non-empty for every release
tag**; the full artifact version is `versionNumber-modifier` (e.g. `9.0.3-VETIVER`).
An empty `modifier` — or `SNAPSHOT` — denotes a **development / local build only**
and never appears on a release tag.

## Project layout — where to find things

Single module: `rskj-core/` (the only published module). Production code under
`src/main/java/`, split across two top-level package roots:

- `co.rsk.*` — Rootstock-specific code.
- `org.ethereum.*` — the Ethereum compatibility layer inherited from the EthereumJ
  ancestry.

### Package map by capability

- **Chain state / core**: `co.rsk.core` — blocks, transactions, accounts, chain
  state.
- **Networking**: `co.rsk.net` (P2P sync, peer management, message handling —
  `SyncProcessor`, `NodeMessageHandler`, `co.rsk.net.messages`, `co.rsk.net.sync`,
  `co.rsk.net.discovery`) together with `org.ethereum.net` (devp2p transport: rlpx
  handshake, peer capabilities, p2p message codecs).
- **Execution / EVM** (consensus-critical): `org.ethereum.vm` (the EVM interpreter)
  + `co.rsk.vm` + `org.ethereum.core` (block and transaction execution).
- **Powpeg / bridge** (consensus-critical): `co.rsk.peg` and its subpackages
  (`bitcoin`, `federation`, `flyover`, `lockingcap`, `pegin`, `pegininstructions`,
  `vote`, `whitelist`, `feeperkb`, `storage`, `union`, `utils`) — implements the
  Bitcoin↔RSK two-way peg.
- **Mining** (consensus-critical): `co.rsk.mine` — mining and merge-mining with
  Bitcoin.
- **Mining rewards** (consensus-critical): `co.rsk.remasc` — the reward
  distribution contract.
- **State storage** (consensus-critical): `co.rsk.trie` — Merkle trie state
  storage.
- **Validation** (consensus-critical): `co.rsk.validators` — block and transaction
  validation rules.
- **Precompiles** (consensus-critical): `co.rsk.pcc` — precompiled contracts
  invoked from the EVM.
- **JSON-RPC API**: `co.rsk.rpc`, `co.rsk.jsonrpc`.
- **Supporting subsystems**: `co.rsk.config`, `co.rsk.db`, `co.rsk.crypto`,
  `co.rsk.scoring`, `co.rsk.metrics`, `co.rsk.util`, `co.rsk.panic`,
  `co.rsk.logfilter`.
- **Standalone CLI tools**: `co.rsk.cli.tools.*` — `ImportBlocks`, `ExportBlocks`,
  `ConnectBlocks`, `RewindBlocks`, `ExecuteBlocks`, `ImportState`, `ExportState`,
  `DbMigrate`, `IndexBlooms`, `ShowStateInfo`, `StartBootstrap`,
  `ValidateBtcHeaders`, `GenerateOpenRpcDoc`.
- **Ethereum compatibility layer**: `org.ethereum.*` (config, net, core, vm, db,
  rpc, sync, validator). Per-network hardfork **activation heights** live in the
  config resources (`rskj-core/src/main/resources/config/{main,testnet,testnet2,regtest}.conf`,
  under `hardforkActivationHeights` / `consensusRules`). The
  `org.ethereum.config.blockchain.upgrades` package (`ActivationConfig`,
  `ConsensusRule`, `NetworkUpgrade`) defines the RSKIP / hard-fork identifiers and
  the loader (`ActivationConfig.read`) that parses those heights — not the height
  values themselves. Treat both the config values and this package as
  consensus-critical.

### Tests

- `src/test/java` — unit tests (JUnit 5).
- `src/integrationTest/java` — integration tests (own `JvmTestSuite`).
- `src/fuzzTest/java` — Jazzer-based fuzz tests (see `FUZZING.md`).
- `src/jmh/java` — JMH benchmarks.

### Config and docs

- Per-network config: `rskj-core/src/main/resources/config/{main,testnet,testnet2,regtest}.conf`;
  base config in `reference.conf` / `expected.conf` (same directory).
- `docs/03-node-operators/` — node-operator guides (installation, configuration,
  running, troubleshooting); mirrored to the public Rootstock Developers Portal —
  see `docs/README.md` for the sync pipeline.
- `doc/rpc/` — OpenRPC spec fragments (one file per JSON-RPC method plus shared
  schemas), used to generate the RPC API reference via the `GenerateOpenRpcDoc` CLI
  tool.

## Build and test commands

Toolchain: **JDK 17** and the in-repo Gradle wrapper. Always use `./gradlew`, never
a system Gradle.

**Bootstrap when `gradle/wrapper/gradle-wrapper.jar` is missing:** run
`./configure.sh`. It downloads the wrapper jar and verifies its SHA256, exiting
non-zero on mismatch.

Commands that mirror CI:

- `./gradlew assemble` — compile production sources, no tests.
- `./gradlew build -x test` — full build minus the unit-test task. Per-source-set
  Checkstyle tasks (`checkstyleMain`, `checkstyleTest`, …) are wired into `check`
  and still run. `checkstyleAll` and `integrationTest` (a separate `JvmTestSuite`)
  are **not** wired into `check`, so this command does **not** run them — a green
  `build -x test` does not mean integration tests or full checkstyle passed.
- `./gradlew test` — unit tests (JUnit 5). Filter with `--tests
  "fully.qualified.ClassName"` or `--tests "fully.qualified.ClassName.methodName"`.
- `./gradlew integrationTest` — integration tests; depends on `assemble`.
- `JAZZER_FUZZ=1 ./gradlew runAllFuzzTests --info --continue` — the full fuzz suite
  (mirrors CI); a plain `fuzzTest` task also runs the suite under JUnit platform
  with Jacoco coverage.
- `./gradlew jmh -PjmhArgs="-wi 5 -i 5 -f 1 -p suite=e2e -p host=http://localhost:4444 co.rsk.jmh.web3.BenchmarkWeb3"`
  or `./gradlew jmh -Pbenchmark=BenchmarkWeb3E2ERunner -Phost=http://localhost:4444 -Pnetwork=regtest`
  — JMH benchmarks.
- `./gradlew checkstyleAll` — runs `checkstyleMain`, `checkstyleTest`,
  `checkstyleJmh`, `checkstyleIntegrationTest`, `checkstyleFuzz`.
- `./gradlew checkstyleFile -PfilePath="src/main/java/A.java,src/main/java/B.java"`
  — checkstyle on a specific file set (mirrors CI's lint job). Paths must be
  relative to the `rskj-core` subproject — repo-relative paths like
  `rskj-core/src/main/java/...` silently match nothing.
- `./gradlew spotlessJavaCheck -PratchetFrom=origin/master` — Spotless formatting
  check on changed files only (replace `master` with the actual base branch);
  `./gradlew spotlessApply` auto-fixes.
- `./gradlew fatJar` — produces
  `rskj-core/build/libs/rskj-core-<version>-all.jar`, where `<version>` is
  `versionNumber[-modifier]` from `version.properties`.

Style config: Checkstyle at `config/checkstyle/checkstyle.xml` /
`config/checkstyle/suppressions.xml`; Spotless is declared in
`rskj-core/build.gradle` with `enforceCheck false` and currently enforces only
`endWithNewline()`. There is no `.editorconfig`.

## Containerized build and reproducible builds

`/Dockerfile` is the canonical container build for the node and the reference
for "how RSKj is built in a container". It bootstraps with `./configure.sh`
and verifies that script against a signed checksum with
`gpg --verify --output SHA256SUMS SHA256SUMS.asc && sha256sum --check SHA256SUMS`
(`SHA256SUMS.asc` is a cleartext-signed file; `--output` extracts the payload
and the `&&` chain gates the build on a good signature). This exact pattern is
established and working — do **not** flag it as broken or claim the output
file "is never created".

The workflow and templates under `.github/reproducible-build/` exist to
**mirror** `/Dockerfile` for a published tag, so prefer consistency with
`/Dockerfile` over alternative idioms; any change to the verify/build sequence
should be made in `/Dockerfile` and the templates **together**, not in one
alone. Before flagging a shell or Docker idiom here as incorrect, confirm it
is not already the established, working pattern in `/Dockerfile`,
`build_and_test.yml`, or `lint-java-code.yml`.

## Running a node locally

Build then run directly with the fat jar:

```bash
./gradlew fatJar
java -cp rskj-core/build/libs/rskj-core-<version>-all.jar co.rsk.Start --regtest
```

Network flags are mutually exclusive; default is mainnet: `--main`, `--testnet`,
`--regtest`. Other notable flags: `--reset` (wipe DB, restart from genesis),
`--import`, `--verify-config`, `--print-system-info`. See
`docs/03-node-operators/04-setup/03-configuration/02-cli.md` for the full list.

## Coding standards

Full style rules live in `./CONTRIBUTING.md`; broader design guidance lives in
`./coding-principles.md`. Don't restate them — read those files. The rules an
agent is most likely to violate by default:

- Prefer `Optional<T>` over `null`; annotate any method that can still return
  `null` with `@Nullable`.
- Brace every control structure, even single-line bodies.
- Treat `@VisibleForTesting` as a design smell, not a shortcut.
- Keep diffs minimal and reviewable: don't reorder, rename, or reformat unrelated
  code in the same change.
- Include units in names for monetary/time values (`amountInSatoshis`,
  `amountInWei`, `amountInRBTC`, `timeoutMillis`); never rely on implicit units.
- Optimize for readability over cleverness — code is read far more often than
  it's written.
- Use intention-revealing names; prefer self-explanatory code over comments
  that explain *what* it does (comments should explain *why*, when non-obvious).
- Prefer focused functions and classes with a single clear responsibility;
  remove duplication when practical.
- Don't introduce abstractions, indirection, or code movement without a clear
  benefit — this applies especially to your own refactoring suggestions.

## Testing conventions

These apply repo-wide, not just to one package:

- Tests must be readable and independent of each other, and must cover
  boundary/error cases, not just the happy path.
- Prefer an existing builder over manual construction — check
  `co.rsk.test.builders` (shared repo-wide, e.g. `BridgeSupportBuilder`,
  `FederationSupportBuilder`) and the package-local test tree before
  hand-assembling an object with `new`/a long setter chain.
- **Avoid mocking when possible.** Prefer building real collaborators
  (via the builders above, or their own constructors) over mocking them.
  Reserve `mock(...)` for narrow boundary interfaces that are genuinely
  impractical to construct for real (external I/O, things with no simple
  in-memory implementation) — not as the default way to satisfy a
  constructor parameter.
- Avoid `@ParameterizedTest`/`@MethodSource`/`@ValueSource`/`@EnumSource`.
  Write a separate, explicitly named test method per case instead; use
  `@Nested` classes to group many related cases/variables within one class
  rather than parameterizing.
- For code gated by RSKIP/hardfork activation checks: default tests to the
  fully-activated preset (e.g. `ActivationConfigsForTest.all()`) and add
  exactly one dedicated test locking in the pre-activation/legacy behavior
  only when a specific RSKIP actually changes that behavior — don't sweep
  every historical activation preset, and don't parameterize across them.
- Don't overload test helper methods; give differently-behaving helpers
  distinct names instead.
- Mark test phases with `// arrange` / `// act` / `// assert` comments;
  collapse to a single `// act & assert` comment when both happen in the same
  statement.

For a fully worked example of these conventions against real classes, see
`rskj-core/src/test/java/co/rsk/peg/AGENT.md`.

## CI gates (what will block a PR)

Every workflow uses JDK 17 and the in-repo Gradle wrapper. Workflows live in
`.github/workflows/`.

- **PR-blocking**: `build_and_test.yml` (build, unit tests, integration tests,
  mining-tests against dockerized bitcoind + the `mining-integration-tests` repo),
  `lint-java-code.yml` (checkstyle + Spotless, changed files only),
  `dependency-review.yml` (blocks on high-severity advisories in new
  dependencies). `rit.yml` (Rootstock integration tests) and `codeql.yml` run only
  when the PR base branch is `master` or matches `*-rc`.
- **Informational** (don't gate merges): `fuzz-test.yml` (Jazzer fuzzing on
  master/release branches), the docker release workflows, `devportal-update.yml`,
  `scorecard.yml`. SonarQube (`smell-test` job) runs after unit tests but isn't a
  hard gate unless enforced externally via branch protection.

For the detailed, reviewer-framed breakdown of these gates and PR-review
heuristics, see `.github/copilot-instructions.md`.

## Sensitive / consensus-critical areas — extra scrutiny

Changes touching the following require deeper review even when the diff is small.
Treat unexplained behavioral changes here as potential defects until proven
otherwise:

- `co.rsk.peg`, `co.rsk.mine`, `co.rsk.remasc`, `co.rsk.vm`, `co.rsk.trie`,
  `co.rsk.validators`, `co.rsk.pcc`, and `org.ethereum.vm` / `org.ethereum.core`.
- Per-network activation heights in `config/*.conf`
  (`hardforkActivationHeights` / `consensusRules`), the
  `org.ethereum.config.blockchain.upgrades` loader, and any code gated by RSKIP
  activation flags.
- Anything that changes consensus behavior, persistence formats, JSON-RPC
  response shapes, wire-protocol messages, or block/transaction validation rules.
- Any new or upgraded dependency — requires a companion PR to
  `rsksmart/reproducible-builds` demonstrating the downloaded binary's hash
  matches an independent compilation.
- `.github/CODEOWNERS`: `@rsksmart/rsk-core` owns mining-related code;
  `@rsksmart/rsk-fed` owns peg/bridge code. Changes to activation heights should
  get review from both groups even where CODEOWNERS path coverage doesn't force
  it automatically.

## Pull requests

Follow `./CONTRIBUTING.md` for PR etiquette (separate commits per concern, rebase
on `master`, keep diffs minimal). PRs targeted at `master` or a `*-rc` branch must
populate every section of `.github/pull_request_template.md`, including the
"Requires Activation Code (Hard Fork)" checklist question for anything touching
consensus, validators, VM, peg, mining, or activation logic.
