# STORY-001: Add Microsoft Teams notification support

## Status
- [x] Draft
- [x] Ready for Development
- [ ] In Architecture
- [ ] In Development
- [ ] In Review
- [ ] In QA
- [ ] Done

## Description

Currently, the time-off notifier only supports Slack for sending notifications to employees. We want to add support for Microsoft Teams as an alternative notification channel, so organizations using Teams can also benefit from this tool.

The implementation should follow the same patterns as the existing Slack integration, using a clean abstraction that allows switching between notification providers.

## Acceptance Criteria

- [ ] AC-1: Create a new `TeamsService` class that implements the same interface as `SlackService`
- [ ] AC-2: Add configuration options for Teams (webhook URL, channel)
- [ ] AC-3: The system should be able to send notifications to Teams channels
- [ ] AC-4: Add environment variables for Teams configuration in `.env.sample`
- [ ] AC-5: Update README with Teams setup instructions
- [ ] AC-6: Add unit tests for the new TeamsService (minimum 80% coverage)
- [ ] AC-7: The existing Slack functionality must continue to work unchanged

## Technical Notes

- Microsoft Teams supports incoming webhooks for posting messages
- Consider creating a `NotificationService` interface that both Slack and Teams implement
- The message format may need to be adapted (Teams uses Adaptive Cards)
- Configuration should allow selecting which service to use (or both)

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
- File: `../plans/STORY-001-plan.md`
- Status: [ ] Pending | [ ] Approved | [ ] Rejected

### Code Review
- PR: #[number]
- File: `../reviews/STORY-001-review.md`
- Status: [ ] Pending | [ ] Approved | [ ] Changes Requested

### QA Report
- File: `../qa-reports/STORY-001-qa.md`
- Status: [ ] Pending | [ ] Passed | [ ] Failed
