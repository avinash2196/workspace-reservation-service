# 001 — Implementation Plan — RE-M2 FOUNDATION: Messaging prerequisites for broker-free execution

## Milestone

- **Work item:** `reservation-events`
- **Milestone:** RE-M2 — Messaging prerequisites for broker-free execution
- **Milestone type (as recorded in `Plan.md`):** FOUNDATION
- **Serves:** RE-M3 (RED) and RE-M5 (RED)
- **Status:** Approved 2026-09-28 (see Human Review). **Executed and verified 2026-09-28**, with all
  criteria met. See the `Plan.md` Execution Status entry for the evidence.

## Authoritative References

| Artifact | Status |
| --- | --- |
| `docs/.ai/reservation-events/requirements.md` | Approved 2026-09-28 |
| `docs/.ai/reservation-events/Plan.md` (§ RE-M2) | Approved 2026-09-28 |
| `docs/.ai/reservation-events/API-Contract.md` | Approved 2026-09-28 |
| `CLAUDE.md` | Approved stack (Java 17, Spring Boot, Maven, JUnit) and verification commands |
| `docs/requirements.md` | Product-level, read-only context (RE-R3) |

## Predecessor Evidence

- RE-M1 (CONTRACT) is recorded **Complete** in `Plan.md` Execution Status: `API-Contract.md` was
  approved by human review on 2026-09-28, and every RE-C1 decision is resolved.
- RE-M1 changed no executable artifact, so the repository's executable state is the `initial-build`
  baseline.

## Current Repository State (inspected 2026-09-28)

- `git status --short` shows only `?? docs/.ai/reservation-events/`. No source, test, or build file
  is modified. HEAD is `a0580d0`.
- `pom.xml`:
  - imports `spring-boot-dependencies` `3.3.3` as a BOM (no starter parent);
  - declares only `spring-boot-starter`, `spring-boot-starter-web`, and `spring-boot-starter-test`
    (test);
  - pins `maven-compiler-plugin` `3.15.0` with source/target `17`.

  Its `java.version`/`maven.compiler.*` properties say `21`. This is a pre-existing inconsistency,
  out of scope (see Explicit Exclusions).
- `src/main/resources/application.properties` contains only `server.port=8081`.
- There is no `src/test/resources`.
- There are five test classes. Two use
  `@SpringBootTest(webEnvironment = RANDOM_PORT)`: `ReservationControllerHttpAPITest` and
  `ReservationControllerValidationTest`.
- No Kafka, messaging, or metrics dependency exists anywhere in the build.
- **Baseline verification, run 2026-09-28 before this plan with no broker present:**
  `mvn -q -B test` exited 0. Tests run: 22 + 13 + 7 + 21 + 13 = **76, Failures 0, Errors 0,
  Skipped 0**.

## Why This FOUNDATION Is Required

1. **Why can RE-M3 and RE-M5 not proceed from the current state?** RE-M3's tests must stand in for
   the broker with an in-process double (RE-R17). They need to assert what reached the broker
   boundary and to make a send fail. Those types come from a Kafka client library, and none is on
   the classpath. A RED milestone may not add dependencies (Plan § RE-M3 Explicit Exclusions), and
   GREEN comes after RED. So RE-M3's tests would fail to compile for an environment reason, not
   because the approved behavior is missing. That is invalid RED evidence. RE-M5 needs the same
   classpath.
2. **Exact prerequisite:**
   - the Kafka client library on the compile and test classpaths;
   - broker connection configuration keys with defaults (RE-D4) that let the application start
     with no broker present (RE-O3).
3. **Files that may change:** `pom.xml` and `src/main/resources/application.properties` only.
   `Plan.md` Execution Status is also updated after verified execution (File Scope rule).
4. **Excluded target behavior:** no publisher, no event type, no payload, no serializer, no failure
   handling, no logging, no counting, no producer bean, and no code that reads the new configuration
   keys. All of that is RE-M4. There is no change to the creation path (RE-M6) and there are no
   tests.
5. **How completion is verified:**
   - `mvn test` and `mvn verify` with no broker present;
   - a dependency-tree check;
   - a check that the in-process double class exists in the resolved artifact;
   - a packaged-application startup check with no broker present.
6. **Evidence that RE-M3 and RE-M5 can begin:**
   - `org.apache.kafka:kafka-clients:4.3.1` resolves at `compile` scope, so it is on both main and
     test classpaths;
   - `org.apache.kafka.clients.producer.MockProducer` is present in that artifact;
   - all 76 existing tests still pass and the application starts, with no broker present.

## Decisions for Review

These choices bind RE-M3, RE-M4, RE-M5, and RE-M6, and those milestones cannot change them. The
labels `RE-F1`–`RE-F4` are introduced here. They do not collide with `RE-R`, `RE-O`, `RE-C`, `RE-X`,
`RE-D`, `RE-M`, `RE-S`, or `RE-K`.

| ID | Decision | Chosen | Alternatives considered | Source |
| --- | --- | --- | --- | --- |
| RE-F1 | Kafka client library and version (RE-D5) | Plain Apache `org.apache.kafka:kafka-clients` **4.3.1**, `compile` scope, with the version declared explicitly on the dependency. This overrides the `kafka-clients` 3.7.x that the Spring Boot 3.3.3 BOM manages. No `spring-kafka`. | (a) Boot-managed `spring-kafka` 3.2.x: end of OSS support 2025-06-30 and end of commercial support 2026-06-30, so unsupported today. (b) Upgrade Spring Boot to 4.x to get a supported `spring-kafka`: an application-wide upgrade beyond RE-M2 scope. | User decision at RE-M2 planning, 2026-09-28. Support evidence below. |
| RE-F2 | In-process test double for RE-M3 and RE-M5 (RE-R17) | Apache `org.apache.kafka.clients.producer.MockProducer`, shipped inside `kafka-clients`, so no extra test dependency. Mockito, already provided by `spring-boot-starter-test`, stays available for doubling the application's own publishing capability in RE-M5. | Mockito-only doubles of `Producer`. | User decision at RE-M2 planning, 2026-09-28. |
| RE-F3 | Broker connection configuration keys and defaults (RE-D4) | `reservation-events.kafka.bootstrap-servers=localhost:9092` | Environment-variable-only configuration with no default: the application could not start without it being set, which conflicts with RE-O3. | Plan § RE-M2 assigns RE-D4 here. |
| RE-F4 | Maximum time a publish may block waiting for broker metadata or buffer space (RE-D4, RE-R12) | `reservation-events.kafka.max-block-ms=1000` (one second) | Leave the client default `max.block.ms` of 60000 ms. With no broker present, a publish on the creation path could then block a create request for up to 60 s. RE-O6 sets no latency target, but RE-O3 requires the application to run with no broker present. | Proposed for review. RE-D4 leaves the value to this milestone. |

**Version support evidence for RE-F1** (checked 2026-09-28):

- Apache Kafka policy: bugfix releases "for the last 3 releases"
  (https://cwiki.apache.org/confluence/display/KAFKA/Time+Based+Release+Plan).
- Maven Central `kafka-clients` metadata lists `<release>4.3.1</release>`. The three most recent
  release lines are 4.1, 4.2, and 4.3, so 4.3.1 is the latest release of the newest supported line
  (https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/maven-metadata.xml).
- Spring support generations: `spring-kafka` 3.2.x OSS ended 2025-06-30 and commercial ended
  2026-06-30 (https://api.spring.io/projects/spring-kafka/generations). Spring Boot 3.3.x OSS ended
  2025-06-30 (https://api.spring.io/projects/spring-boot/generations).
- `kafka-clients` 4.3.1 declares runtime dependencies `zstd-jni` 1.5.6-10, `lz4-java` 1.10.2,
  `snappy-java` 1.1.10.7, and `slf4j-api` 1.7.36
  (https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/4.3.1/kafka-clients-4.3.1.pom).
  The Spring Boot 3.3.3 BOM manages `slf4j-api` to its own 2.0.x version, so the application keeps
  a single SLF4J 2 API. This is checked in verification step V3.
- Java compatibility: Kafka 4.x clients run on Java 11 or later. The approved runtime is Java 17
  (verified by compilation in V1).

The Spring Boot version itself (3.3.3, OSS support ended 2025-06-30) is pre-existing. Upgrading it is
outside this work item's approved scope. It is recorded as a residual risk (see RE-M2-risk 1).

## Carried-Forward Decisions

`reservation-events` has no earlier approved Implementation Plan. This plan relies on these
repository-established choices and does not change them:

- Spring Boot `3.3.3` imported as a BOM; `maven-compiler-plugin` `3.15.0`, target 17 (`pom.xml`).
- `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ) as the test stack (`pom.xml`).
- `server.port=8081` (`application.properties`).

## Files in Scope

| File | Action |
| --- | --- |
| `pom.xml` | Modify: add one dependency |
| `src/main/resources/application.properties` | Modify: add two properties |

No file is created. No other file is modified, apart from the post-verification Execution Status
update in `Plan.md`.

## Ordered Proposed Changes

1. **`pom.xml`:** add a `<dependency>` on `org.apache.kafka:kafka-clients` version `4.3.1` (implicit
   `compile` scope), with a comment, after the `spring-boot-starter-web` dependency (RE-F1, RE-F2).
2. **`application.properties`:** add a comment line and
   `reservation-events.kafka.bootstrap-servers=localhost:9092` (RE-F3).
3. **`application.properties`:** add `reservation-events.kafka.max-block-ms=1000` (RE-F4).

## Exact Code

### `pom.xml` (complete unified diff)

```diff
--- a/pom.xml
+++ b/pom.xml
@@ -46,6 +46,14 @@
             <artifactId>spring-boot-starter-web</artifactId>
         </dependency>
 
+        <!-- Kafka client for ReservationCreated publishing (reservation-events RE-F1).
+             Version pinned to a supported Apache Kafka line, overriding the Boot-managed version. -->
+        <dependency>
+            <groupId>org.apache.kafka</groupId>
+            <artifactId>kafka-clients</artifactId>
+            <version>4.3.1</version>
+        </dependency>
+
         <!-- Spring Boot Starter Test (includes JUnit 5, Mockito, AssertJ) -->
         <dependency>
             <groupId>org.springframework.boot</groupId>
```

### `src/main/resources/application.properties` (complete final content)

```properties
server.port=8081

# reservation-events: Kafka broker connection (RE-F3, RE-F4). No broker is required to start.
reservation-events.kafka.bootstrap-servers=localhost:9092
reservation-events.kafka.max-block-ms=1000
```

Both keys are read by nothing in this milestone. RE-M4 consumes them when it creates the producer.

## Acceptance / Completion Criteria

RE-M2 is complete when all of the following are true:

1. `pom.xml` declares `org.apache.kafka:kafka-clients:4.3.1` and no other new dependency or plugin.
2. `application.properties` contains exactly the content shown above.
3. `kafka-clients` resolves at version `4.3.1`, `compile` scope. No `spring-kafka` artifact is on the
   classpath. `slf4j-api` resolves to the single Boot-managed 2.0.x version.
4. `org/apache/kafka/clients/producer/MockProducer.class` is present in the resolved
   `kafka-clients-4.3.1.jar`.
5. `mvn test` completes successfully with no broker present. All 76 existing tests pass, and no test
   file is changed.
6. `mvn verify` completes successfully with no broker present.
7. The application starts with no broker present. Its log contains
   `Started WorkspaceReservationApplication`, and it serves HTTP. *(Revision 1, approved
   2026-09-28: previously "The packaged application starts". The build has never produced an
   executable jar because `spring-boot-maven-plugin` has no `repackage` execution. That is a
   pre-existing gap and out of scope.)*
8. No file other than those in Files in Scope is changed (excluding the Plan.md Execution Status
   update).
9. No excluded capability is introduced (see Explicit Exclusions).

## Verification Commands and Expected Evidence

Run from the repository root. Every Expected result is **Expected / Predicted — Not Yet
Verified.**

| Step | Command | Expected / Predicted — Not Yet Verified |
| --- | --- | --- |
| V0 | `powershell -Command "(Test-NetConnection localhost -Port 9092 -WarningAction SilentlyContinue).TcpTestSucceeded"` | `False`: no broker is listening, which confirms the no-broker precondition. |
| V1 | `mvn -B test` | `BUILD SUCCESS`. Tests run: 76, Failures 0, Errors 0, Skipped 0. |
| V2 | `mvn -B verify` | `BUILD SUCCESS`. Same 76 tests pass, and `target/workspace-reservation-service-0.1.0-SNAPSHOT.jar` is produced. |
| V3 | `mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree "-Dincludes=org.apache.kafka,org.springframework.kafka,org.slf4j:slf4j-api"` | Shows `org.apache.kafka:kafka-clients:jar:4.3.1:compile`, no `org.springframework.kafka` entry, and one `org.slf4j:slf4j-api` at 2.0.x. |
| V4 | `unzip -l ~/.m2/repository/org/apache/kafka/kafka-clients/4.3.1/kafka-clients-4.3.1.jar \| grep "clients/producer/MockProducer.class"` | One matching line. |
| V5 | `mvn -B spring-boot:run "-Dspring-boot.run.arguments=--server.port=0"`, using the pom's existing plugin `3.3.3`. Run until started (up to 60 s), send one HTTP request, then stop. *(Revision 1, approved 2026-09-28. Replaced `java -jar …`, which failed with `no main manifest attribute`.)* | The log contains `Started WorkspaceReservationApplication`, has no Kafka-related error, and the process stays up until it is stopped. |
| V6 | `git status --short` | Only `M pom.xml`, `M src/main/resources/application.properties`, and the untracked `docs/.ai/reservation-events/`. |

**Repeatability and isolation:** no test in this milestone touches persistent state or overrides
configuration, and no test is added. The repeat-run rule therefore does not apply. V5 uses
`--server.port=0` so it does not depend on port 8081 being free.

## Pre-authorized Contingencies

- **C1:**
  - *Trigger:* V3 shows `org.slf4j:slf4j-api` at `1.7.36`, or at two different versions.
  - *Change:* in `pom.xml`, add `<exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>`
    to the `kafka-clients` dependency only.
  - *Then:* rerun V1–V6.

No other contingency is authorized. Any other failure stops execution and returns to planning.

## Rollback / Recovery

Revert only this milestone's two files:
`git restore pom.xml src/main/resources/application.properties`. Do not use any repository-wide
reset, clean, or checkout.

## Risks

1. **Spring Boot 3.3.3 is itself out of OSS support** (pre-existing). Upgrading it is outside the
   approved scope. It is raised for Final Review as a residual risk, not addressed here.
2. **Overriding a BOM-managed version:** `kafka-clients` 4.3.1 differs from Boot 3.3.3's managed
   3.7.x. There is no Boot Kafka auto-configuration without `spring-kafka`, so Boot has no Kafka
   integration code to conflict with. Transitive `slf4j-api` alignment is checked by V3 and covered
   by C1.
3. **Unread configuration keys:** the two new keys have no reader until RE-M4. If RE-M4 is not
   approved, they remain inert.
4. **`max-block-ms=1000` (RE-F4)** is a proposal. If rejected at review, remove that line and the
   client default of 60000 ms applies in RE-M4.

## Explicit Exclusions

- No publisher, event type, payload, serializer, producer bean, failure handling, logging, or
  counting, and no code reading the new keys (RE-M4).
- No change to the creation path (RE-M6).
- No tests, and no change to existing tests (not a RED milestone).
- No `spring-kafka`, Docker, Testcontainers, embedded broker, or `spring-kafka-test` (RE-X10).
- No metrics, Actuator, Micrometer, or tracing dependency (RE-X9).
- No dependency serving anything but the Kafka publish (RE-R2, RE-X5).
- No topic-name configuration key: the topic is fixed by `API-Contract.md`.
- No Spring Boot upgrade, and no fix to the pre-existing `java.version` 21 / compiler 17
  inconsistency in `pom.xml`.
- No change to the existing operations' external behavior (RE-X8).
- No edit to `docs/requirements.md` (RE-R3).

## Human Review

- Status: **Approved** as written by human review (2026-09-28), including RE-F1–RE-F4. Approval
  authorizes RE-M2 execution only, not RE-M3.
- Revision 1: criterion 7 and V5 amended. Approved by human review 2026-09-28 after the original V5
  failed during execution. No change to the files in scope or to the exact code.
