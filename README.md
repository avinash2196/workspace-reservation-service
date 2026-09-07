Workspace Reservation Service

A small Spring Boot REST API used to validate a Prompt-Driven Development (PDD) workflow with explicit human review between requirements, planning, contract definition, RED, GREEN, and final review phases.

The service manages time-based workspace reservations using in-memory state only. It intentionally avoids persistence and distributed infrastructure so the focus remains on controlled delivery, test-first behavior, concurrency safety, and reviewable implementation plans.

Goals

This project demonstrates how to build a feature through small, reviewable PDD milestones:

capture and approve requirements;

create and approve the delivery Plan;

define and approve the external API contract;

create a detailed Implementation Plan for each RED or GREEN milestone;

review proposed tests or production code before implementation;

execute only the approved phase;

verify the result;

update only milestone execution status in Plan.md;

continue from the new repository state.

The important control is that RED and GREEN are separate authorization boundaries. Completing RED does not automatically authorize GREEN.

Technology

Java 17

Spring Boot 3

Maven

REST API

In-memory reservation state

Explicitly Out of Scope

The project intentionally does not include:

database persistence;

JPA or ORM;

Flyway or schema migrations;

messaging;

caching;

authentication or authorization;

external service integrations;

user management;

additional reservation endpoints beyond the approved contract.

Reservation state may be lost when the application restarts.

Reservation Model

A reservation contains:

id — server-generated UUID;

workspaceId — workspace identifier;

userId — user identifier;

startTime — UTC start time;

endTime — UTC end time;

status — ACTIVE or CANCELLED.

Core Rules

workspaceId, userId, startTime, and endTime are required.

startTime must be before endTime.

New reservations are created as ACTIVE.

Two ACTIVE reservations for the same workspace must not overlap.

Reservations for different workspaces may overlap.

CANCELLED reservations do not block future reservations.

Cancelling an already-cancelled reservation is idempotent.

Concurrent create requests must not successfully create two overlapping ACTIVE reservations for the same workspace.

The overlap rule is:

s1 < e2 AND s2 < e1

Adjacent reservations such as 10:00-11:00 and 11:00-12:00 do not conflict.

API

The approved scope contains exactly three endpoints.

Create Reservation

POST /api/v1/reservations

Expected outcomes:

201 Created — reservation created;

400 Bad Request — invalid request;

409 Conflict — overlapping active reservation for the same workspace.

Get Reservation

GET /api/v1/reservations/{id}

Expected outcomes:

200 OK — reservation found;

404 Not Found — reservation does not exist.

Cancel Reservation

DELETE /api/v1/reservations/{id}

Expected outcomes:

204 No Content — reservation cancelled, including repeated cancellation of an already-cancelled reservation;

404 Not Found — reservation does not exist.

The detailed request, response, and error schemas belong to the approved API contract under docs/.ai/.

PDD Artifacts

The project keeps application-specific PDD artifacts in the repository:

docs/
├── requirements.md
└── .ai/
├── Plan.md
├── 0_API_Contract.md
├── 001_Implementation_Plan_Core_Reservation_Store_RED.md
├── 002_Implementation_Plan_Core_Reservation_Store_GREEN.md
└── ...

Artifact authority flows in one direction:

Requirements
↓
Plan
↓
API Contract
↓
Implementation Plan
↓
Tests / Checks
↓
Production Implementation

If authoritative artifacts materially conflict, implementation stops for human review instead of silently rewriting one artifact to match another.

PDD Milestones

The approved Plan is organized as:

Milestone

Phase

Purpose

0

CONTRACT

Define external API behavior

1

RED

Core reservation-store tests

2

GREEN

Core in-memory reservation store

3

RED

Validation and error-handling tests

4

GREEN

Validation and error handling

5

RED

Conflict-detection and concurrency tests

6

GREEN

Conflict detection and concurrency-safe creation

7

RED

HTTP API tests

8

GREEN

Spring Boot HTTP API implementation

Each repository-changing milestone gets its own detailed Implementation Plan.

Implementation Plan Review Model

Before an Implementation Plan is created, the planner must inspect:

the current repository structure;

relevant source and tests;

approved requirements;

approved Plan.md;

approved API contract when applicable;

predecessor evidence;

current milestone execution status.

The Implementation Plan is a human-review artifact. It should contain the exact files to create or modify and concrete proposed tests or production code where practical.

The planner may propose code inside the Implementation Plan, but must not apply those changes to source or test files.

After human approval:

the test-engineer executes approved RED plans;

the implementation-engineer executes approved GREEN plans;

the refactoring-engineer is used only for separately approved, behavior-preserving refactoring.

After successful verification, only the milestone execution/status information in Plan.md is updated. Approved milestone scope is not rewritten to match implementation.

Verification

The Plan defines the following verification commands:

./mvnw test

Run after each milestone where applicable.

Final verification:

./mvnw verify

A milestone is not considered complete unless the relevant verification was actually executed and the expected evidence was observed.

Final Review

After Milestone 8, the project goes through three separate reviews:

Architecture Review — architecture boundaries, concurrency design, scope drift, and unnecessary infrastructure.

Code Review — correctness, maintainability, test quality, API compliance, and scope control.

Production Readiness Review — build evidence, runtime behavior, failure handling, and operational risks within the explicitly approved scope.

The absence of a database, messaging, caching, authentication, or external integrations is intentional for this project and should not be treated as missing functionality.

Why This Project Exists

The service itself is intentionally small. The main purpose is to validate a disciplined development workflow where:

requirements remain authoritative;

planning remains reviewable;

proposed code can be reviewed before implementation;

tests provide executable evidence;

implementation is constrained to approved scope;

progress is recorded without rewriting approved decisions;

human review remains an explicit part of delivery.

That makes the repository a practical reference for applying PDD to a real Spring Boot application without hiding major design or implementation decisions inside a single AI-generated change.