# Architecture Plan: STORY-003

## Employee Data Caching for Faster Reports

**Story:** STORY-003
**Author:** Architect Agent
**Date:** 2026-02-06
**Status:** Pending Approval

---

## 1. Summary

Implement a caching layer for employee data to speed up report generation. The cache will store employee information (not vacations) with a configurable TTL (default 30 days). Two invalidation methods will be provided: a CLI command with env var authorization, and a Slack command restricted to an email whitelist.

### Key Decisions
- Use file-based JSON cache (simplest approach, easy to swap later)
- Abstract cache behind an interface for future technology changes
- Create a `CachedEmployeeService` that wraps the API
- Cache both employee data AND named lists (title, department) since they change infrequently
- Separate authorization configs: CLI uses `CACHE_ADMIN=true`, Slack uses `CACHE_ADMIN_EMAILS`

---

## 2. Requirements Breakdown

| AC | Requirement | Implementation Approach |
|----|-------------|------------------------|
| AC-1 | Employee data cached after first fetch | `CachedEmployeeService.getEmployees()` saves to cache |
| AC-2 | Subsequent runs use cached data | Check cache before API call |
| AC-3 | Configurable TTL (default 30 days) | `CACHE_TTL_DAYS` env var, stored in cache metadata |
| AC-4 | Fresh data when cache empty/expired | TTL check in cache read logic |
| AC-5 | Cache abstracted behind interface | `CacheService` interface with `FileCache` implementation |
| AC-6 | CLI command for cache invalidation | `npm run cache:clear` → `src/cacheClear.ts` |
| AC-7 | CLI requires env var authorization | Check `CACHE_ADMIN=true` |
| AC-8 | Slack command for cache invalidation | `/cache-clear` command in SlackService |
| AC-9 | Slack restricted to email whitelist | `CACHE_ADMIN_EMAILS` env var |

---

## 3. Affected Components

| File | Action | Description |
|------|--------|-------------|
| `src/cache/cacheService.ts` | Create | Cache interface definition |
| `src/cache/fileCache.ts` | Create | File-based JSON cache implementation |
| `src/cache/cachedEmployeeService.ts` | Create | Wraps API with caching logic |
| `src/cache/index.ts` | Create | Barrel export for cache module |
| `src/cacheClear.ts` | Create | CLI command for cache invalidation |
| `src/cacheConfig.ts` | Create | Cache-specific configuration |
| `src/managerReports.ts` | Modify | Use CachedEmployeeService |
| `src/vacationsReport.ts` | Modify | Use CachedEmployeeService |
| `src/slack.ts` | Modify | Add cache-clear command support |
| `src/config.ts` | Modify | Add cache admin email list |
| `src/index.ts` | Modify | Wire up Slack cache-clear command |
| `package.json` | Modify | Add `cache:clear` script |
| `src/tests/cache.test.ts` | Create | Tests for cache module |
| `src/tests/cacheClear.test.ts` | Create | Tests for CLI command |

---

## 4. Architecture Decisions

### 4.1 Cache Interface Design

**Decision:** Create a generic `CacheService<T>` interface with get/set/clear methods.

**Rationale:** Allows swapping file cache for Redis/database later without changing consuming code.

```typescript
interface CacheService<T> {
    get(key: string): Promise<T | null>;
    set(key: string, value: T, ttlMs?: number): Promise<void>;
    clear(key?: string): Promise<void>;
    has(key: string): Promise<boolean>;
}
```

### 4.2 Cache Storage Location

**Decision:** Store cache in `.cache/` directory in project root (gitignored).

**Rationale:** Simple file location, easy to inspect/debug, automatically excluded from version control.

### 4.3 CachedEmployeeService Pattern

**Decision:** Create a service that wraps the API and adds caching, rather than modifying the API class.

**Rationale:**
- Keeps `Api` class focused on HTTP concerns
- Follows composition over inheritance
- Easier to test (can mock cache and API separately)
- Follows existing Service pattern in codebase

### 4.4 Dual Authorization Strategy

**Decision:** CLI uses simple env var (`CACHE_ADMIN=true`), Slack uses email whitelist (`CACHE_ADMIN_EMAILS`).

**Rationale:**
- CLI runs locally where env vars are secure
- Slack needs user-level auth since anyone in workspace could try the command
- Reuses existing Slack authorization pattern from `slack.ts`

---

## 5. Implementation Phases

Each phase goes through the complete cycle: Developer (TDD) → Code Review → QA

### Phase 1: Cache Interface & File Implementation

**Acceptance Criteria Covered:** AC-3, AC-4, AC-5

**Tests to Write FIRST (TDD - Red):**
- [ ] `CacheService` interface contract tests
- [ ] `FileCache.get()` returns null when key doesn't exist
- [ ] `FileCache.get()` returns null when TTL expired
- [ ] `FileCache.get()` returns value when valid
- [ ] `FileCache.set()` persists data to file
- [ ] `FileCache.clear()` removes specific key
- [ ] `FileCache.clear()` removes all keys when no key specified
- [ ] `FileCache.has()` returns correct boolean

**Implementation Steps (TDD - Green):**
- [ ] Create `src/cache/` directory
- [ ] Create `src/cache/cacheService.ts` with interface definition
- [ ] Create `src/cache/fileCache.ts` implementing the interface
- [ ] Add `.cache/` to `.gitignore`
- [ ] Create `src/cache/index.ts` barrel export

**Refactoring (TDD - Refactor):**
- [ ] Extract constants (default TTL, cache directory)
- [ ] Add logging for cache operations

### Phase 2: CachedEmployeeService & Integration

**Acceptance Criteria Covered:** AC-1, AC-2

**Tests to Write FIRST (TDD - Red):**
- [ ] `CachedEmployeeService.getEmployees()` returns cached data when available
- [ ] `CachedEmployeeService.getEmployees()` calls API when cache miss
- [ ] `CachedEmployeeService.getEmployees()` saves to cache after API call
- [ ] `CachedEmployeeService.getNamedList()` uses cache
- [ ] Integration: managerReports uses cached data on second run
- [ ] Integration: vacationsReport uses cached employee data (not vacations)

**Implementation Steps (TDD - Green):**
- [ ] Create `src/cache/cachedEmployeeService.ts`
- [ ] Create `src/cacheConfig.ts` with TTL configuration
- [ ] Modify `src/managerReports.ts` to use `CachedEmployeeService`
- [ ] Modify `src/vacationsReport.ts` to use `CachedEmployeeService`
- [ ] Update `src/cliConfig.ts` with cache config options

**Refactoring (TDD - Refactor):**
- [ ] Ensure consistent error handling
- [ ] Add debug logging for cache hits/misses

### Phase 3: CLI Cache Invalidation

**Acceptance Criteria Covered:** AC-6, AC-7

**Tests to Write FIRST (TDD - Red):**
- [ ] CLI exits with error when `CACHE_ADMIN` not set
- [ ] CLI exits with error when `CACHE_ADMIN=false`
- [ ] CLI clears cache when `CACHE_ADMIN=true`
- [ ] CLI outputs success message after clearing
- [ ] CLI handles case when cache already empty

**Implementation Steps (TDD - Green):**
- [ ] Create `src/cacheClear.ts` CLI entry point
- [ ] Add authorization check for `CACHE_ADMIN` env var
- [ ] Implement cache clear logic using `CacheService`
- [ ] Add `"cache:clear": "node dist/cacheClear.js"` to `package.json`

**Refactoring (TDD - Refactor):**
- [ ] Add helpful usage message
- [ ] Consistent error message format

### Phase 4: Slack Cache Invalidation

**Acceptance Criteria Covered:** AC-8, AC-9

**Tests to Write FIRST (TDD - Red):**
- [ ] Slack command rejected when user email not in whitelist
- [ ] Slack command succeeds when user email in whitelist
- [ ] `CACHE_ADMIN_EMAILS` parsed correctly (comma-separated)
- [ ] Slack responds with success message after clearing
- [ ] Slack responds with unauthorized message when rejected

**Implementation Steps (TDD - Green):**
- [ ] Add `CACHE_ADMIN_EMAILS` to `src/config.ts`
- [ ] Add `cacheAdminEmails` to `Config` interface
- [ ] Modify `src/slack.ts` to support multiple commands
- [ ] Add cache-clear command handler
- [ ] Modify `src/index.ts` to wire up cache-clear subscription

**Refactoring (TDD - Refactor):**
- [ ] Extract command handling into cleaner structure
- [ ] Ensure consistent Slack message formatting

---

## 6. Testing Strategy

| Phase | Test Focus | Coverage Target |
|-------|------------|-----------------|
| Phase 1 | Cache interface, file I/O, TTL logic | 90% (utility) |
| Phase 2 | Service integration, cache hit/miss | 80% (service) |
| Phase 3 | CLI authorization, clear functionality | 60% (entry point) |
| Phase 4 | Slack command, email authorization | 80% (existing slack.ts) |

### Coverage Targets
Reference: `.workflow/CONFIG.md`

| File | Type | Target |
|------|------|--------|
| `cache/cacheService.ts` | Interface | N/A |
| `cache/fileCache.ts` | Utility | 90% |
| `cache/cachedEmployeeService.ts` | Service | 80% |
| `cacheClear.ts` | Entry Point | 60% |
| `cacheConfig.ts` | Config | 70% |

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Cache file corruption | Low | Medium | Treat corrupted cache as cache miss, log warning |
| TTL calculation errors | Low | Low | Comprehensive unit tests for TTL logic |
| Slack command conflicts | Low | Medium | Use distinct command name (`/cache-clear`) |
| File permissions issues | Medium | Medium | Document required permissions, fail gracefully |
| Cache grows too large | Low | Low | Single file per cache key, max entries limit |

---

## 8. New Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CACHE_TTL_DAYS` | No | `30` | Cache time-to-live in days |
| `CACHE_ADMIN` | No | `false` | Enable CLI cache admin (set to `true`) |
| `CACHE_ADMIN_EMAILS` | No | `""` | Comma-separated emails for Slack cache admin |
| `CACHE_DIR` | No | `.cache` | Cache storage directory |

---

## 9. File Structure After Implementation

```
src/
├── cache/
│   ├── index.ts              # Barrel export
│   ├── cacheService.ts       # Interface definition
│   ├── fileCache.ts          # File-based implementation
│   └── cachedEmployeeService.ts  # API wrapper with caching
├── cacheClear.ts             # CLI entry point
├── cacheConfig.ts            # Cache configuration
└── tests/
    ├── cache.test.ts         # Cache module tests
    └── cacheClear.test.ts    # CLI tests
```

---

## Approval

- [ ] Plan reviewed by human
- [ ] Approved for implementation
