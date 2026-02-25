# Project Context

This file contains project-specific information that agents use to understand the codebase.

---

## Project Overview

| Field | Value |
|-------|-------|
| **Name** | rskj |
| **Description** | Java implementation of a Rootstock (RSK) blockchain full node |
| **Domain** | Blockchain / Smart Contracts / Bitcoin Sidechain |
| **License** | GNU Lesser General Public License v3.0 |
| **Version** | 8.2.0-SNAPSHOT |
| **Main Entry Point** | `co.rsk.Start` |

Rootstock is a smart contract platform secured by Bitcoin's hash power via merged mining. RskJ allows running a full node that participates in the RSK network, executes EVM-compatible smart contracts, and maintains the 2-way peg (bridge) with Bitcoin.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 17 |
| Build System | Gradle 8.6 (wrapper) |
| Testing | JUnit 5 (Jupiter) 5.10.3 |
| Mocking | Mockito 5.12.0 |
| Fuzzing | Jazzer 0.24.0 |
| Benchmarking | JMH 1.36 |
| Linting | Checkstyle 8.45, Spotless |
| Code Quality | SonarQube (sonarcloud.io), CodeQL |
| Coverage | JaCoCo |
| Configuration | Typesafe Config (HOCON) |
| Database | RocksDB 7.10.2, LevelDB 1.18.3 |
| Networking | Netty 4.1.78 |
| Crypto | SpongyCastle 1.58.0.0, BouncyCastle 1.59 |
| Serialization | Jackson 2.15.4 |
| RPC | jsonrpc4j 1.6 |
| CLI | Picocli 4.6.3 |
| Bitcoin | bitcoinj-thin 0.14.4-rsk-18 |
| Logging | SLF4J 1.7.36 + Logback 1.2.10 |

---

## Architecture Overview

### Dependency Injection

RskJ uses **manual dependency injection** via a central context class, not Spring or Guice.

- **`RskContext`** - Main service locator/factory that instantiates and wires all components (network, storage, RPC, bridge, consensus).
- **`RskSystemProperties`** - Configuration management using Typesafe Config (HOCON). Precedence: CLI args > env vars > system properties > network config files > `reference.conf`.
- **`ConfigLoader`** - Orchestrates configuration loading and verification against `expected.conf`.

### Startup Flow

1. `co.rsk.Start.main()` creates `RskContext` with CLI args
2. Preflight checks run via `PreflightChecksUtils`
3. `NodeRunner` starts all services (P2P, sync, RPC, mining)
4. Shutdown hook registered for graceful teardown

### Feature Activation

New features are gated by consensus rules called **RSKIPs** (RSK Improvement Proposals). Each RSKIP maps to a block height per network. The `ActivationConfig` class determines which features are active at any given block.

---

## Key Patterns

### RPC Module Pattern

JSON-RPC methods are defined as public methods on module classes. Method names follow the convention `<moduleName>_<methodName>`. Modules support feature toggling through interface variants (e.g., `EthModuleWalletEnabled` / `EthModuleWalletDisabled`).

**Reference:** `co/rsk/rpc/Web3RskImpl.java`, `co/rsk/rpc/modules/eth/EthModule.java`

```java
// Module methods are named <namespace>_<method>
public String eth_getBalance(HexAddressParam address, BlockRefParam blockRefParam) { ... }
public String eth_call(CallArgumentsParam args, BlockRefParam blockRefParam) { ... }
```

**Available RPC modules:** eth, net, rpc, web3, evm, txpool, personal, sco, debug, trace, rsk

### Precompiled Contract Pattern

Precompiled contracts are native-code EVM contracts at fixed addresses. They implement `getGasCost()` and `execute()`. RSK-specific precompiled contracts include:

| Address | Name | Purpose |
|---------|------|---------|
| `0x...1000006` | Bridge | Bitcoin-RSK 2-way peg |
| `0x...1000008` | Remasc | Mining reward distribution |
| `0x...1000009` | HDWalletUtils | BIP32/BIP44 wallet utilities |
| `0x...1000010` | BlockHeaderContract | Block header operations |
| `0x...1000011` | Environment | Network environment info |

**Reference:** `org/ethereum/vm/PrecompiledContracts.java`

### Bridge Pattern

The Bitcoin-RSK bridge is the most complex subsystem. It follows a layered design:

1. **`Bridge`** (precompiled contract) - Entry point, delegates to BridgeSupport
2. **`BridgeSupport`** - Core peg-in/peg-out logic, federation management, Bitcoin transaction processing
3. **`BridgeSupportFactory`** - Creates properly wired BridgeSupport instances
4. **`Federation`** - Immutable representation of federation members and their BTC keys
5. **`BridgeStorageProvider`** - Persistent storage for bridge state

**Reference:** `co/rsk/peg/Bridge.java`, `co/rsk/peg/BridgeSupport.java`

### Storage Pattern

Storage uses a `KeyValueDataSource` interface backed by RocksDB (primary) or LevelDB (fallback). An optional `DataSourceWithCache` decorator adds in-memory caching.

**Reference:** `org/ethereum/datasource/KeyValueDataSource.java`, `org/ethereum/datasource/RocksDbDataSource.java`

### Trie Pattern

State is stored in a custom **immutable binary trie** (not standard Merkle Patricia Trie). Modifications create new nodes. Large values support lazy retrieval. The trie is persisted via `TrieStore` backed by `KeyValueDataSource`.

**Reference:** `co/rsk/trie/Trie.java`, `co/rsk/trie/TrieStore.java`

### Testing Pattern

Tests use JUnit 5 with Mockito. Common test helpers:

- **`BlockGenerator`** - Creates test blocks and chains
- **`RskTestContext`** - Sets up test environment with dependencies
- **`World`** - Test world builder for integration-style unit tests

**Reference:** `co/rsk/blockchain/BlockchainTest.java`

```java
class BlockchainTest {
    @Test
    void addFirstBlock() {
        Blockchain blockchain = createBlockchain();
        Block block = new BlockGenerator().createChildBlock(blockchain.getBestBlock());
        blockchain.tryToConnect(block);
        Assertions.assertEquals(blockchain.getBestBlock(), block);
    }
}
```

---

## Directory Structure

```
rskj/
├── rskj-core/                         # Single Gradle module (all source code)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   ├── co/rsk/             # RSK-specific code
│   │   │   │   │   ├── cli/            # CLI interface and tools (Picocli)
│   │   │   │   │   ├── config/         # Configuration management
│   │   │   │   │   ├── core/           # Core blockchain logic
│   │   │   │   │   ├── crypto/         # Cryptographic utilities
│   │   │   │   │   ├── db/             # Database abstractions
│   │   │   │   │   ├── mine/           # Mining functionality
│   │   │   │   │   ├── net/            # P2P network protocols
│   │   │   │   │   ├── peg/            # Bitcoin bridge (largest subsystem)
│   │   │   │   │   │   ├── federation/ # Federation management
│   │   │   │   │   │   ├── flyover/    # Flyover protocol (fast peg-in)
│   │   │   │   │   │   └── ...         # Peg-in, peg-out, whitelist, etc.
│   │   │   │   │   ├── remasc/         # Mining reward system (REMASC)
│   │   │   │   │   ├── rpc/            # RPC service and modules
│   │   │   │   │   ├── scoring/        # Peer scoring system
│   │   │   │   │   ├── trie/           # State trie implementation
│   │   │   │   │   ├── validators/     # Block and transaction validators
│   │   │   │   │   └── vm/             # Virtual machine extensions
│   │   │   │   │
│   │   │   │   └── org/ethereum/       # Ethereum compatibility layer
│   │   │   │       ├── config/         # Ethereum-style configuration
│   │   │   │       ├── core/           # Core abstractions (Block, Transaction)
│   │   │   │       ├── datasource/     # Key-value data source abstractions
│   │   │   │       ├── db/             # Block store, index store
│   │   │   │       ├── listener/       # Event listener interfaces
│   │   │   │       ├── net/            # P2P protocol (ETH, RLPx)
│   │   │   │       ├── rpc/            # JSON-RPC method implementations
│   │   │   │       ├── sync/           # Blockchain synchronization
│   │   │   │       ├── util/           # Shared utilities (RLP, ByteUtil)
│   │   │   │       ├── validator/      # Block validation rules
│   │   │   │       └── vm/             # EVM implementation
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── config/             # Network configs (main, testnet, regtest, devnet)
│   │   │       ├── genesis/            # Genesis block definitions per network
│   │   │       ├── reference.conf      # Default configuration (all options)
│   │   │       ├── expected.conf       # Configuration verification schema
│   │   │       ├── logback.xml         # Logging configuration
│   │   │       ├── remasc.json         # REMASC reward configuration
│   │   │       └── version.properties  # Version info
│   │   │
│   │   ├── test/                       # Unit tests
│   │   ├── integrationTest/            # Integration tests
│   │   ├── jmh/                        # JMH performance benchmarks
│   │   └── fuzzTest/                   # Jazzer fuzz tests
│   │
│   └── build.gradle                    # Module build config (639 lines)
│
├── build.gradle                        # Root build (SonarQube setup)
├── settings.gradle                     # Project structure definition
├── config/checkstyle/                  # Checkstyle rules
├── .github/workflows/                  # CI/CD pipelines
├── Dockerfile                          # Multi-stage Docker build (JDK 17)
└── CONTRIBUTING.md                     # Contribution guidelines
```

---

## Network Configurations

| Network | Port | Network ID | Genesis | Database Dir | Auto-Mine | Peer Discovery |
|---------|------|------------|---------|--------------|-----------|----------------|
| **Mainnet** | 5050 | 775 | `rsk-mainnet.json` | `~/.rsk/mainnet/database` | No | Yes (16 bootstrap nodes) |
| **Testnet** | 50505 | 8100 | `orchid-testnet.json` | `~/.rsk/testnet/database` | No | Yes (8 bootstrap nodes) |
| **Regtest** | 50501 | 7771 | `rsk-dev.json` | `~/.rsk/regtest/database` | Yes (1s blocks) | No |
| **Devnet** | - | 44444444 | `devnet-genesis.json` | `~/.rsk/devnet/database` | Yes (20s blocks) | No |

Config files are in `rskj-core/src/main/resources/config/`. All override `reference.conf` defaults.

**Hardfork History (Mainnet):** Bahamas (3397) > Orchid (729000) > Wasabi100 (1591000) > Papyrus200 (2392700) > Iris300 (3614800) > Hop400 (4598500) > Fingerroot500 (5468000) > Arrowhead600 (6223700) > Lovell700 (7338024) > Reed800 (8052200)

---

## Domain Terminology

| Term | Meaning |
|------|---------|
| **Rootstock (RSK)** | Smart contract platform secured by Bitcoin's hash power via merged mining |
| **Merged Mining** | Mining RSK blocks simultaneously with Bitcoin blocks, sharing PoW |
| **2-Way Peg** | Mechanism to transfer BTC between Bitcoin and RSK (lock on one chain, release on the other) |
| **Peg-in** | Moving BTC from Bitcoin to RSK (locking BTC, minting RBTC) |
| **Peg-out** | Moving RBTC from RSK back to Bitcoin (burning RBTC, releasing BTC) |
| **RBTC** | RSK's native currency, pegged 1:1 to BTC |
| **Bridge** | Precompiled contract managing the Bitcoin-RSK 2-way peg |
| **Federation** | Group of signers that collectively control the multisig holding pegged BTC |
| **PowPeg** | Proof-of-Work secured peg; federation keys held in HSMs that enforce consensus |
| **Flyover** | Fast peg-in protocol allowing near-instant BTC-to-RSK transfers via liquidity providers |
| **REMASC** | Reward Manager Smart Contract - distributes mining rewards (miners, federation, RSK labs) |
| **RSKIP** | RSK Improvement Proposal - consensus rules that gate feature activation per block height |
| **ActivationConfig** | Determines which RSKIPs are active at a given block number |
| **Trie** | Custom immutable binary trie used for blockchain state storage |
| **TrieStore** | Persistence layer for the state trie, backed by RocksDB/LevelDB |
| **RskContext** | Central dependency container that wires all node components |
| **Precompiled Contract** | Native-code smart contract at a fixed address (Bridge, Remasc, etc.) |
| **Gas** | Unit measuring computational effort for EVM execution |
| **Locking Cap** | Maximum BTC that can be locked in the bridge at any time |
| **Whitelist** | Approved BTC addresses allowed to perform peg-ins |
| **UTXO** | Unspent Transaction Output (Bitcoin's transaction model) |
| **SPV Proof** | Simplified Payment Verification - proving a BTC tx is in a block without full node |
| **Coinbase** | Special transaction in a block that creates new coins (mining reward) |
| **Uncle Block** | Valid block not in the main chain; RSK rewards uncle miners via REMASC |
| **Snap Sync** | Experimental sync mode that downloads state snapshots instead of replaying all blocks |
| **Best Block** | The block at the tip of the current best chain |
| **Genesis Block** | The first block in the chain, configured per network |
| **RLP** | Recursive Length Prefix - encoding format for Ethereum data structures |
| **DevP2P** | Peer-to-peer protocol for Ethereum-compatible node communication |

---

## Development Workflows

### Initial Setup

```bash
# Clone and configure
git clone https://github.com/rsksmart/rskj.git
cd rskj

# Build the project (produces JAR + test reports)
./gradlew build -x test    # Skip tests for faster build

# Full build with tests
./gradlew build
```

### Build Commands

| Command | Purpose |
|---------|---------|
| `./gradlew build` | Full build with unit tests |
| `./gradlew build -x test` | Build without tests |
| `./gradlew test` | Run unit tests only |
| `./gradlew parallelTest` | Run tests in parallel (faster, uses half CPU cores) |
| `./gradlew integrationTest` | Run integration tests |
| `./gradlew fatJar` | Build fat JAR with all dependencies (`-all` classifier) |
| `./gradlew jacocoTestReport` | Generate code coverage report |
| `./gradlew checkstyleMain` | Run checkstyle on main sources |
| `./gradlew checkstyleAll` | Run checkstyle on all source sets |
| `./gradlew spotlessJavaCheck` | Check code formatting |
| `./gradlew spotlessJavaApply` | Auto-fix code formatting |
| `./gradlew -PfilePath=path/File.java checkstyleFile` | Run checkstyle on specific file(s) |

### Running a Local Node

```bash
# Build the fat JAR first
./gradlew fatJar

# Run on regtest (local development)
java -cp rskj-core/build/libs/rskj-core-*-all.jar co.rsk.Start --regtest

# Run on testnet
java -cp rskj-core/build/libs/rskj-core-*-all.jar co.rsk.Start --testnet

# Run on mainnet
java -cp rskj-core/build/libs/rskj-core-*-all.jar co.rsk.Start --main
```

**Default JVM args:** `-server -Xss32m -Xms3G -Xmx5G -XX:+UseCompressedOops -XX:-OmitStackTraceInFastThrow`

### CLI Tools

Available under `co.rsk.cli.tools`:

| Tool | Purpose |
|------|---------|
| `ExportBlocks` | Export blocks from a range to file |
| `ImportBlocks` | Import blocks from file |
| `ConnectBlocks` | Connect previously disconnected blocks |
| `ExecuteBlocks` | Re-execute blocks for verification |
| `DbMigrate` | Migrate between LevelDB and RocksDB |
| `ExportState` / `ImportState` | Export/import blockchain state |
| `ShowStateInfo` | Display state info for a block |
| `RewindBlocks` | Rewind state to a specific block or find inconsistencies |
| `IndexBlooms` | Index Bloom filters for log queries |
| `GenerateOpenRpcDoc` | Generate OpenRPC API documentation |
| `StartBootstrap` | Run a dedicated bootstrap/discovery node |

### Running Tests

```bash
# Single test class
./gradlew test --tests "co.rsk.blockchain.BlockchainTest"

# Single test method
./gradlew test --tests "co.rsk.blockchain.BlockchainTest.addFirstBlock"

# Tests matching a pattern
./gradlew test --tests "*Bridge*"

# Parallel execution (faster for full suite)
./gradlew parallelTest

# With fail-fast (stop on first failure)
./gradlew parallelTest -PfailFast

# Integration tests
./gradlew integrationTest

# JMH benchmarks
./gradlew jmh -PjmhArgs="-wi 5 -i 5 -f 1 co.rsk.jmh.BenchmarkClass"
```

**Test JVM settings:** `-Xss32m -Xmx4G` (unit), `-Xss64m -Xmx4G` (parallel)

---

## Important Files for Each Task Type

### Adding/Modifying an RPC Method

1. Module class: `co/rsk/rpc/modules/<namespace>/<Namespace>Module.java`
2. Web3 facade: `co/rsk/rpc/Web3RskImpl.java`
3. Module wiring: `co/rsk/RskContext.java`
4. Module config: `co/rsk/rpc/ModuleDescription.java`

### Modifying the Bridge/Peg

1. Bridge contract: `co/rsk/peg/Bridge.java`
2. Core logic: `co/rsk/peg/BridgeSupport.java`
3. Federation: `co/rsk/peg/federation/Federation.java`
4. Constants: `co/rsk/peg/BridgeConstants.java` (and network-specific subclasses)
5. Storage: `co/rsk/peg/BridgeStorageProvider.java`
6. Tests: `src/test/java/co/rsk/peg/`

### Adding a New Consensus Rule (RSKIP)

1. Define activation: `co/rsk/core/bc/ConsensusRule.java`
2. Config mapping: `reference.conf` (under `blockchain.config.consensusRules`)
3. Network activation heights: `resources/config/{main,testnet,regtest}.conf`
4. Guard with: `activations.isActive(ConsensusRule.RSKIPXXX)`

### Modifying the EVM

1. VM implementation: `org/ethereum/vm/VM.java`
2. Program execution: `org/ethereum/vm/program/Program.java`
3. Gas costs: `org/ethereum/vm/GasCost.java`
4. Opcodes: `org/ethereum/vm/OpCode.java`
5. Precompiled contracts: `org/ethereum/vm/PrecompiledContracts.java`

### Adding a New Precompiled Contract

1. Register address: `org/ethereum/vm/PrecompiledContracts.java`
2. Implement contract class extending `PrecompiledContract`
3. Gate behind RSKIP activation
4. Add tests in `src/test/java/`

### Modifying Storage/Database

1. Interface: `org/ethereum/datasource/KeyValueDataSource.java`
2. RocksDB impl: `org/ethereum/datasource/RocksDbDataSource.java`
3. LevelDB impl: `org/ethereum/datasource/LevelDbDataSource.java`
4. Cache layer: `org/ethereum/datasource/DataSourceWithCache.java`
5. Trie: `co/rsk/trie/Trie.java`, `co/rsk/trie/TrieStore.java`

### Modifying Configuration

1. Defaults: `rskj-core/src/main/resources/reference.conf`
2. Network overrides: `rskj-core/src/main/resources/config/{main,testnet,regtest,devnet}.conf`
3. Properties class: `co/rsk/config/RskSystemProperties.java`
4. Config loader: `co/rsk/config/ConfigLoader.java`
5. Verification: `rskj-core/src/main/resources/expected.conf`

---

## CI/CD Pipelines

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build_and_test.yml` | Push to master/*-rc, all PRs | Build, test, quality checks |
| `lint-java-code.yml` | PR opened/synced | Checkstyle + Spotless validation |
| `rit.yml` | Daily, PRs to master, manual | Rootstock Integration Tests (45 min timeout) |
| `fuzz-test.yml` | Scheduled, push to master | Jazzer fuzz testing |
| `codeql.yml` | Daily, push to master, PRs | Static security analysis (Java, Python) |
| `dependency-review.yml` | PRs | Dependency vulnerability checks |
| `docker-*.yml` | Releases, master push | Docker image build and publish |
| `scorecard.yml` | - | OpenSSF security scorecard |

---

## Code Style

- **Checkstyle** enforces basic formatting (UTF-8, newline at EOF)
- **Spotless** enforces end-of-file newlines on changed files
- Prefer `private final` fields with constructor injection
- Prefer `Optional<T>` over `null`; annotate `@Nullable` when null is possible
- Use `Objects.requireNonNull()` for null precondition checks
- All control structures require curly braces
- Delete unused code (no `@VisibleForTesting`, no commented-out code)
- Avoid merge commits; rebase on master
- IntelliJ IDEA code style files available in the repo
- Separate changes into meaningful commits that each compile and ideally pass tests

---

## Environment and Ports

### Default RPC Configuration

| Protocol | Port | Default Binding |
|----------|------|-----------------|
| HTTP JSON-RPC | 4444 | localhost |
| WebSocket JSON-RPC | 4445 | localhost |
| P2P (Mainnet) | 5050 | all interfaces |
| P2P (Testnet) | 50505 | all interfaces |
| P2P (Regtest) | 50501 | all interfaces |

### Key RPC Config Options (reference.conf)

| Option | Default | Description |
|--------|---------|-------------|
| `rpc.providers.web.http.enabled` | true | Enable HTTP RPC |
| `rpc.providers.web.ws.enabled` | true | Enable WebSocket RPC |
| `rpc.modules` | eth,net,rpc,web3,evm,txpool,personal | Enabled RPC modules |
| `rpc.callGasCap` | 50000000 | Max gas for eth_call |
| `rpc.maxBatchRequestsSize` | 100 | Max batch request size |
