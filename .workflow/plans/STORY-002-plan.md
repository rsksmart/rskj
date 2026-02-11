# Architecture Plan: STORY-002

## Add vacation report CLI with JSON output

**Story:** STORY-002
**Author:** Architect Agent
**Date:** 2026-02-06
**Status:** Pending Approval

---

## 1. Summary

Create a new CLI command `report-vacations` that generates a hierarchical JSON file containing vacation balances for all employees. The JSON structure organizes employees in a tree where managers contain their direct reports recursively.

### Key Decisions
- Create a new CLI entry point `src/reportVacations.ts`
- Extract shared CLI utilities to `src/cliUtils.ts` to avoid code duplication
- Use argument parsing for `--format` and `--output` flags
- Build hierarchical tree from flat employee list
- Reuse existing `VacationsReport` logic for fetching vacation balances

---

## 2. Requirements Breakdown

| AC | Requirement | Implementation Approach |
|----|-------------|------------------------|
| AC-1 | Existing functionality unaffected | No modifications to existing files except extracting utilities |
| AC-2 | Missing `--format` → fail | Argument validation in CLI entry point |
| AC-3 | Invalid format → fail | Argument validation (only "JSON" accepted) |
| AC-4 | Empty result → valid JSON | Return `{ generatedAt, employees: [] }` |
| AC-5 | `--output` defaults to `./output.json` | Argument parsing with default value |
| AC-6 | Hierarchical JSON | Tree-building algorithm from flat employee list |
| AC-7 | All employees included | No filtering by balance |
| AC-8 | Use existing env vars | Reuse `cliConfig.ts` |
| AC-9 | Resolve named list IDs | Reuse `resolveListIds()` (extract to shared module) |
| AC-10 | Unit tests 80%+ | Test argument parsing, tree building, JSON output |

---

## 3. Affected Components

| File | Action | Description |
|------|--------|-------------|
| `src/reportVacations.ts` | **Create** | New CLI entry point |
| `src/cliUtils.ts` | **Create** | Shared CLI utilities extracted from managerReports.ts |
| `src/models/vacationReport.ts` | **Create** | TypeScript interfaces for JSON output |
| `src/managerReports.ts` | **Modify** | Import shared utilities from cliUtils.ts |
| `src/cliConfig.ts` | **Modify** | Add optional `filterByManager` field |
| `package.json` | **Modify** | Add `report-vacations` npm script |
| `src/tests/reportVacations.test.ts` | **Create** | Unit tests for new CLI |
| `src/tests/cliUtils.test.ts` | **Create** | Unit tests for shared utilities |

---

## 4. Architecture Decisions

### 4.1 Extract Shared Utilities

**Decision:** Create `src/cliUtils.ts` with shared functions.

**Rationale:** Both `managerReports.ts` and `reportVacations.ts` need:
- `resolveListIds()` - Resolve HiBob named list IDs to display names
- `withTimeout()` - Wrap operations with timeout
- Argument parsing helpers

**Impact:** Requires updating `managerReports.ts` to import from the new module (low risk, same functionality).

### 4.2 Tree Building Algorithm

**Decision:** Build hierarchy by:
1. Create a map of email → employee node
2. For each employee, find their manager and add as child
3. Root nodes are employees with no manager (or manager not in dataset)

**Rationale:** More efficient than recursive traversal for large datasets. Single pass O(n).

### 4.3 Vacation Balance Fetching

**Decision:** Adapt logic from `VacationsReport.generate()` but:
- Fetch all employees (no hardcoded filter)
- Support optional `FILTER_BY_MANAGER` env var
- Return structured data instead of building manager reports

**Rationale:** Reuse proven logic while adapting for new output format.

### 4.4 Argument Parsing

**Decision:** Parse `process.argv` manually for `--format=VALUE` and `--output=VALUE` patterns.

**Rationale:**
- Keeps dependencies minimal (no need for `yargs` or `commander`)
- Consistent with existing CLI patterns in the codebase
- Simple enough for 2 arguments

---

## 5. Implementation Steps

### Phase 1: Shared Utilities (Foundation)

- [ ] **Step 1.1:** Create `src/cliUtils.ts`
  - Move `resolveListIds()` from `managerReports.ts`
  - Move `withTimeout()` from `managerReports.ts`
  - Add `parseArgs()` function for `--key=value` parsing

- [ ] **Step 1.2:** Update `src/managerReports.ts`
  - Import `resolveListIds`, `withTimeout` from `cliUtils.ts`
  - Remove local implementations

- [ ] **Step 1.3:** Create `src/tests/cliUtils.test.ts`
  - Move relevant tests from `managerReports.test.ts`
  - Add tests for `parseArgs()`

- [ ] **Step 1.4:** Run tests to verify no regression
  ```bash
  npm test
  ```

### Phase 2: Data Models

- [ ] **Step 2.1:** Create `src/models/vacationReport.ts`
  ```typescript
  export interface VacationReportEmployee {
      name: string;
      email: string;
      team: string;
      position: string;
      balance: number;
      periodEndsAt: string;  // ISO date string
      reports: VacationReportEmployee[];
  }

  export interface VacationReportOutput {
      generatedAt: string;  // ISO timestamp
      employees: VacationReportEmployee[];
  }
  ```

### Phase 3: CLI Implementation

- [ ] **Step 3.1:** Update `src/cliConfig.ts`
  - Add `filterByManager?: string` to `CliConfig` interface
  - Load from `FILTER_BY_MANAGER` env var

- [ ] **Step 3.2:** Create `src/reportVacations.ts`
  - Argument parsing (`--format`, `--output`)
  - Argument validation
  - Config loading
  - Fetch employees and vacation balances
  - Build hierarchical tree
  - Write JSON to file

- [ ] **Step 3.3:** Update `package.json`
  ```json
  "report-vacations": "node dist/reportVacations.js"
  ```

### Phase 4: Testing

- [ ] **Step 4.1:** Create `src/tests/reportVacations.test.ts`
  - Test argument validation (missing format, invalid format)
  - Test tree building algorithm
  - Test JSON output structure
  - Test empty employee list
  - Test file writing (mock fs)

- [ ] **Step 4.2:** Run full test suite and coverage
  ```bash
  npm run test:coverage
  ```

### Phase 5: Verification

- [ ] **Step 5.1:** Build and manual test
  ```bash
  npm run build
  npm run report-vacations -- --format=JSON
  cat output.json | head -50
  ```

- [ ] **Step 5.2:** Test with custom output path
  ```bash
  npm run report-vacations -- --format=JSON --output=./vacations.json
  ```

- [ ] **Step 5.3:** Test error cases
  ```bash
  npm run report-vacations  # Should fail: missing format
  npm run report-vacations -- --format=CSV  # Should fail: invalid format
  ```

---

## 6. Testing Strategy

### Unit Tests

| Test Category | Test Cases |
|---------------|------------|
| Argument Parsing | Missing `--format`, invalid format value, missing `--output` uses default |
| Tree Building | Single employee, flat list, nested hierarchy, circular reference handling |
| JSON Output | Correct structure, ISO date formatting, empty employees array |
| File Writing | Successful write, directory creation if needed |
| Integration | Full flow with mocked API |

### Coverage Targets

| File | Target |
|------|--------|
| `reportVacations.ts` | 80%+ |
| `cliUtils.ts` | 90%+ |
| `models/vacationReport.ts` | 100% (interfaces only) |

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Circular manager references | Low | Medium | Add visited set to prevent infinite loops |
| Large dataset performance | Low | Low | Tree building is O(n), should handle thousands |
| Breaking existing CLI | Low | High | Extract utilities carefully, run full test suite |
| API rate limiting | Medium | Medium | Reuse existing retry logic in Api class |

---

## 8. File Structure After Implementation

```
src/
├── cliConfig.ts          (modified - add filterByManager)
├── cliUtils.ts           (new - shared CLI utilities)
├── managerReports.ts     (modified - import from cliUtils)
├── reportVacations.ts    (new - CLI entry point)
├── models/
│   ├── employee.ts
│   ├── report.ts
│   └── vacationReport.ts (new - JSON output interfaces)
└── tests/
    ├── cliUtils.test.ts       (new)
    ├── reportVacations.test.ts (new)
    └── ... (existing tests)
```

---

## 9. Estimated Complexity

- **Lines of Code:** ~300-400 (new code)
- **Files Changed:** 3 modified, 5 created
- **Estimated Effort:** Medium (1-2 days)

---

## 10. Open Questions

None - all requirements are clear from the user story.

---

## Approval

- [ ] Plan reviewed by human
- [ ] Approved for implementation
- [ ] Approved by: _______________
- [ ] Date: _______________
