# Workflow Configuration

This file defines how the workflow integrates with external systems and how humans interact with agents.

---

## Human-in-the-Loop (HITL) Integration

Defines how humans receive notifications and provide approvals at each workflow stage.

### Current Configuration

```yaml
hitl:
  type: cli
  description: "Direct CLI interaction with Claude Code"

  notifications:
    story_ready: "Agent outputs message in CLI"
    review_complete: "Agent outputs message in CLI"
    qa_complete: "Agent outputs message in CLI"

  approvals:
    plan_approval: "User types approval in CLI"
    review_approval: "User types approval in CLI"
    merge_approval: "User types approval in CLI"
```

### Future: Slack Integration

```yaml
hitl:
  type: slack
  description: "Slack notifications and approvals"

  config:
    webhook_url: "${SLACK_WORKFLOW_WEBHOOK}"
    channel: "#dev-workflow"
    mention_on_approval: "@dev-team"

  notifications:
    story_ready:
      message: ":memo: Story {story_id} is ready for architecture"
      include_link: true
    review_complete:
      message: ":mag: Code review complete for {story_id}"
      include_summary: true
    qa_complete:
      message: ":white_check_mark: QA passed for {story_id}"
      include_report_link: true

  approvals:
    plan_approval:
      message: ":building_construction: Architecture plan ready for {story_id}"
      buttons: ["Approve", "Request Changes"]
    review_approval:
      message: ":eyes: Review complete for {story_id}"
      buttons: ["Approve", "Request Changes"]
    merge_approval:
      message: ":rocket: {story_id} ready to merge"
      buttons: ["Merge", "Hold"]
```

---

## Story Source Integration

Defines where user stories come from and how they're tracked.

### Current Configuration

```yaml
story_source:
  type: markdown
  description: "Stories defined as markdown files in .workflow/stories/"

  config:
    directory: ".workflow/stories"
    template: ".workflow/STORY_TEMPLATE.md"
    naming: "STORY-{number}.md"

  status_tracking:
    method: "checkboxes"
    location: "in-file"
```

### Future: Jira Integration

```yaml
story_source:
  type: jira
  description: "Stories synced from Jira"

  config:
    base_url: "${JIRA_BASE_URL}"
    project_key: "TON"
    api_token: "${JIRA_API_TOKEN}"

  sync:
    # Pull stories with these labels into .workflow/stories/
    import_filter: "labels = 'ai-workflow' AND status = 'Ready'"

    # Map Jira fields to story template
    field_mapping:
      title: "summary"
      description: "description"
      acceptance_criteria: "customfield_10001"
      priority: "priority.name"

    # Update Jira when workflow stages complete
    status_transitions:
      architecture_complete: "In Development"
      review_complete: "In Review"
      qa_complete: "Ready to Merge"
      merged: "Done"

    # Add comments to Jira with artifact links
    comments:
      plan_approved: "Architecture plan: {plan_link}"
      review_complete: "Code review: {review_link}"
      qa_passed: "QA report: {qa_link}"
```

---

## Validation Gates

Defines what must pass before transitioning between workflow stages.

```yaml
validation_gates:

  before_code_review:
    required:
      - "npm run build passes"
      - "npm run lint passes with 0 errors"
      - "npm test passes"
    recommended:
      - "No TODO comments in new code"
      - "All new functions have JSDoc comments"

  before_qa:
    required:
      - "Code review approved"
      - "All review feedback addressed"
    recommended:
      - "PR created and CI passing"

  before_merge:
    required:
      - "QA report shows PASS"
      - "All acceptance criteria validated"
      - "CI pipeline green"
    recommended:
      - "Documentation updated if needed"
```

---

## Coverage Expectations

Defines expected test coverage by file type (used by Code Review and QA agents).

```yaml
coverage:

  # Shared utilities, pure functions
  utilities:
    patterns: ["**/utils.ts", "**/cliUtils.ts", "**/*Helper.ts"]
    target: 90
    rationale: "Pure functions should be fully testable"

  # Data models and interfaces
  models:
    patterns: ["**/models/**/*.ts"]
    target: null
    rationale: "Interfaces have no runtime code"

  # CLI entry points and main functions
  entry_points:
    patterns: ["**/index.ts", "**/*Reports.ts", "**/report*.ts"]
    target: 60
    rationale: "Integration code is harder to test; focus on unit testing called functions"

  # API and service classes
  services:
    patterns: ["**/api.ts", "**/slack.ts", "**/*Service.ts"]
    target: 80
    rationale: "Core business logic should be well tested"

  # Configuration loaders
  config:
    patterns: ["**/config.ts", "**/cliConfig.ts"]
    target: 70
    rationale: "Config validation should be tested; env loading is integration"

  # Test files themselves
  tests:
    patterns: ["**/tests/**/*.ts", "**/*.test.ts"]
    target: null
    rationale: "Test files are not measured"
```

---

## How to Modify This Configuration

1. **Changing HITL integration**: Update the `hitl` section and ensure agents reference the new notification methods
2. **Changing story source**: Update `story_source` section; may require creating sync scripts
3. **Adjusting validation gates**: Update `validation_gates`; agents will reference these requirements
4. **Adjusting coverage targets**: Update `coverage` section; Code Review and QA agents use these targets
