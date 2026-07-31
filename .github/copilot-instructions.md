# Copilot custom instructions for RSKj

Trust these instructions. If a step is missing here or appears wrong, fall back to a focused repo search; do not perform a broad exploration before consulting this document.

For repo summary, versioning, build/test/run commands, project/package layout, and
sensitive/consensus-critical areas, see `../AGENT.md` — that file is the canonical
orientation doc for any AI agent working in this repository. This file adds
Copilot-specific PR-review guidance on top of it.

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

Java style conventions are defined in `./CONTRIBUTING.md`. Key reviewer-facing rules: prefer constructor injection with `private final` fields and `Objects.requireNonNull` on parameters; prefer `Optional<T>` over `null` (annotate nullable returns with `@Nullable`); always brace control structures; treat `@VisibleForTesting` as a design smell and flag accordingly. Standard Java naming applies: lowercase packages, `UpperCamelCase` classes, `lowerCamelCase` members, `CONSTANT_CASE` for static final immutable constants. Broader design guidance is in `./coding-principles.md` (see `../AGENT.md` for the highlights most relevant to an agent's own output).

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

For containerized/reproducible-build notes, project layout, and sensitive/consensus-critical areas, see `../AGENT.md`.

## Trust these instructions

This document, together with `../AGENT.md`, is the canonical reference for working in this repository. Trust them. Search the repo only when a step is missing from both or has been proven wrong by direct observation.

One scoping caveat: the "trust, don't explore" bias optimizes authoring and CI prediction. For **review correctness specifically, the bias flips** — a claim that existing code is defective, will fail, or is non-idiomatic must be backed by direct observation (an actual failing case, the real file format, or a contradicting in-repo usage), never inferred from this document's silence on a topic.
