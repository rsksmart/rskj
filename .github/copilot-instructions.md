# Copilot custom instructions for RSKj

Trust these instructions. If a step is missing here or appears wrong, fall back to a focused repo search; do not perform a broad exploration before consulting this document.

## Repository summary

RSKj is the Java implementation of the Rootstock node — an EVM-compatible sidechain that is merge-mined with Bitcoin and operates a Bitcoin↔RSK two-way peg ("powpeg"). The repository is a single-module Gradle build; the primary module is `rskj-core` and the entry point is `co.rsk.Start`. Java 17 source/target. Gradle wrapper 8.6. License: GNU LGPL v3.0.

## Versioning and release tags

Published releases are tagged `<CODENAME>-<MAJOR>.<MINOR>.<PATCH>` (e.g. `VETIVER-9.0.3`, `REED-8.1.0`). The codename is the network-upgrade name and rotates with each major version. In `rskj-core/src/main/resources/version.properties`, `modifier` holds that codename on a release and is **non-empty for every release tag**; the full artifact version is `versionNumber-modifier` (e.g. `9.0.3-VETIVER`). An empty `modifier` — or `SNAPSHOT` — denotes a **development / local build only** and never appears on a release tag.

## PR review priorities

Review-ability is the top priority. Use these heuristics to flag PRs and to author them.

- **Scope discipline.** Flag PRs whose changes exceed the stated motivation or whose diff is too large to follow. Recommend splitting.
- **Boy Scout Rule applies.** Small, localized code-style improvements alongside a real change are welcome. Large-scale formatting or refactor PRs are not.
- **Commit hygiene.** Cosmetic / style changes and core-functionality changes may share a PR, but they must live in **separate commits**. Flag commits that mix the two. Each commit should compile.
- **Refactors are incremental.** Flag broad refactors that lack a behavioural reason or are not split into reviewable steps.
- **Test coverage cannot regress.** New code paths — including edge cases and error paths — require unit tests. Untested exception handling needs explicit written justification in the PR description.
- **Anti-flakiness.** This repository has a known flakiness backlog the team is actively reducing. Flag new or changed tests that use `Thread.sleep` for synchronization, real-clock timing dependencies, real network calls without stubs, parallel-execution ordering assumptions, fragile filesystem assumptions, or that are newly `@Disabled` / `@Ignore`d.
- **Clean Code is a guideline, not a rule.** The codebase is large and partly legacy; do not demand rewrites in legacy modules. Apply Clean Code principles where reasonable.
- **No unrelated reordering or renaming.** Diffs that reorder or rename unchanged code without a clear benefit hurt review-ability — flag them.
- **Ground claims of breakage in observation.** Before asserting that code is broken, will fail at runtime, or produces invalid output, verify the claim against repo reality — the actual file format, an existing working usage of the same idiom, or the trigger/permission model. Prefer "this is unusual, please confirm X" over "this is broken". Distinguish *the underlying tool/library supports case X* from *this repo's inputs ever reach case X*.
- **Review the change holistically.** Do not raise a finding that another part of the same diff already prevents (e.g. an early validation guard that makes a later branch unreachable). Read the whole changed unit before commenting on a line in isolation.
- **PR template compliance.** PRs targeted at **master** or a branch ending with the **-rc** sufix must populate every section of `.github/pull_request_template.md`: **Description**, **Motivation and Context**, **How Has This Been Tested?**, **Types of changes**, **Checklist**. The checklist contains a deliberate "Requires Activation Code (Hard Fork)" question — flag PRs that touch consensus, validators, VM, peg, mining, or activation logic without answering it.

Java style conventions are defined in `CONTRIBUTING.md`. Key reviewer-facing rules: prefer constructor injection with `private final` fields and `Objects.requireNonNull` on parameters; prefer `Optional<T>` over `null` (annotate nullable returns with `@Nullable`); always brace control structures; treat `@VisibleForTesting` as a design smell and flag accordingly. Standard Java naming applies: lowercase packages, `UpperCamelCase` classes, `lowerCamelCase` members, `CONSTANT_CASE` for static final immutable constants.

## Rootstock coding principles

Follow the coding principles documented in `docs/coding-principles.md`.

Key rules:

* Code is read more often than written. Optimize for readability and maintainability.
* Use intention-revealing names.
* Include units in monetary and time-related names, such as `amountInSatoshis`, `amountInWei`, `amountInRBTC`, and `timeoutMillis`.
* Never rely on implicit monetary units.
* Prefer focused functions and classes with clear responsibilities.
* Eliminate duplication when practical.
* Prefer self-explanatory code over explanatory comments.
* Tests should be readable, independent, and cover boundary/error cases.
* Refactors must improve clarity, maintainability, or correctness. Do not introduce abstractions, indirection, or code movement without a clear benefit.

## CI gates a reviewer must predict

Every build job uses JDK 17 and the in-repo Gradle wrapper 8.6. Workflows live in `.github/workflows/`.

**PR-blocking** (run on `pull_request`):

- `build_and_test.yml` — a `build` job runs `./gradlew --no-daemon --stacktrace build -x test`. Then three jobs fan out in parallel, each `needs: build`: `unit-tests-java17` (`./gradlew test`), `integration-tests` (`./gradlew integrationTest`), and `mining-tests`. A fourth job, `smell-test`, has `needs: unit-tests-java17` (not `build`) — it downloads the unit-test results/reports and runs the SonarQube scan, so it executes *after* unit tests rather than in parallel with them. `integrationTest` is declared as a `JvmTestSuite` in `rskj-core/build.gradle`, but an additional test suite is **not** auto-wired into `check`, so the `build -x test` step does **not** run the integration tests; they execute only in the dedicated `integration-tests` job. The `mining-tests` job starts dockerized bitcoind services, checks out `rsksmart/mining-integration-tests`, and executes `npm test` against the freshly built node — this gate is not covered by the listed Gradle commands and can fail independently of them. The `smell-test` job runs a SonarQube scan only when the repository secret `SONAR_TOKEN` is configured; the workflow does **not** set `sonar.qualitygate.wait` or otherwise block on the quality-gate result, so treat Sonar findings as informational unless branch-protection rules enforce the gate externally.
- `lint-java-code.yml` — `./gradlew --no-daemon checkstyleFile -PfilePath="<files>" -x build` and `./gradlew --no-daemon spotlessJavaCheck -PratchetFrom=origin/$BASE_REF -x build`. Both commands operate on **changed files only**; do not flag style issues on lines outside the diff. The `lint-java-code` job runs on every PR (any base branch); when no `.java` file changed it computes an empty file list, skips the lint steps, prints a skip message, and reports success — a passing `lint-java-code` check does not by itself imply any Java was linted.
- `rit.yml` — Rootstock integration tests via the external `rsksmart/rootstock-integration-tests` action. Only runs on PRs whose base branch is `master` or matches `*-rc`; PRs targeting any other base branch do not hit this gate. Slow and historically flaky; unexplained failures may indicate infrastructure rather than a code defect.
- `codeql.yml` — CodeQL static analysis (autobuild). Only runs on PRs whose base branch is `master` or matches `*-rc`.
- `docker-verification.yml` — triggers when `Dockerfile` or this workflow changes; builds and starts the container. The `docker` job is guarded by `github.event.pull_request.head.repo.fork == false`, so PRs originating from forks skip this gate entirely.
- `dependency-review.yml` — blocks on high-severity advisories in newly added dependencies.

**Informational** (do not gate merges): `fuzz-test.yml` (Jazzer-based fuzzing on `master`/release branches), `docker-release-master-to-edge.yml`, `docker-release-tags-to-latest.yml`, `devportal-update.yml`, `scorecard.yml`.

## Build and test commands

Toolchain: **JDK 17** and the in-repo Gradle wrapper. Always use `./gradlew`, never a system gradle.

**Bootstrap when `gradle/wrapper/gradle-wrapper.jar` is missing:** run `./configure.sh`. The script downloads the wrapper jar, verifies its SHA256, and exits non-zero on mismatch. The Dockerfile invokes it before any gradle call.

Commands that mirror CI:

- `./gradlew assemble` — compile production sources, no tests.
- `./gradlew build -x test` — runs the full `build` minus the unit-test task. The per-source-set Checkstyle tasks (`checkstyleMain`, `checkstyleTest`, …) are wired into `check` by the checkstyle plugin and still run. The custom `checkstyleAll` aggregator task and `integrationTest` (a `JvmTestSuite`) are **not** wired into `check`, so this command does **not** run them — invoke `./gradlew integrationTest` and `./gradlew checkstyleAll` explicitly. A green `build -x test` does not mean integration tests passed.
- `./gradlew test` — unit tests (JUnit 5). Filter with `--tests "FullyQualifiedClass"` or `--tests "FullyQualifiedClass.method"`.
- `./gradlew integrationTest` — integration tests; depends on `assemble`.
- `./gradlew checkstyleAll` — runs `checkstyleMain`, `checkstyleTest`, `checkstyleJmh`, `checkstyleIntegrationTest`, and `checkstyleFuzz`.
- `./gradlew checkstyleFile -PfilePath="src/main/java/A.java,src/main/java/B.java"` — checkstyle on a specific file set (mirrors CI). Paths must be relative to the `rskj-core` subproject; the lint workflow strips the `rskj-core/` prefix before invocation, and the task resolves the list via `project.files(...)` inside `rskj-core`. Repo-relative paths (e.g. `rskj-core/src/main/java/...`) silently match nothing.
- `./gradlew spotlessJavaCheck -PratchetFrom=origin/master` — Spotless on changed files; replace `master` with the PR base branch when applicable.
- `./gradlew spotlessApply` — auto-fix Spotless violations.
- `./gradlew fatJar` — produces `rskj-core/build/libs/rskj-core-<version>-all.jar`, where `<version>` is `versionNumber[-modifier]` from `rskj-core/src/main/resources/version.properties` (e.g. `rskj-core-9.1.0-SNAPSHOT-all.jar` for snapshot builds, `rskj-core-9.1.0-all.jar` when the modifier is empty — an empty modifier is a local/dev state only; see *Versioning and release tags*).

Style configuration files (informational): checkstyle at `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml`; Spotless is declared inside `rskj-core/build.gradle` with `enforceCheck false` (not bound to the `check` lifecycle — the lint workflow invokes `spotlessJavaCheck` explicitly) and currently enforces only `endWithNewline()`, scoped by the ratchet to changed files. There is no `.editorconfig`.

## Containerized build and reproducible builds

`/Dockerfile` is the canonical container build for the node and the reference for "how RSKj is built in a container". It bootstraps with `./configure.sh` and verifies that script against a signed checksum with `gpg --verify --output SHA256SUMS SHA256SUMS.asc && sha256sum --check SHA256SUMS` (`SHA256SUMS.asc` is a cleartext-signed file; `--output` extracts the payload and the `&&` chain gates the build on a good signature). This exact pattern is established and working — do **not** flag it as broken or claim the output file "is never created".

The workflow and templates under `.github/reproducible-build/` exist to **mirror** `/Dockerfile` for a published tag, so prefer consistency with `/Dockerfile` over alternative idioms; any change to the verify/build sequence should be made in `/Dockerfile` and the templates **together**, not in one alone. Before flagging a shell or Docker idiom here as incorrect, confirm it is not already the established, working pattern in `/Dockerfile`, `build_and_test.yml`, or `lint-java-code.yml`.

## Project layout

- `rskj-core/` — the only published module. Production code under `src/main/java/`. Resources under `src/main/resources/` include `reference.conf`, `expected.conf`, and per-network configs `config/{main,testnet,testnet2,devnet,regtest}.conf`. Test roots: `src/test/java`, `src/integrationTest/java`, JMH benchmarks in `src/jmh/java`.
- Top-level packages under `co.rsk.*` and their role:
  - `co.rsk.core` — blocks, transactions, accounts, chain state.
  - `co.rsk.net` — P2P networking and peer management.
  - `co.rsk.mine` — mining and merge-mining with Bitcoin (consensus-critical).
  - `co.rsk.peg` — Bitcoin↔RSK two-way peg / powpeg bridge (consensus-critical).
  - `co.rsk.remasc` — mining-reward distribution contract (consensus-critical).
  - `co.rsk.vm` — EVM execution (consensus-critical).
  - `co.rsk.trie` — Merkle trie state storage (consensus-critical).
  - `co.rsk.validators` — block and transaction validation rules (consensus-critical).
  - `co.rsk.pcc` — precompiled contracts invoked from the EVM (consensus-critical).
  - `co.rsk.rpc`, `co.rsk.jsonrpc` — JSON-RPC API surface.
  - `co.rsk.config`, `co.rsk.db`, `co.rsk.crypto`, `co.rsk.scoring`, `co.rsk.metrics`, `co.rsk.util`, `co.rsk.panic`, `co.rsk.logfilter` — configuration, storage, crypto, and supporting subsystems.
  - `co.rsk.cli.tools.*` — standalone CLI utilities (`ImportBlocks`, `ExportBlocks`, `ConnectBlocks`, `RewindBlocks`, `ExecuteBlocks`, `ImportState`, `ExportState`, `DbMigrate`, `IndexBlooms`, `ShowStateInfo`, `StartBootstrap`, `ValidateBtcHeaders`, `GenerateOpenRpcDoc`).
- `org.ethereum.*` — Ethereum compatibility layer inherited from the EthereumJ ancestry. The actual per-network activation **heights** live in the config resources (`rskj-core/src/main/resources/config/{main,testnet,testnet2,devnet,regtest}.conf`, under `hardforkActivationHeights` / `consensusRules`). The `org.ethereum.config.blockchain.upgrades` package (`ActivationConfig`, `ConsensusRule`, `NetworkUpgrade`) defines the RSKIP / hard-fork identifiers and the loader (`ActivationConfig.read`) that parses those heights — not the height values themselves. Treat both the config values and this package as consensus-critical.
- `.github/CODEOWNERS` — `@rsksmart/rsk-core` owns mining-related code; `@rsksmart/rsk-fed` owns peg/bridge. Treat changes to activation heights in config resources as requiring review from both groups, even if current CODEOWNERS path coverage does not enforce that automatically.

## Sensitive areas (extra scrutiny)

Changes touching the following require deeper review even when the diff is small. Treat unexplained behavioural changes here as potential defects until proven otherwise.

- `co.rsk.peg`, `co.rsk.mine`, `co.rsk.remasc`, `co.rsk.vm`, `co.rsk.trie`, `co.rsk.validators`, `co.rsk.pcc`.
- The per-network activation heights in `config/*.conf` (`hardforkActivationHeights` / `consensusRules`), the `org.ethereum.config.blockchain.upgrades` loader, and any code gated by RSKIP activation flags.
- Anything that changes consensus behaviour, persistence formats, JSON-RPC response shapes, wire-protocol messages, or block / transaction validation rules.
- Any new or upgraded dependency (requires a companion `rsksmart/reproducible-builds` PR).

## Trust these instructions

This document is the canonical reference for working in this repository. Trust it. Search the repo only when a step is missing here or has been proven wrong by direct observation.

One scoping caveat: the "trust, don't explore" bias optimizes authoring and CI prediction. For **review correctness specifically, the bias flips** — a claim that existing code is defective, will fail, or is non-idiomatic must be backed by direct observation (an actual failing case, the real file format, or a contradicting in-repo usage), never inferred from this document's silence on a topic.
