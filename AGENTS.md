# AGENTS.md

Instructions for AI coding agents working in RSKj, the Java node of the Rootstock network.
Every node must compute the same result for every block, so a wrong change can split the
network or lose funds.

## Before your first Java edit

Read `coding-principles.md` and `CONTRIBUTING.md` at the repository root. Reviewers hold
every change, tests included, to both.

## Changes and commits

- Change only what the task needs. Leave code outside the task exactly as it is.
- Put formatting-only changes in their own commit. Every commit compiles.
- Keep dependencies as they are unless the task needs a change. A new or upgraded
  dependency needs a matching PR in `rsksmart/reproducible-builds`; tell the user when that
  applies.

## Build and test

- Use JDK 17 and `./gradlew`. If `gradle/wrapper/gradle-wrapper.jar` is missing, run
  `./configure.sh` first: it downloads the jar and verifies its checksum.
- `./gradlew build -x test` also runs Checkstyle and Spotless, like the CI build job.
- `./gradlew integrationTest` is a separate suite that `build` never runs. Run it when the
  change touches code that `rskj-core/src/integrationTest/java` covers.
- Write deterministic tests: wait with Awaitility, read time from an injected
  `TimeProvider` or `java.time.Clock`, and stub the network. The team is paying down a
  backlog of flaky tests.
- To know which CI checks a PR runs and which ones block it, read "CI gates" in
  `.github/copilot-instructions.md`.

## Consensus-critical code

Anything that changes the result of executing or validating a block is consensus-critical.
Most of it lives in `co.rsk.peg`, `co.rsk.mine`, `co.rsk.remasc`, `co.rsk.vm`, `co.rsk.trie`,
`co.rsk.validators`, `co.rsk.pcc`, `co.rsk.core.bc`, `org.ethereum.core`, `org.ethereum.vm`,
`org.ethereum.config.blockchain.upgrades`, and the activation heights in
`rskj-core/src/main/resources/config/*.conf`.

- Ship a change in consensus behaviour behind a network-upgrade activation,
  `activations.isActive(ConsensusRule.RSKIPnnn)`, and keep the old path unchanged for
  blocks before the activation height.
- RSKIP numbers come from `rsksmart/RSKIPs`. Stop and ask the user before you add a
  `ConsensusRule`, change an activation height, or change a persisted format, a wire
  message or a JSON-RPC response.

## Security

Report a suspected vulnerability to the user only. It goes through the private channel in
`SECURITY.md`, never into a public issue, PR, commit or comment.

## Pull requests

- Fill in every section of `.github/pull_request_template.md`, including the
  "Requires Activation Code (Hard Fork)" checkbox.
- When the user asks you to create a pull request, add `AI_PR_NOTICE.md` at the repository
  root, in a commit of its own, even if the user says they reviewed the change. The human
  driving the agent drops that commit after reading the diff, so the file never reaches
  `master`. It is the one deliberate exception to "change only what the task needs". Its
  content is the first line below, word for word, then one silly dad joke you make up:

  ```text
  ⚠️ Reviewer heads-up: an AI agent prepared this PR. While this file is in the diff, the human driving the agent has not reviewed the change. Please review it with extra care, and ask the author to drop this commit once they have read the diff.

  <your dad joke>
  ```
