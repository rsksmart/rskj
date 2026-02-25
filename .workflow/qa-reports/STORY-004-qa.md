# QA Report: STORY-004 - All Phases (Combined)

## Remove contractAddress parameter from bridge-related classes

**QA Agent:** QA Agent
**Date:** 2026-02-11
**Branch:** `feature/STORY-004-remove-bridge-contract-address-param`
**Phase:** All Phases (1-4) - Combined QA

---

## Summary

**Verdict: PASS**

All 7 acceptance criteria are validated. The refactoring correctly removes `RskAddress contractAddress` from `BridgeStorageProvider`, `RepositoryBtcBlockStoreWithCache`, `Bridge`, and `BridgeSupportFactory.newInstance()`. All internal usages are replaced with `PrecompiledContracts.BRIDGE_ADDR`. The full build, lint, and test suite pass. Coverage meets or exceeds targets for all modified files.

---

## Validation Results

### Build

```
./gradlew build -x test
BUILD SUCCESSFUL in 15s
25 actionable tasks: 15 executed, 10 up-to-date
```

**Status: PASS**

### Lint

```
./gradlew checkstyleMain
BUILD SUCCESSFUL in 659ms - 0 errors

./gradlew spotlessJavaCheck
BUILD SUCCESSFUL in 410ms - 0 errors
```

**Status: PASS**

### Tests

```
./gradlew test jacocoTestReport
BUILD SUCCESSFUL in 5m 9s
7 actionable tasks: 2 executed, 5 up-to-date
All tests green - 0 failures
```

**Status: PASS**

### Coverage

```
./gradlew jacocoTestReport
Coverage report: rskj-core/build/reports/jacoco/test/html/index.html
```

---

## Coverage Report

Reference: `.workflow/CONFIG.md`

| File | Type | Instruction Coverage | Branch Coverage | Target | Status |
|------|------|---------------------|-----------------|--------|--------|
| `BridgeStorageProvider.java` | Consensus | 98% | 98% | 85% | **PASS** |
| `RepositoryBtcBlockStoreWithCache.java` | Services | 96% | 88% | 80% | **PASS** |
| `Bridge.java` | Consensus | 93% | 94% | 85% | **PASS** |
| `BridgeSupportFactory.java` | Services | 100% | 100% | 80% | **PASS** |
| `PrecompiledContracts.java` | Consensus | 97% | 91% | 85% | **PASS** |
| `EthModule.java` | Entry Point | 83% | 81% | 60% | **PASS** |

### Coverage Analysis
- All modified files exceed their coverage targets
- `BridgeSupportFactory` achieves 100% coverage on both instructions and branches
- `BridgeStorageProvider` at 98% instruction and 98% branch coverage (well above 85% target)
- `Bridge` at 93% instruction and 94% branch coverage (well above 85% target)
- No coverage regressions from the refactoring

---

## Acceptance Criteria Results

| AC | Description | Status | Evidence |
|----|-------------|--------|----------|
| AC-1 | `BridgeStorageProvider` no longer receives `contractAddress` as constructor parameter | **PASS** | Constructor signature: `(Repository, NetworkParameters, ActivationConfig.ForBlock)`. No `contractAddress` field. `grep contractAddress BridgeStorageProvider.java` returns 0 matches. |
| AC-2 | `RepositoryBtcBlockStoreWithCache` no longer receives `contractAddress` as constructor parameter | **PASS** | Both constructors have no `contractAddress` param. No `contractAddress` field. `grep contractAddress RepositoryBtcBlockStoreWithCache.java` returns 0 matches. |
| AC-3 | `Bridge` no longer receives `contractAddress` as constructor parameter, sets inherited field | **PASS** | Both constructors have no `contractAddress` param. Line 284: `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` |
| AC-4 | `BridgeSupportFactory.newInstance()` no longer receives `contractAddress` | **PASS** | Method signature: `newInstance(Repository, Block, List<LogInfo>)` — 3 params, no `contractAddress` |
| AC-5 | All call sites updated | **PASS** | Grep for old patterns in main sources: 0 matches for `contractAddress` in any of the 4 refactored classes. `new Bridge(` in PrecompiledContracts.java passes 4 args (no address). `newInstance(` in BridgeSupportFactory.java not present externally. |
| AC-6 | All tests compile and pass | **PASS** | `./gradlew test` — BUILD SUCCESSFUL in 5m 9s, all tests green |
| AC-7 | No functional behavior changes | **PASS** | All existing test assertions unchanged. Runtime address remains `PrecompiledContracts.BRIDGE_ADDR` everywhere. Only constructor/method signatures changed. |

---

## Detailed AC Validation

### AC-1: BridgeStorageProvider no longer receives contractAddress

**Validation Method:** Code inspection + grep

**Result:** PASS

**Evidence:**
- `BridgeStorageProvider.java:92-99`: Constructor is `(Repository repository, NetworkParameters networkParameters, ActivationConfig.ForBlock activations)` — no `contractAddress` parameter
- No `private final RskAddress contractAddress;` field exists
- `grep contractAddress BridgeStorageProvider.java` — 0 matches
- All internal usages replaced with `PrecompiledContracts.BRIDGE_ADDR` (import at line 34)

### AC-2: RepositoryBtcBlockStoreWithCache no longer receives contractAddress

**Validation Method:** Code inspection + grep

**Result:** PASS

**Evidence:**
- `RepositoryBtcBlockStoreWithCache.java:66-72`: 6-param constructor with no `contractAddress`
- Second constructor (7-param with `maxDepthBlockCache`) also has no `contractAddress`
- `Factory` inner class no longer has `contractAddress` field
- `grep contractAddress RepositoryBtcBlockStoreWithCache.java` — 0 matches

### AC-3: Bridge no longer receives contractAddress, sets inherited field

**Validation Method:** Code inspection

**Result:** PASS

**Evidence:**
- `Bridge.java:260-273`: Public constructor is `(Constants, ActivationConfig, BridgeSupportFactory, SignatureCache)` — no `contractAddress`
- `Bridge.java:276-290`: `@VisibleForTesting` constructor also has no `contractAddress`
- `Bridge.java:284`: `this.contractAddress = PrecompiledContracts.BRIDGE_ADDR;` — correctly sets inherited `PrecompiledContract.contractAddress` field

### AC-4: BridgeSupportFactory.newInstance() no longer receives contractAddress

**Validation Method:** Code inspection

**Result:** PASS

**Evidence:**
- `BridgeSupportFactory.java:69-72`: `newInstance(Repository repository, Block executionBlock, List<LogInfo> logs)` — 3 params, no `contractAddress`
- `BridgeSupportFactory.java:80-84`: `new BridgeStorageProvider(repository, networkParameters, activations)` — 3 params, no address

### AC-5: All call sites updated

**Validation Method:** Codebase-wide grep

**Result:** PASS

**Evidence:**
- `grep "new BridgeStorageProvider("` in main sources: only 1 match (BridgeSupportFactory.java, correctly 3 params)
- `grep "new Bridge("` in main sources: only 1 match (PrecompiledContracts.java, correctly 4 params without address)
- `grep ".newInstance("` in BridgeSupportFactory: no external matches with `contractAddress`
- `EthModule.java:140-141`: `bridgeSupportFactory.newInstance(track, bestBlock, null)` — correctly 3 params
- All 27+ test files updated (verified by full test compilation and passing)

### AC-6: All tests compile and pass

**Validation Method:** Full test suite execution

**Result:** PASS

**Evidence:**
- `./gradlew test jacocoTestReport` — BUILD SUCCESSFUL in 5m 9s
- 0 test failures
- All test files across 27+ files compile with new constructor signatures

### AC-7: No functional behavior changes

**Validation Method:** Test analysis + code inspection

**Result:** PASS

**Evidence:**
- All test assertions remain unchanged — only constructor call signatures modified
- Runtime bridge address remains `PrecompiledContracts.BRIDGE_ADDR` everywhere
- No logic changes in any source file — only parameter removal and direct constant usage
- Existing comprehensive test suite (100+ Bridge tests, 62+ BridgeStorageProvider tests) validates identical behavior

---

## Integration Check

- [x] Existing tests pass (BUILD SUCCESSFUL, all green)
- [x] Build succeeds (BUILD SUCCESSFUL in 15s)
- [x] No regressions from previous phases
- [x] Checkstyle passes (0 errors)
- [x] Spotless passes (0 errors)

---

## Issues Found

None.

All code review recommendations have been addressed:
1. Unused `RskAddress` import in `BridgeStorageProvider.java` — **Fixed** in Phase 4 commit (`473f1b048`)
2. Extraneous files in Phase 1 commit — **Non-blocking**, documented in review

---

## Verdict

**All Phases: PASS**

All 7 acceptance criteria validated. Build, lint, and full test suite pass. Coverage exceeds targets for all modified files. No issues found.

### Next Step
- [x] **Final phase:** Ready to Merge

---

## Handoff: QA → Merge

**Story:** STORY-004
**Final Phase:** 4 of 4
**QA Report:** `.workflow/qa-reports/STORY-004-qa.md`
**Verdict:** PASS

### Validation Summary
- Compile (`./gradlew build -x test`): **PASS** (BUILD SUCCESSFUL in 15s)
- Checkstyle (`./gradlew checkstyleMain`): **PASS** (0 errors)
- Spotless (`./gradlew spotlessJavaCheck`): **PASS** (0 errors)
- Tests (`./gradlew test`): **PASS** (BUILD SUCCESSFUL in 5m 9s, all green)
- Coverage (`./gradlew jacocoTestReport`): **PASS** (all files exceed targets)

### All Acceptance Criteria
- 7/7 criteria validated across all phases

### Ready to Merge
- [x] All phases completed
- [ ] Human approval received
- [ ] PR created
- [ ] CI passing
