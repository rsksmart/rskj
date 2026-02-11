# STORY-002: Add vacation report CLI with JSON output

## Status
- [x] Draft
- [x] Ready for Development
- [x] In Architecture
- [x] In Development
- [x] In Review
- [x] In QA
- [x] Done

## Description

As a user, I want to be able to run a CLI command that outputs a JSON file with all pending vacations in the company, structured hierarchically by manager relationships.

The new CLI command `report-vacations` will generate a JSON file containing vacation balances for all employees, organized in a tree structure where each manager contains their direct reports (who may themselves have reports, and so on).

### Usage

```bash
npm run build
npm run report-vacations -- --format=JSON
npm run report-vacations -- --format=JSON --output=./vacations.json
```

### JSON Output Structure

```json
{
  "generatedAt": "2026-02-06T12:00:00.000Z",
  "employees": [
    {
      "name": "Top Manager",
      "email": "top@example.com",
      "team": "Engineering",
      "position": "VP Engineering",
      "balance": 10,
      "periodEndsAt": "2026-12-31",
      "reports": [
        {
          "name": "Middle Manager",
          "email": "middle@example.com",
          "team": "Engineering",
          "position": "Tech Lead",
          "balance": 5,
          "periodEndsAt": "2026-12-31",
          "reports": [
            {
              "name": "Developer",
              "email": "dev@example.com",
              "team": "Engineering",
              "position": "Software Engineer",
              "balance": 15,
              "periodEndsAt": "2026-12-31",
              "reports": []
            }
          ]
        }
      ]
    }
  ]
}
```

## Acceptance Criteria

- [ ] AC-1: Existing functionalities are not affected (Slack notifications, manager-reports CLI)
- [ ] AC-2: If the `--format` argument is not provided, the CLI fails with an error message
- [ ] AC-3: If the `--format` value is different than "JSON", the CLI fails with an error message
- [ ] AC-4: If there are no employees/vacations, the JSON will be empty but valid (`{ "generatedAt": "...", "employees": [] }`)
- [ ] AC-5: The `--output` argument specifies the file path, defaulting to `./output.json`
- [ ] AC-6: The JSON is hierarchical - managers contain a `reports` array with their direct reports (recursively)
- [ ] AC-7: All employees are included to maintain complete hierarchy (regardless of balance)
- [ ] AC-8: Uses existing environment variables (`API_USER`, `API_TOKEN`, `USE_SANDBOX`, `FILTER_BY_MANAGER`)
- [ ] AC-9: Named list IDs (team, position) are resolved to display names (like manager-reports)
- [ ] AC-10: Unit tests cover argument validation, JSON structure, and error cases (minimum 80% coverage)

## Technical Notes

- Reuse the lightweight `cliConfig.ts` created for `manager-reports`
- Reuse the `resolveListIds()` function from `managerReports.ts` for resolving team/position IDs
- Adapt the vacation balance fetching logic from `VacationsReport.generate()`
- The hierarchy building can adapt the `getAllReports()` pattern but needs to build a tree, not a flat list
- Consider extracting shared utilities to avoid code duplication with `managerReports.ts`
- The `--format` argument is designed for future extensibility (CSV, XML, etc.)

## Priority

- [ ] Critical
- [ ] High
- [x] Medium
- [ ] Low

## Estimated Size

- [ ] S (< 1 day)
- [x] M (1-3 days)
- [ ] L (3-5 days)
- [ ] XL (> 5 days)

---

## Workflow Artifacts

### Architecture Plan
- File: `../plans/STORY-002-plan.md`
- Status: [ ] Pending | [x] Approved | [ ] Rejected
- Approved by: User
- Date: 2026-02-06

### Code Review
- PR: N/A (local branch)
- File: `../reviews/STORY-002-review.md`
- Status: [ ] Pending | [x] Approved | [ ] Changes Requested

### QA Report
- File: `../qa-reports/STORY-002-qa.md`
- Status: [ ] Pending | [x] Passed | [ ] Failed
