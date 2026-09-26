# workspace-reservation-service — Claude Code Instructions

## Project Context

Technology:

* Java 17
* Spring Boot
* Maven
* JUnit
* In-memory reservation state (see docs/requirements.md)

Application-specific constraints:

Do not introduce excluded capabilities unless explicitly approved by requirements.

## Architecture

* Follow the application's approved architecture.
* Prefer the smallest design satisfying approved requirements.
* Do not introduce unnecessary abstractions, infrastructure, or distributed-system patterns.

## PDD Workflow

For behavior-changing work:

Requirements
→ Plan
→ Human Review
→ API / External Contract when applicable
→ Human Review
→ Implementation Plan
→ Human Review
→ RED
→ GREEN
→ optional REFACTOR
→ Final Review

RED, GREEN, and REFACTOR are separate authorization boundaries.

Completion of one phase does not authorize the next.

## Planning Artifacts

* Work items: each piece of work (new project or enhancement) has its own folder `docs/.ai/<work-item>/`; PDD commands take the work item as an argument
* Requirements: `docs/.ai/<work-item>/requirements.md` (optional product-level `docs/requirements.md` is read-only context)
* Plan: `docs/.ai/<work-item>/Plan.md`
* API / External Contract: `docs/.ai/<work-item>/API-Contract.md` (when applicable)
* Implementation Plans: `docs/.ai/<work-item>/NNN_Implementation_Plan_<Milestone>.md`

Plan defines WHAT is delivered.

The external contract defines approved externally observable behavior when applicable.

Each Implementation Plan defines HOW one approved milestone is executed.

If approved artifacts materially conflict, stop and surface the conflict for human review.

## External Engineering Standards

Use externally configured agents, skills, and prompts from `production-engineering-standards`.

Do not convert recommendations from skills into project requirements without requirement or repository evidence.

## Clarification Before Assumption

Do not invent or silently resolve material requirements.

If missing, ambiguous, or contradictory information materially affects the current task:

1. Ask focused clarification questions.
2. Stop the workflow.
3. Wait for the user's response before continuing.

Do not use an Open Questions section as a substitute for required clarification.

## Verification

Run the project's approved verification commands:

```
mvn test
mvn verify
```

Do not claim verification succeeded unless the commands completed successfully.
