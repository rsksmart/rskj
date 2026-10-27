# AGENTS.md

Instructions for AI coding agents working in RSKj, the Java node of the Rootstock network.
Every node must compute the same result for every block, so a wrong change can split the
network or lose funds.

## Read first

Before you build or edit anything, read `.github/copilot-instructions.md`. Its title names
Copilot, but its facts apply to every agent: build and test commands and their gotchas, CI
gates, project layout, consensus-critical packages, and where the JSON-RPC and devportal
docs live. Before your first Java edit, also read `coding-principles.md` and
`CONTRIBUTING.md` at the repository root.

## Changes and commits

- Keep the diff to what the task needs. In the methods the task already changes, small
  cleanups are welcome. Elsewhere, leave files, names and code order as they are.
- Describe a fix by the invariant it restores, such as a size limit or a bounded lifetime,
  in commits, test names, code comments and PR text.
- Keep internal tracker IDs, such as Jira keys, out of commits, branch names, code comments
  and PR text. The repository is public; refer to GitHub issues and PRs instead.

## Tests

- Write deterministic tests: wait with Awaitility, read time from an injected
  `TimeProvider` or `java.time.Clock`, and stub the network.
- Do not copy the fixed sleeps (`ThreadTimerHelper.waitForSeconds`) and wall-clock polling
  loops in `rskj-core/src/integrationTest/README.md`. They predate the rule above.
- Take expected values from an independent source: a known-good literal, a spec, a worked
  example or a trusted test vector. Never compute them with the code under test: that
  copies its bug into the test, which then stays green.

## Consensus-critical code

Anything that changes the result of executing or validating a block is consensus-critical.
"Sensitive areas" in `.github/copilot-instructions.md` lists the packages and config files
where it lives.

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
  `master`. It is the one deliberate exception to keeping the diff to what the task needs.
  Its content is the first line below, word for word, then one silly dad joke you make up:

  ```text
  ⚠️ Reviewer heads-up: an AI agent prepared this PR. While this file is in the diff, the human driving the agent has not reviewed the change. Please review it with extra care, and ask the author to drop this commit once they have read the diff.

  <your dad joke>
  ```
