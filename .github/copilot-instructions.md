# Workspace Reservation Service

## Project Context

This repository contains the Workspace Reservation Service.

Technology:

- Java 17
- Spring Boot 3
- Maven
- JUnit

The application intentionally uses in-memory storage only.

There is:

- no production database
- no persistence framework
- no Flyway
- no messaging
- no cache
- no authentication
- no external service integration

Do not introduce these capabilities unless an approved requirement explicitly adds them.

## Architecture

Keep the architecture appropriate for a small service.

- Controllers handle HTTP concerns only.
- Business rules must remain outside controllers.
- API request and response models should not contain business logic.
- Keep reservation state behind a clear application boundary.
- Do not introduce unnecessary interfaces, factories, adapters, modules, or distributed-system patterns.
- Prefer the smallest design satisfying the approved requirements.

## Testing

Behavior-changing work follows:

Requirements
→ Plan
→ Human Review
→ Implementation Plan
→ Human Review
→ RED
→ GREEN
→ optional REFACTOR
→ Final Review

RED, GREEN, and REFACTOR are separate authorization boundaries.

RED:
- tests and required test support only
- no production implementation

GREEN:
- smallest production change required to satisfy approved RED evidence
- no implementation of future milestones
- no unrelated refactoring

REFACTOR:
- optional
- behavior preserving
- separately authorized

Completion of one phase does not authorize the next.

## External Engineering Standards

This repository uses externally configured agents, skills, and prompts from
production-engineering-standards.

When a prompt or agent names a skill, load and apply that skill.

Apply additional skills only when relevant to the approved scope.

Do not convert recommendations from skills into project requirements without
requirement or repository evidence.

## Planning Artifacts

Plan:

docs/.ai/Plan.md

Implementation Plans:

docs/.ai/NNN_Implementation_Plan_<Milestone>.md

Plan.md defines WHAT is delivered.

Each Implementation Plan defines HOW one approved milestone only is executed.

Human approval is required between authorization phases.

## Verification

Run:

./mvnw test

and when applicable:

./mvnw verify

Do not claim verification succeeded unless the command actually completed
successfully.

## Clarification Before Assumption

Do not invent or silently resolve material requirements.

When information is missing or contradictory and materially affects
application behavior, contracts, data handling, persistence, security,
integrations, testing, or milestone scope:

1. Surface the ambiguity.
2. Ask the user focused clarification questions.
3. Stop the current workflow until those questions are answered.

Do not replace required clarification with an "Open Questions" section
and continue as though the requirements were complete.

Non-material details that do not affect the current milestone may remain
explicitly unresolved.