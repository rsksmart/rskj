# STORY-004: Remove contractAddress parameter from bridge-related classes

## Status
- [x] Draft
- [x] Ready for Development
- [x] In Architecture
- [x] In Development
- [ ] In Review
- [ ] In QA
- [ ] Done

## Description

Some bridge-related classes receive the bridge address (`RskAddress contractAddress`) as a constructor/method parameter, while others correctly hardcode it using `PrecompiledContracts.BRIDGE_ADDR`. Since all these classes are exclusively related to the Bridge precompiled contract, there is no reason to parameterize the address — it is always the same. Receiving it as a parameter creates a risk of inconsistency if a caller passes the wrong address.

This story removes `contractAddress` as a received parameter from all bridge-related classes and replaces it with a direct reference to `PrecompiledContracts.BRIDGE_ADDR`.

## Acceptance Criteria

- [ ] AC-1: `BridgeStorageProvider` no longer receives `RskAddress contractAddress` as a constructor parameter. It uses `PrecompiledContracts.BRIDGE_ADDR` internally (as a `private static final` constant or direct reference).
- [ ] AC-2: `RepositoryBtcBlockStoreWithCache` no longer receives `RskAddress contractAddress` as a constructor parameter. It uses `PrecompiledContracts.BRIDGE_ADDR` internally.
- [ ] AC-3: `Bridge` no longer receives `RskAddress contractAddress` as a constructor parameter. It uses `PrecompiledContracts.BRIDGE_ADDR` internally (note: `Bridge` extends `PrecompiledContract` which has a `contractAddress` field — evaluate whether the parent class field should be set directly).
- [ ] AC-4: `BridgeSupportFactory.newInstance()` no longer receives or passes `contractAddress` to the classes above.
- [ ] AC-5: All call sites that previously passed `contractAddress` to these constructors/methods are updated to use the new signatures.
- [ ] AC-6: All existing tests compile and pass after the refactoring. Test code that constructed these classes with an explicit bridge address is updated accordingly.
- [ ] AC-7: No functional behavior changes — this is a pure refactoring. The bridge address used at runtime remains `PrecompiledContracts.BRIDGE_ADDR` everywhere.

## Technical Notes

### Classes to refactor (receive address as parameter today)
- `co.rsk.peg.Bridge` — constructor receives `RskAddress contractAddress`
- `co.rsk.peg.BridgeStorageProvider` — constructor receives `RskAddress contractAddress`, used for `repository.getStorageBytes(contractAddress, ...)`
- `co.rsk.peg.RepositoryBtcBlockStoreWithCache` — constructor receives `RskAddress contractAddress`, used for `repository.addStorageBytes(contractAddress, ...)`
- `co.rsk.peg.BridgeSupportFactory` — `newInstance()` receives and forwards `contractAddress`

### Classes already following the correct pattern (reference examples)
- `co.rsk.peg.storage.BridgeStorageAccessorImpl` — `private static final RskAddress CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;`
- `co.rsk.peg.utils.BrigeEventLoggerLegacyImpl` — `private static final byte[] BRIDGE_CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR.getBytes();`
- `co.rsk.peg.utils.BridgeEventLoggerImpl` — uses `PrecompiledContracts.BRIDGE_ADDR` inline

### Special considerations
- `Bridge` extends `PrecompiledContracts.PrecompiledContract`, which has an inherited `contractAddress` field. Verify how the parent class uses this field and whether it needs to be set via `super` or can be overridden.
- Many test files construct `BridgeStorageProvider` and `RepositoryBtcBlockStoreWithCache` with an explicit address — these all need updating.
- The `RepositoryBtcBlockStoreWithCache.Factory` inner class already hardcodes `PrecompiledContracts.BRIDGE_ADDR` — this is consistent with the target pattern.

## Priority

- [ ] Critical
- [x] High
- [ ] Medium
- [ ] Low

## Estimated Size

- [ ] S (< 1 day)
- [x] M (1-3 days)
- [ ] L (3-5 days)
- [ ] XL (> 5 days)

---

## Workflow Artifacts

### Architecture Plan
- File: `../plans/STORY-004-plan.md`
- Status: [ ] Pending | [x] Approved | [ ] Rejected

### Code Review
- PR: #[number]
- File: `../reviews/STORY-004-review.md`
- Status: [ ] Pending | [ ] Approved | [ ] Changes Requested

### QA Report
- File: `../qa-reports/STORY-004-qa.md`
- Status: [ ] Pending | [ ] Passed | [ ] Failed
