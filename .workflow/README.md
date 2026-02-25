# AI-Assisted Development Workflow

A structured workflow for AI agents to collaborate on software development with human oversight.

---

## Quick Links

| Document | Purpose |
|----------|---------|
| [PROJECT.md](./PROJECT.md) | **Project-specific context** (tech stack, patterns, domain) |
| [CONFIG.md](./CONFIG.md) | Workflow config (HITL, story sources, coverage targets) |
| [STORY_TEMPLATE.md](./STORY_TEMPLATE.md) | Template for creating new user stories |
| **Agents** | |
| [agents/architect.md](./agents/architect.md) | Creates implementation plans |
| [agents/developer.md](./agents/developer.md) | Implements the code |
| [agents/code-review.md](./agents/code-review.md) | Reviews code quality |
| [agents/qa.md](./agents/qa.md) | Validates acceptance criteria |

---

## Workflow Overview

This workflow uses **Test-Driven Development (TDD)** and **phase-by-phase completion**:
- The Architect analyzes everything upfront and creates a phased plan
- Each phase completes the full cycle (Dev → Review → QA) before the next begins
- The Developer writes tests FIRST, then implements (Red-Green-Refactor)

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│   ┌─────────┐    ┌───────────┐                                     │
│   │  Story  │───▶│ Architect │──┐                                  │
│   │         │    │   Agent   │  │                                  │
│   └─────────┘    └───────────┘  │                                  │
│        │              │         │                                  │
│        ▼              ▼         │                                  │
│   [STORY.md]    [FULL plan.md]  │  Analyzes ALL requirements       │
│                 with PHASES     │  upfront, creates phased plan    │
│                 + APPROVAL      │                                  │
│                                 │                                  │
│   ┌─────────────────────────────┼──────────────────────────────┐   │
│   │  FOR EACH PHASE:            ▼                              │   │
│   │  ┌───────────┐    ┌────────────┐    ┌───────────┐          │   │
│   │  │ Developer │───▶│   Code     │───▶│    QA     │──┐       │   │
│   │  │   (TDD)   │    │   Review   │    │   Agent   │  │       │   │
│   │  └───────────┘    └────────────┘    └───────────┘  │       │   │
│   │       │                │                  │        │       │   │
│   │       ▼                ▼                  ▼        │       │   │
│   │  [tests FIRST    [phase-N-        [phase-N-       │       │   │
│   │   then code]      review.md]       qa.md]         │       │   │
│   │                   + APPROVAL       + APPROVAL     │       │   │
│   │                                         │         │       │   │
│   │                                         ▼         │       │   │
│   │                                   Next Phase ◀────┘       │   │
│   └───────────────────────────────────────────────────────────┘   │
│                                                                     │
│   ┌─────────┐                                                      │
│   │  Merge  │◀── After ALL phases complete                         │
│   └─────────┘                                                      │
│        │                                                            │
│        ▼                                                            │
│   [merged]                                                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Workflow Stages

### 1. Story Definition

**Input:** Business requirement
**Output:** `.workflow/stories/STORY-XXX.md`
**Human Action:** Write or approve the story

Create a user story using the [STORY_TEMPLATE.md](./STORY_TEMPLATE.md). Include:
- Clear description
- Acceptance criteria (testable)
- Technical notes
- Priority and size estimates

### 2. Architecture (Architect Agent) — Analyzes ALL Upfront

**Input:** Story file
**Output:** `.workflow/plans/STORY-XXX-plan.md`
**Human Action:** Approve or request changes to the plan

The [Architect Agent](./agents/architect.md) is the **only agent that sees the full picture**:
- Analyzes the complete story and all acceptance criteria
- Creates a detailed implementation plan with **well-defined phases**
- Maps each acceptance criterion to specific phases
- Identifies risks and dependencies across phases
- Proposes testing strategy for each phase

### 3-5. Phase Loop (Developer → Code Review → QA)

Each phase from the plan goes through the complete cycle before the next phase begins.

#### Developer Agent (TDD)

**Input:** Current phase from approved plan
**Output:** Code, tests, commits for ONE phase
**Human Action:** Monitor progress

The [Developer Agent](./agents/developer.md) uses **Test-Driven Development**:
1. **RED:** Write tests first (tests should fail)
2. **GREEN:** Write minimal code to make tests pass
3. **REFACTOR:** Clean up while keeping tests green

**Validation Gate:** See [CONFIG.md](./CONFIG.md#validation-gates)
- `./gradlew build -x test` must pass (compilation)
- `./gradlew checkstyleMain` must pass with 0 errors
- `./gradlew spotlessJavaCheck` must pass
- `./gradlew test` must pass

#### Code Review Agent

**Input:** Implemented phase code
**Output:** `.workflow/reviews/STORY-XXX-phase-N-review.md`
**Human Action:** Approve or request changes

The [Code Review Agent](./agents/code-review.md) reviews ONE phase:
- Verifies TDD was followed (tests exist and are meaningful)
- Reviews code quality and patterns
- Evaluates test coverage (see [CONFIG.md](./CONFIG.md#coverage-expectations))
- Checks security considerations

#### QA Agent

**Input:** Reviewed phase code
**Output:** `.workflow/qa-reports/STORY-XXX-phase-N-qa.md`
**Human Action:** Approve to proceed to next phase

The [QA Agent](./agents/qa.md) validates ONE phase:
- Validates acceptance criteria covered by this phase
- Runs full test suite
- Verifies coverage targets
- Approves progression to next phase

### 6. Merge

**Input:** All phases completed and approved
**Output:** Merged to main
**Human Action:** Merge the PR

---

## Directory Structure

```
.workflow/
├── README.md                 # This file - workflow overview
├── PROJECT.md                # Project-specific context (tech stack, patterns)
├── CONFIG.md                 # Workflow configuration (HITL, coverage targets)
├── STORY_TEMPLATE.md         # Template for new stories
│
├── agents/                   # Agent definitions
│   ├── architect.md          # Architecture planning
│   ├── developer.md          # Implementation
│   ├── code-review.md        # Code review
│   └── qa.md                 # QA validation
│
├── stories/                  # User stories
│   ├── STORY-001.md
│   └── STORY-002.md
│
├── plans/                    # Architecture plans
│   └── STORY-XXX-plan.md
│
├── reviews/                  # Code review reports
│   └── STORY-XXX-review.md
│
└── qa-reports/               # QA reports
    └── STORY-XXX-qa.md
```

---

## Human-in-the-Loop (HITL) Integration

The workflow requires human approval at key points:

| Stage | Approval Required | Current Method |
|-------|-------------------|----------------|
| Story | Define/approve story | CLI / Markdown |
| Architecture | Approve implementation plan | CLI |
| Code Review | Approve for QA | CLI |
| QA | Approve for merge | CLI |
| Merge | Execute merge | CLI / GitHub |

**Configuring HITL:** See [CONFIG.md](./CONFIG.md#human-in-the-loop-hitl-integration)

Future integrations (Slack, etc.) can be configured in CONFIG.md.

---

## Story Source Integration

Stories can come from different sources:

| Source | Current | Future |
|--------|---------|--------|
| Markdown files | Yes | Yes |
| Jira | No | Planned |
| GitHub Issues | No | Possible |

**Configuring Story Source:** See [CONFIG.md](./CONFIG.md#story-source-integration)

---

## Coverage Expectations

Test coverage targets vary by file type:

| File Type | Target | Blocking? |
|-----------|--------|-----------|
| Utilities | 90%+ | Yes |
| Services | 80%+ | Yes |
| Consensus-critical | 85%+ | Yes |
| Entry Points | 60%+ | No |
| Config | 70%+ | No |
| Models | N/A | N/A |

**Detailed coverage config:** See [CONFIG.md](./CONFIG.md#coverage-expectations)

---

## Getting Started

### Starting a New Story

1. Copy [STORY_TEMPLATE.md](./STORY_TEMPLATE.md) to `stories/STORY-XXX.md`
2. Fill in the story details and acceptance criteria
3. Mark as "Ready for Development"

### Running the Workflow

1. **Architecture:** Use the prompt in [agents/architect.md](./agents/architect.md)
2. **Development:** Use the prompt in [agents/developer.md](./agents/developer.md)
3. **Code Review:** Use the prompt in [agents/code-review.md](./agents/code-review.md)
4. **QA:** Use the prompt in [agents/qa.md](./agents/qa.md)

### Viewing Status

Check the story file's status checkboxes:
```markdown
- [x] Draft
- [x] Ready for Development
- [x] In Architecture
- [ ] In Development  ← Current stage
- [ ] In Review
- [ ] In QA
- [ ] Done
```

---

## Modifying the Workflow

| What to Modify | Where |
|----------------|-------|
| Project context (tech stack, patterns) | [PROJECT.md](./PROJECT.md) |
| HITL integration | [CONFIG.md](./CONFIG.md#human-in-the-loop-hitl-integration) |
| Story source | [CONFIG.md](./CONFIG.md#story-source-integration) |
| Validation gates | [CONFIG.md](./CONFIG.md#validation-gates) |
| Coverage targets | [CONFIG.md](./CONFIG.md#coverage-expectations) |
| Agent behavior | `agents/*.md` |
| Story format | [STORY_TEMPLATE.md](./STORY_TEMPLATE.md) |

---

## Adapting to Another Project

To use this workflow in a different project:

1. **Copy the `.workflow/` folder** to your project root

2. **Update PROJECT.md** with your project's:
   - Tech stack (language, frameworks, testing tools)
   - Directory structure
   - Key patterns and reference files
   - Domain terminology
   - Build/test commands

3. **Update CONFIG.md** with:
   - Coverage targets appropriate for your project
   - Validation commands for your tech stack

4. **Keep agents generic** - they reference PROJECT.md for specifics

The agents are designed to be project-agnostic. All project-specific knowledge lives in PROJECT.md.
