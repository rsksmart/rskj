# Architecture Plan: STORY-004

## Remove contractAddress parameter from bridge-related classes

**Story:** STORY-004
**Author:** Architect Agent
**Date:** 2026-02-11
**Status:** Approved

---

## 1. Summary

This story removes the `RskAddress contractAddress` parameter from the constructors of `BridgeStorageProvider`, `RepositoryBtcBlockStoreWithCache`, and `Bridge`, as well as from the `BridgeSupportFactory.newInstance()` method. Each class will instead hardcode the bridge address internally using `PrecompiledContracts.BRIDGE_ADDR`, following the pattern already established in `BridgeStorageAccessorImpl`. This is a pure refactoring with zero behavioral change -- every affected class already receives `BRIDGE_ADDR` at every call site.

### Key Decisions
- Each refactored class will use `PrecompiledContracts.BRIDGE_ADDR` directly wherever the bridge address is needed, without redeclaring a local constant.
- `Bridge` will set the inherited `PrecompiledContract.contractAddress` field to `PrecompiledContracts.BRIDGE_ADDR` in its constructor body (the parent class has a `public RskAddress contractAddress` field that is not read by any external framework code, but should still be set for safety and consistency with other precompiled contracts like `RemascContract`).
- `BridgeSupportFactory.newInstance()` will have its `RskAddress contractAddress` parameter removed entirely, since the downstream classes no longer need it.
- The work is split into 4 phases, ordered so each phase produces a compilable, testable codebase.

---

## 2. Requirements Breakdown

| AC | Requirement | Implementation Approach |
|----|-------------|------------------------|
| AC-1 | `BridgeStorageProvider` no longer receives `contractAddress` as a constructor parameter | Remove parameter from constructor; replace all internal `this.contractAddress` references with `PrecompiledContracts.BRIDGE_ADDR` |
| AC-2 | `RepositoryBtcBlockStoreWithCache` no longer receives `contractAddress` as a constructor parameter | Remove parameter from both constructors; replace internal usages with `PrecompiledContracts.BRIDGE_ADDR`; update `Factory` inner class to stop passing address |
| AC-3 | `Bridge` no longer receives `contractAddress` as a constructor parameter | Remove parameter from both constructors; set `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` in constructor body (sets the inherited `PrecompiledContract.contractAddress` field) |
| AC-4 | `BridgeSupportFactory.newInstance()` no longer receives or passes `contractAddress` | Remove `RskAddress contractAddress` parameter from `newInstance()`; remove the argument when constructing `BridgeStorageProvider` |
| AC-5 | All call sites updated | Update `PrecompiledContracts.getContractForAddress()`, `Bridge.init()`, `EthModule.bridgeState()`, and all test call sites |
| AC-6 | All tests compile and pass | Update ~208 `new BridgeStorageProvider(` calls across 21 test files, ~4 `new RepositoryBtcBlockStoreWithCache(` calls across 3 test files, ~111 `new Bridge(` calls across 6 test files, and mock stubs for `BridgeSupportFactory.newInstance()` |
| AC-7 | No behavioral change | Verified by existing test suite passing unchanged (only constructor signatures change, runtime address remains `BRIDGE_ADDR`) |

---

## 3. Affected Components

### Main Source Files

| File | Action | Description |
|------|--------|-------------|
| `rskj-core/src/main/java/co/rsk/peg/BridgeStorageProvider.java` | Modify | Remove `contractAddress` constructor parameter; replace all `this.contractAddress` usages with `PrecompiledContracts.BRIDGE_ADDR` |
| `rskj-core/src/main/java/co/rsk/peg/RepositoryBtcBlockStoreWithCache.java` | Modify | Remove `contractAddress` from both constructors; replace usages with `PrecompiledContracts.BRIDGE_ADDR`; update `Factory.newInstance()` to stop passing address |
| `rskj-core/src/main/java/co/rsk/peg/Bridge.java` | Modify | Remove `contractAddress` from both constructors; set inherited field in constructor body; stop passing `contractAddress` to `bridgeSupportFactory.newInstance()` |
| `rskj-core/src/main/java/co/rsk/peg/BridgeSupportFactory.java` | Modify | Remove `RskAddress contractAddress` parameter from `newInstance()`; stop passing it to `BridgeStorageProvider` constructor |
| `rskj-core/src/main/java/org/ethereum/vm/PrecompiledContracts.java` | Modify | Update `new Bridge(...)` call in `getContractForAddress()` to remove the `BRIDGE_ADDR` argument |
| `rskj-core/src/main/java/co/rsk/rpc/modules/eth/EthModule.java` | Modify | Update `bridgeSupportFactory.newInstance(...)` call to remove the `PrecompiledContracts.BRIDGE_ADDR` argument |

### Test Source Files

| File | Action | Approx. Changes |
|------|--------|-----------------|
| `rskj-core/src/test/java/co/rsk/peg/BridgeStorageProviderTest.java` | Modify | ~62 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportIT.java` | Modify | ~53 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportTest.java` | Modify | ~47 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportAddSignatureTest.java` | Modify | ~12 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportFlyoverTest.java` | Modify | ~10 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportReleaseBtcTest.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportRegisterBtcTransactionTest.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeSupportSvpTest.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeStateTest.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeStorageProviderPegoutTxIndexTests.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/PegUtilsGetTransactionTypeTest.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/RepositoryBtcBlockStoreWithCacheChainWorkTest.java` | Modify | ~1 `new BridgeStorageProvider(` + ~1 `new RepositoryBtcBlockStoreWithCache(`: remove address args |
| `rskj-core/src/test/java/co/rsk/peg/RepositoryBtcBlockStoreWithCacheTest.java` | Modify | ~1 `new RepositoryBtcBlockStoreWithCache(`: remove address arg |
| `rskj-core/src/test/java/co/rsk/peg/union/UnionBridgeIT.java` | Modify | ~2 `new BridgeStorageProvider(` calls: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/federation/FederationChangeIT.java` | Modify | ~1 `new BridgeStorageProvider(` call: remove 2nd argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeIT.java` | Modify | ~5 `new BridgeStorageProvider(` + ~101 `new Bridge(`: remove address args |
| `rskj-core/src/test/java/co/rsk/peg/BridgeCostsTest.java` | Modify | ~5 `new Bridge(` calls: remove 1st argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeUtilsTest.java` | Modify | ~1 `new Bridge(` call: remove 1st argument |
| `rskj-core/src/test/java/co/rsk/peg/BridgeRSKIP220NewMethodsTest.java` | Modify | ~1 `new Bridge(` + `newInstance` mock stub: remove address args |
| `rskj-core/src/test/java/co/rsk/test/builders/BridgeBuilder.java` | Modify | Remove `contractAddress` field and `contractAddress()` setter; hardcode in `build()`; update `newInstance` mock stub from 4 args to 3 |
| `rskj-core/src/test/java/co/rsk/peg/performance/BridgePerformanceTestCase.java` | Modify | ~1 `new BridgeStorageProvider(` + ~1 `new Bridge(`: remove address args |
| `rskj-core/src/test/java/co/rsk/peg/performance/ReceiveHeadersTest.java` | Modify | ~1 `new BridgeStorageProvider(` + ~1 `new RepositoryBtcBlockStoreWithCache(`: remove address args |
| `rskj-core/src/test/java/co/rsk/peg/performance/ReceiveHeaderTest.java` | Modify | ~1 `new BridgeStorageProvider(`: remove address arg |
| `rskj-core/src/test/java/co/rsk/peg/performance/ReleaseBtcTest.java` | Modify | ~2 `new BridgeStorageProvider(`: remove address args |
| `rskj-core/src/test/java/co/rsk/peg/performance/RegisterBtcCoinbaseTransactionTest.java` | Modify | ~1 `new BridgeStorageProvider(`: remove address arg |
| `rskj-core/src/test/java/co/rsk/peg/performance/RegisterBtcTransactionTest.java` | Modify | ~2 `new BridgeStorageProvider(`: remove address args |

---

## 4. Architecture Decisions

### 4.1 Use `PrecompiledContracts.BRIDGE_ADDR` directly
**Decision:** Each modified class will reference `PrecompiledContracts.BRIDGE_ADDR` directly wherever the bridge address is needed, without redeclaring a local constant.
**Rationale:** `PrecompiledContracts.BRIDGE_ADDR` is already a well-known, centrally defined constant. Redeclaring it as a `private static final` in each class adds unnecessary indirection. Using the constant directly makes it immediately clear where the address comes from and avoids duplication.

### 4.2 Set the inherited `PrecompiledContract.contractAddress` field in `Bridge`
**Decision:** The `Bridge` constructor will set `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` to populate the inherited field from `PrecompiledContract`.
**Rationale:** The `PrecompiledContract` abstract class declares `public RskAddress contractAddress;`. While no external code in the main source tree reads this field from `Bridge` instances, setting it maintains consistency with other precompiled contracts (`RemascContract`, `NativeContract`, `Environment`) that all set this field in their constructors. It also prevents any subtle future bugs if the field were ever read. We do **not** need to call `super(contractAddress)` -- directly assigning the field is equivalent and more explicit.

### 4.3 Phase ordering: Foundation classes first, then dependents
**Decision:** Refactor in bottom-up order: `BridgeStorageProvider` (Phase 1) -> `RepositoryBtcBlockStoreWithCache` (Phase 2) -> `BridgeSupportFactory` + `Bridge` + call sites (Phase 3).
**Rationale:** `BridgeSupportFactory` depends on `BridgeStorageProvider`. `Bridge` depends on `BridgeSupportFactory`. By refactoring from the bottom up, each phase produces compilable code. Also, the `RepositoryBtcBlockStoreWithCache.Factory` already hardcodes the address, so Phase 2 only removes an unnecessary parameter pass-through.

### 4.4 Remove `contractAddress()` from `BridgeBuilder` test helper
**Decision:** Remove the `contractAddress` field and `contractAddress(RskAddress)` setter from `BridgeBuilder`, since `Bridge` will no longer accept that parameter.
**Rationale:** `BridgeBuilder` defaults to `PrecompiledContracts.BRIDGE_ADDR` (line 38) and the setter is called with `BRIDGE_ADDRESS` in only 2 places in `BridgeIT.java`. Since Bridge now hardcodes the address, the setter becomes pointless. Test code calling `.contractAddress(BRIDGE_ADDRESS)` should simply remove that call.

---

## 5. Implementation Phases

### Phase 1: Remove `contractAddress` from `BridgeStorageProvider`
**Acceptance Criteria Covered:** AC-1, AC-6 (partially), AC-7

**Tests to Write FIRST (TDD - Red):**
- [ ] In `BridgeStorageProviderTest`, verify existing tests compile and pass after changing `new BridgeStorageProvider(repository, address, networkParams, activations)` to `new BridgeStorageProvider(repository, networkParams, activations)`. Before the production code change, these tests will not compile (red).
- [ ] No new behavioral tests are needed -- this is a refactoring. The existing tests ARE the behavioral specification.

**Implementation Steps (TDD - Green):**
- [ ] In `BridgeStorageProvider.java`:
  - Add `import org.ethereum.vm.PrecompiledContracts;`
  - Change constructor signature from `(Repository repository, RskAddress contractAddress, NetworkParameters networkParameters, ActivationConfig.ForBlock activations)` to `(Repository repository, NetworkParameters networkParameters, ActivationConfig.ForBlock activations)`
  - Remove `this.contractAddress = contractAddress;` from constructor body
  - Remove the `private final RskAddress contractAddress;` field declaration entirely
  - Replace all remaining `contractAddress` references in the class body with `PrecompiledContracts.BRIDGE_ADDR` (approximately 8 usages in `repository.getStorageBytes(contractAddress, ...)` and `repository.addStorageBytes(contractAddress, ...)` calls)
- [ ] In `BridgeSupportFactory.java`:
  - Change `new BridgeStorageProvider(repository, contractAddress, networkParameters, activations)` to `new BridgeStorageProvider(repository, networkParameters, activations)` (note: `contractAddress` parameter in `newInstance()` itself is NOT removed yet -- that happens in Phase 3)
- [ ] Update all 21 test files that call `new BridgeStorageProvider(`:
  - `BridgeStorageProviderTest.java` (~62 calls)
  - `BridgeSupportIT.java` (~53 calls)
  - `BridgeSupportTest.java` (~47 calls)
  - `BridgeSupportAddSignatureTest.java` (~12 calls)
  - `BridgeSupportFlyoverTest.java` (~10 calls)
  - `BridgeIT.java` (~5 calls)
  - `BridgeSupportReleaseBtcTest.java` (~1 call)
  - `BridgeSupportRegisterBtcTransactionTest.java` (~1 call)
  - `BridgeSupportSvpTest.java` (~1 call)
  - `BridgeStateTest.java` (~1 call)
  - `BridgeStorageProviderPegoutTxIndexTests.java` (~1 call)
  - `PegUtilsGetTransactionTypeTest.java` (~1 call)
  - `RepositoryBtcBlockStoreWithCacheChainWorkTest.java` (~1 call)
  - `UnionBridgeIT.java` (~2 calls)
  - `FederationChangeIT.java` (~1 call)
  - `BridgePerformanceTestCase.java` (~1 call)
  - `ReceiveHeadersTest.java` (~1 call)
  - `ReceiveHeaderTest.java` (~1 call)
  - `ReleaseBtcTest.java` (~2 calls)
  - `RegisterBtcCoinbaseTransactionTest.java` (~1 call)
  - `RegisterBtcTransactionTest.java` (~2 calls)
  - For each: remove the `contractAddress` / `BRIDGE_ADDR` / `bridgeAddress` / `bridgeContractAddress` argument (the 2nd positional parameter)
  - Clean up any now-unused `contractAddress`/`BRIDGE_ADDR`/`bridgeAddress` local variables or constants in test classes that were solely used for this purpose

**Refactoring (TDD - Refactor):**
- [ ] Run `./gradlew test --tests "*BridgeStorageProvider*"` to verify Phase 1
- [ ] Run `./gradlew test --tests "*BridgeSupport*"` to verify no regressions
- [ ] Run `./gradlew checkstyleMain` and `./gradlew spotlessJavaCheck` on modified files

---

### Phase 2: Remove `contractAddress` from `RepositoryBtcBlockStoreWithCache`
**Acceptance Criteria Covered:** AC-2, AC-6 (partially), AC-7

**Tests to Write FIRST (TDD - Red):**
- [ ] In `RepositoryBtcBlockStoreWithCacheTest` and `RepositoryBtcBlockStoreWithCacheChainWorkTest`, update `new RepositoryBtcBlockStoreWithCache(...)` calls to remove the `contractAddress` parameter. Before the production code change, these tests will not compile (red).

**Implementation Steps (TDD - Green):**
- [ ] In `RepositoryBtcBlockStoreWithCache.java`:
  - Remove `RskAddress contractAddress` from both constructors (the 7-param and 8-param versions)
  - Remove `this.contractAddress = contractAddress;` from the constructor body
  - Remove the `private final RskAddress contractAddress;` field declaration
  - Replace all `contractAddress` usages in the class body with `PrecompiledContracts.BRIDGE_ADDR` (in `put()`, `get()`, `getChainHead()`, `setChainHead()` -- approximately 4 usages of `repository.addStorageBytes(contractAddress, ...)` and `repository.getStorageBytes(contractAddress, ...)`)
  - In the `Factory` inner class:
    - Remove the `private final RskAddress contractAddress;` field (line 359)
    - Remove `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` from the constructor (line 369)
    - In `Factory.newInstance()`, change `new RepositoryBtcBlockStoreWithCache(..., contractAddress, ...)` to `new RepositoryBtcBlockStoreWithCache(...)` removing the `contractAddress` argument
- [ ] Update test files that call `new RepositoryBtcBlockStoreWithCache(` directly:
  - `RepositoryBtcBlockStoreWithCacheTest.java` (~1 call): remove the `PrecompiledContracts.BRIDGE_ADDR` argument (the 4th positional parameter)
  - `RepositoryBtcBlockStoreWithCacheChainWorkTest.java` (~1 call): remove the `BRIDGE_ADDR` argument (the 4th positional parameter)
  - `ReceiveHeadersTest.java` (~1 call): remove the `PrecompiledContracts.BRIDGE_ADDR` argument (the 4th positional parameter)

**Refactoring (TDD - Refactor):**
- [ ] Run `./gradlew test --tests "*RepositoryBtcBlockStore*"` to verify Phase 2
- [ ] Run `./gradlew checkstyleMain` and `./gradlew spotlessJavaCheck` on modified files

---

### Phase 3: Remove `contractAddress` from `BridgeSupportFactory.newInstance()` and `Bridge`
**Acceptance Criteria Covered:** AC-3, AC-4, AC-5, AC-6 (remaining), AC-7

This phase has two sub-parts that must be done together because `Bridge.init()` calls `BridgeSupportFactory.newInstance()`, so changing one signature requires changing the other.

**Tests to Write FIRST (TDD - Red):**
- [ ] In `BridgeIT.java`, `BridgeCostsTest.java`, `BridgeUtilsTest.java`, `BridgeRSKIP220NewMethodsTest.java`: update `new Bridge(...)` calls to remove the `contractAddress` first argument. Before production code change, these will not compile (red).
- [ ] In `BridgeBuilder.java`: remove the `contractAddress` field, `contractAddress()` setter, and update `build()` to call `new Bridge(constants, activationConfig, bridgeSupportFactory, signatureCache)`. Update mock stub from `newInstance(any(), any(), any(), any())` to `newInstance(any(), any(), any())`.

**Implementation Steps (TDD - Green):**
- [ ] In `BridgeSupportFactory.java`:
  - Change `newInstance(Repository repository, Block executionBlock, RskAddress contractAddress, List<LogInfo> logs)` to `newInstance(Repository repository, Block executionBlock, List<LogInfo> logs)` (remove 3rd parameter)
  - The `new BridgeStorageProvider(...)` call inside was already updated in Phase 1
- [ ] In `Bridge.java`:
  - Change the public constructor from `Bridge(RskAddress contractAddress, Constants constants, ActivationConfig activationConfig, BridgeSupportFactory bridgeSupportFactory, SignatureCache signatureCache)` to `Bridge(Constants constants, ActivationConfig activationConfig, BridgeSupportFactory bridgeSupportFactory, SignatureCache signatureCache)`
  - Change the `@VisibleForTesting` constructor similarly (remove `RskAddress contractAddress` first parameter)
  - In the constructor body, change `this.contractAddress = contractAddress;` to `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` (this sets the inherited `PrecompiledContract.contractAddress` field)
  - In `init()`, change `bridgeSupportFactory.newInstance(args.getRepository(), rskExecutionBlock, contractAddress, args.getLogs())` to `bridgeSupportFactory.newInstance(args.getRepository(), rskExecutionBlock, args.getLogs())`
- [ ] In `PrecompiledContracts.java`:
  - Change `new Bridge(BRIDGE_ADDR, config.getNetworkConstants(), config.getActivationConfig(), bridgeSupportFactory, signatureCache)` to `new Bridge(config.getNetworkConstants(), config.getActivationConfig(), bridgeSupportFactory, signatureCache)` (around line 196-200)
- [ ] In `EthModule.java`:
  - Change `bridgeSupportFactory.newInstance(track, bestBlock, PrecompiledContracts.BRIDGE_ADDR, null)` to `bridgeSupportFactory.newInstance(track, bestBlock, null)` (around line 140-141)
- [ ] Update test files:
  - `BridgeIT.java`: ~101 `new Bridge(BRIDGE_ADDRESS, ...)` calls: remove first argument. Also update ~1 mock stub `when(bridgeSupportFactory.newInstance(any(), any(), any(), any()))` to `when(bridgeSupportFactory.newInstance(any(), any(), any()))`
  - `BridgeCostsTest.java`: ~5 `new Bridge(PrecompiledContracts.BRIDGE_ADDR, ...)` calls: remove first argument
  - `BridgeUtilsTest.java`: ~1 `new Bridge(PrecompiledContracts.BRIDGE_ADDR, ...)` call: remove first argument
  - `BridgeRSKIP220NewMethodsTest.java`: ~1 `new Bridge(...)` call: remove first argument. Update mock stub from 4 args to 3.
  - `BridgePerformanceTestCase.java`: ~1 `new Bridge(PrecompiledContracts.BRIDGE_ADDR, ...)` call: remove first argument
  - `BridgeBuilder.java`: remove `contractAddress` field and `contractAddress()` setter; update `new Bridge(...)` call to remove first argument; update mock stub from `newInstance(any(), any(), any(), any())` to `newInstance(any(), any(), any())`
  - `BridgeIT.java`: remove calls to `bridgeBuilder.contractAddress(BRIDGE_ADDRESS)` (~2 occurrences)

**Refactoring (TDD - Refactor):**
- [ ] Run `./gradlew test --tests "*Bridge*"` to verify Phase 3
- [ ] Run full test suite: `./gradlew test`
- [ ] Run `./gradlew checkstyleMain` and `./gradlew spotlessJavaCheck`

---

### Phase 4: Final Cleanup and Verification
**Acceptance Criteria Covered:** AC-5, AC-6, AC-7 (full verification)

**Steps:**
- [ ] Search the entire codebase for any remaining references to the old constructor signatures (grep for patterns that suggest missed call sites)
- [ ] Search for any now-unused imports of `RskAddress` in files that were only importing it for the `contractAddress` parameter
- [ ] Search for any now-unused local variables or constants that held the bridge address purely for passing to these constructors (e.g., `bridgeAddress`, `bridgeContractAddress`, `BRIDGE_ADDRESS` in test classes) -- remove if unused
- [ ] Run the full build: `./gradlew build`
- [ ] Run integration tests: `./gradlew integrationTest`
- [ ] Run checkstyle: `./gradlew checkstyleMain`
- [ ] Run spotless: `./gradlew spotlessJavaCheck`
- [ ] Verify no behavioral changes by confirming all tests pass with identical assertions

---

## 6. Testing Strategy

| Phase | Test Focus | Coverage Target |
|-------|------------|-----------------|
| Phase 1 | `BridgeStorageProvider` construction and all repository read/write operations | All 62 tests in `BridgeStorageProviderTest`, all `BridgeSupport*` test classes that construct a `BridgeStorageProvider` |
| Phase 2 | `RepositoryBtcBlockStoreWithCache` block storage operations (put, get, getChainHead, setChainHead) | `RepositoryBtcBlockStoreWithCacheTest`, `RepositoryBtcBlockStoreWithCacheChainWorkTest`, `ReceiveHeadersTest` |
| Phase 3 | `Bridge` initialization and method dispatch; `BridgeSupportFactory` wiring | All 101 tests in `BridgeIT`, `BridgeCostsTest`, `BridgeUtilsTest`, `BridgeRSKIP220NewMethodsTest`, `BridgePerformanceTestCase` |
| Phase 4 | Full regression | Complete `./gradlew build` and `./gradlew integrationTest` |

**Note:** No new unit tests are required for this story. The existing test suite comprehensively covers the behavior of all affected classes. The refactoring only changes constructor signatures; the runtime behavior (which address is used) remains identical. The validation that no behavior changed is that all existing tests pass.

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Missed call site causes compilation failure | Low | Low | Systematic grep-based search for all constructor call patterns; Phase 4 full build verification |
| Test file uses a non-`BRIDGE_ADDR` value for `contractAddress` (exposing a hidden dependency) | Very Low | Medium | Verified by searching: all test call sites pass either `PrecompiledContracts.BRIDGE_ADDR`, `BRIDGE_ADDRESS` (which is typically `BRIDGE_ADDR`), or `bridgeAddress`/`bridgeContractAddress` (which are set to `BRIDGE_ADDR`). No test intentionally passes a different address. |
| `PrecompiledContract.contractAddress` field is read by external code we missed | Very Low | High | Searched all main and test source trees for `.contractAddress` field access patterns. The field is only written (in constructors) and used within `Bridge.init()` to pass to `BridgeSupportFactory`. After refactoring, the field is still set to the correct value. |
| Merge conflicts with concurrent PRs touching the same files | Medium | Low | The changes are mechanical (removing a parameter). Conflicts would be easy to resolve. Coordinate with team. |
| Performance regression | None | N/A | No performance-affecting changes. Only constructor parameter removal. |

---

## 8. Detailed Change Specification

### 8.1 `BridgeStorageProvider.java` Changes

**Before:**
```java
private final RskAddress contractAddress;

public BridgeStorageProvider(
    Repository repository,
    RskAddress contractAddress,
    NetworkParameters networkParameters,
    ActivationConfig.ForBlock activations) {
    this.repository = repository;
    this.contractAddress = contractAddress;
    this.networkParameters = networkParameters;
    this.activations = activations;
}
```

**After:**
```java
public BridgeStorageProvider(
    Repository repository,
    NetworkParameters networkParameters,
    ActivationConfig.ForBlock activations) {
    this.repository = repository;
    this.networkParameters = networkParameters;
    this.activations = activations;
}
```

All internal usages of `contractAddress` become `PrecompiledContracts.BRIDGE_ADDR`.

### 8.2 `RepositoryBtcBlockStoreWithCache.java` Changes

**Before (both constructors):**
```java
private final RskAddress contractAddress;

public RepositoryBtcBlockStoreWithCache(
    NetworkParameters btcNetworkParams,
    Repository repository,
    Map<Sha256Hash, StoredBlock> cacheBlocks,
    RskAddress contractAddress,
    BridgeConstants bridgeConstants,
    BridgeStorageProvider bridgeStorageProvider,
    ForBlock activations) { ... }
```

**After:**
```java
public RepositoryBtcBlockStoreWithCache(
    NetworkParameters btcNetworkParams,
    Repository repository,
    Map<Sha256Hash, StoredBlock> cacheBlocks,
    BridgeConstants bridgeConstants,
    BridgeStorageProvider bridgeStorageProvider,
    ForBlock activations) { ... }
```

All internal usages of `contractAddress` become `PrecompiledContracts.BRIDGE_ADDR`. The `Factory` inner class also removes its `contractAddress` field.

### 8.3 `Bridge.java` Changes

**Before:**
```java
public Bridge(
    RskAddress contractAddress,
    Constants constants,
    ActivationConfig activationConfig,
    BridgeSupportFactory bridgeSupportFactory,
    SignatureCache signatureCache) { ... }
```

**After:**
```java
public Bridge(
    Constants constants,
    ActivationConfig activationConfig,
    BridgeSupportFactory bridgeSupportFactory,
    SignatureCache signatureCache) {
    this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;
    // ... rest unchanged
}
```

### 8.4 `BridgeSupportFactory.java` Changes

**Before:**
```java
public BridgeSupport newInstance(
    Repository repository,
    Block executionBlock,
    RskAddress contractAddress,
    List<LogInfo> logs) { ... }
```

**After:**
```java
public BridgeSupport newInstance(
    Repository repository,
    Block executionBlock,
    List<LogInfo> logs) { ... }
```

---

## Approval

- [x] Plan reviewed by human
- [x] Approved for implementation
