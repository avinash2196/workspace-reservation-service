# Implementation Plan: Milestone 5 - Conflict Detection and Concurrency - RED

**Milestone**: 5  
**Phase**: RED  
**Title**: Conflict Detection and Concurrency - RED  
**Status**: Ready for Human Review  
**Not Approved**: This plan requires human review before authorization to proceed with test implementation.

---

## Traceability

**Approved Baseline**:
- docs/requirements.md § Create Reservation (Conflict Detection, Concurrency Guarantee)
- docs/.ai/Plan.md § Milestone 5: Conflict Detection and Concurrency - RED
- docs/.ai/0_API_Contract.md § Conflict Condition, Concurrency Guarantee, 409 Conflict Response

**This Plan Addresses**: Test contract for overlap detection, conflict prevention, concurrency-safe reservation creation, and 409 Conflict response at the application/store layer.

---

## Current Repository State

**Completed (Milestones 1–4)**:
- Domain layer: `Reservation.java`, `ReservationStatus.java` (immutable, fully tested)
- Store layer: `ReservationStore.java` with thread-safe ConcurrentHashMap (fully tested, 20 passing tests)
- Validation layer: `ReservationValidator.java` (required field and time range validation)
- Exception layer: `ValidationException.java` (validation error mapping)
- Test layer:
  - `ReservationTest.java` (domain layer tests)
  - `ReservationStoreTest.java` (store layer concurrency and basic CRUD tests)
  - `ReservationControllerValidationTest.java` (HTTP validation and error handling tests)
- All Milestones 1–4 tests passing (33 tests total)
- Build: `pom.xml` configured for Maven, Java 17, Spring Boot 3, JUnit 5

**Incomplete (Blocking Milestone 5 GREEN)**:
- Conflict detection logic (overlap predicate)
- Concurrency-safe conflict checking at store boundary
- 409 Conflict exception and response handling
- Tests for overlap detection and conflict prevention
- Tests for concurrent create requests with overlap

**Missing Files to Create**:
- Conflict detection tests at store layer (application boundary)
- Tests verifying overlap rule: `s1 < e2 AND s2 < e1`
- Concurrency tests for concurrent overlapping create requests

---

## Milestone 5 Scope: Conflict Detection and Concurrency - RED

**Goal**: Define test contract for overlap detection, conflict prevention, and concurrency-safe reservation creation.

**Success Criteria**:
- All tests compile and are runnable via `./mvnw test`
- All tests fail initially (no conflict detection logic implemented yet)
- Tests verify overlap rule from requirements: `s1 < e2 AND s2 < e1`
- Tests verify adjacent reservations are not considered conflicting
- Tests verify ACTIVE reservations block overlapping ACTIVE reservations
- Tests verify CANCELLED reservations do not block any reservation
- Tests verify different workspaces do not interfere with overlap detection
- Tests verify 409 Conflict response when overlap detected
- Concurrency tests expose race conditions in naive implementations
- Tests are behavior-focused and independent of HTTP transport (application layer)

**Exclusions**:
- No conflict detection logic implementation (that is Milestone 6 - GREEN)
- No 409 Conflict exception or response handler implementation (that is Milestone 6 - GREEN)
- No HTTP endpoint integration (that is Milestone 7–8)
- No controller or validator changes (those are in place from Milestones 1–4)

---

## Files to Create / Modify

### 1. `src/test/java/com/example/workspace/reservation/store/ReservationStoreConflictTest.java`

**Status**: To Create  
**Priority**: Critical (RED phase artifact)  
**Content**: Tests for overlap detection, conflict prevention, and concurrency guarantees at the store layer.

**Test Framework**:
- JUnit 5 (from spring-boot-starter-test)
- No Spring Boot annotations (pure unit tests of ReservationStore logic)
- REST client: N/A (testing store directly, not HTTP)
- Assertions: AssertJ
- Concurrency utilities: CountDownLatch, CyclicBarrier, ExecutorService (from java.util.concurrent)

**Test Coverage Organized by Scenario**:

#### Group A: Overlap Rule Definition and Adjacent Reservations (No Conflicts)

1. **test_overlappingReservations_sameWorkspace_activeBlocksOverlappingActive**
   - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Attempt to create overlapping ACTIVE reservation: [10:30, 11:30] for workspace-1
   - Expected: Conflict exception (409 semantics)
   - Verify: Exception message indicates conflict

2. **test_overlappingReservations_sameWorkspace_multipleOverlapScenarios**
   - Test multiple overlap scenarios for same workspace:
     - [10:00, 11:00] with [10:00, 11:00] — exact match (overlap)
     - [10:00, 11:00] with [09:00, 10:30] — start before, end during (overlap)
     - [10:00, 11:00] with [10:30, 12:00] — start during, end after (overlap)
     - [10:00, 11:00] with [09:00, 12:00] — surrounds existing (overlap)
   - Expected: Each attempt raises conflict exception

3. **test_adjacentReservations_sameWorkspace_noConflict**
   - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Create ACTIVE reservation: [11:00, 12:00] for workspace-1 (ends exactly when first starts)
   - Expected: No conflict (adjacent times do not overlap per `s1 < e2 AND s2 < e1`)
   - Verify: Both reservations successfully exist in store

4. **test_nonAdjacentNonOverlappingReservations_sameWorkspace_noConflict**
   - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Create ACTIVE reservation: [12:00, 13:00] for workspace-1 (gap of 1 hour)
   - Expected: No conflict
   - Verify: Both reservations successfully exist in store

5. **test_overlapRule_verifyPredicate_s1LessThanE2_AND_s2LessThanE1**
   - Explicit verification of overlap predicate logic
   - Test case: [10:00, 11:00] vs [11:00, 12:00]
     - s1 (10:00) < e2 (12:00) ✓
     - s2 (11:00) < e1 (11:00) ✗ (NOT less than)
     - Result: NO overlap (conflict)
   - Test case: [10:00, 11:00] vs [10:30, 11:30]
     - s1 (10:00) < e2 (11:30) ✓
     - s2 (10:30) < e1 (11:00) ✓
     - Result: OVERLAP (conflict)

#### Group B: ACTIVE vs CANCELLED Blocking Behavior

6. **test_cancelledReservation_doesNotBlockNewActiveReservation_sameTime**
   - Create and cancel ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Create new ACTIVE reservation: [10:00, 11:00] for workspace-1 (exact same time)
   - Expected: No conflict (CANCELLED does not block)
   - Verify: New reservation successfully created

7. **test_cancelledReservation_doesNotBlockNewActiveReservation_overlap**
   - Create and cancel ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Create new ACTIVE reservation: [10:30, 11:30] for workspace-1 (overlapping time)
   - Expected: No conflict (CANCELLED does not block)
   - Verify: New reservation successfully created

8. **test_activeThenCancelledThenNewActive_sameTime**
   - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Cancel it (status → CANCELLED)
   - Create new ACTIVE reservation: [10:00, 11:00] for workspace-1
   - Expected: New reservation succeeds despite CANCELLED overlapping reservation
   - Verify: Both reservations exist, both retrievable (one ACTIVE, one CANCELLED)

9. **test_multipleActiveReservationsMultipleCancelledNonBlocking**
   - Create 3 ACTIVE reservations for workspace-1 in sequence: [09:00-10:00], [10:00-11:00], [11:00-12:00] (adjacent, no conflicts)
   - Cancel first and second (ACTIVE → CANCELLED)
   - Attempt to create ACTIVE reservation: [09:30, 10:30]
   - Expected: Conflict with third reservation (still ACTIVE at [11:00-12:00]) OR success if no overlap with third
   - Verify: CANCELLED reservations do not contribute to conflict decision

#### Group C: Workspace Isolation (Different Workspaces)

10. **test_differentWorkspaces_sameTime_noConflict**
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-2 (same time, different workspace)
    - Expected: No conflict (conflict detection is per-workspace)
    - Verify: Both reservations successfully exist in store

11. **test_differentWorkspaces_overlapTime_noConflict**
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
    - Create ACTIVE reservation: [10:30, 11:30] for workspace-2 (overlapping time, different workspace)
    - Expected: No conflict
    - Verify: Both reservations successfully exist in store

12. **test_sameWorkspace_conflicts_differentWorkspace_noConflict**
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1 (conflict)
    - Expected: Conflict exception
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-2 (no conflict despite time overlap)
    - Expected: Success
    - Verify: workspace-1 has 1 reservation (first), workspace-2 has 1 reservation (third)

#### Group D: 409 Conflict Exception and Response Code Mapping

13. **test_conflictDetection_throwsConflictException**
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
    - Attempt to create overlapping ACTIVE reservation: [10:30, 11:30] for workspace-1
    - Expected: Exception type indicates 409 Conflict (verify exception type/message)
    - Verify: Exception can be mapped to HTTP 409 status code

14. **test_conflictException_contains409Semantics**
    - Create ACTIVE reservation: [10:00, 11:00] for workspace-1
    - Attempt to create overlapping ACTIVE reservation: [10:30, 11:30] for workspace-1
    - Expected: Thrown exception contains:
      - Error classification (e.g., "CONFLICT")
      - Descriptive message mentioning conflict or overlap
    - Verify: Message is suitable for 409 response body

#### Group E: Concurrency - Race-Free Conflict Detection

15. **test_concurrentCreates_overlappingReservations_onlyOneSucceeds**
    - Two threads attempt to concurrently create overlapping ACTIVE reservations for workspace-1
    - Thread 1: POST [10:00, 11:00]
    - Thread 2: POST [10:30, 11:30] (overlaps with Thread 1)
    - Expected: Race-free guarantee — one succeeds, one receives 409 Conflict
    - Verify: Exactly one reservation in store, one thread got success, one got conflict

16. **test_concurrentCreates_threeThreadsOverlappingReservations_onlyOneSucceeds**
    - Three threads attempt to concurrently create overlapping ACTIVE reservations for workspace-1
    - Thread 1: POST [10:00, 11:00]
    - Thread 2: POST [10:30, 11:30] (overlaps with Thread 1)
    - Thread 3: POST [10:15, 10:45] (overlaps with Thread 1)
    - Expected: Exactly one succeeds, two receive 409 Conflict
    - Verify: Only one reservation in store, exactly one thread succeeded

17. **test_concurrentCreates_nonOverlappingReservations_allSucceed**
    - Two threads attempt to concurrently create non-overlapping ACTIVE reservations for workspace-1
    - Thread 1: POST [10:00, 11:00]
    - Thread 2: POST [11:00, 12:00] (adjacent, not overlapping)
    - Expected: Both succeed (no race condition, no false conflicts)
    - Verify: Both reservations in store, both threads succeeded

18. **test_concurrentCreates_differentWorkspaces_allSucceed**
    - Two threads attempt to concurrently create same-time ACTIVE reservations for different workspaces
    - Thread 1: POST [10:00, 11:00] for workspace-1
    - Thread 2: POST [10:00, 11:00] for workspace-2
    - Expected: Both succeed (different workspace, no interference)
    - Verify: Both reservations in store, both threads succeeded

19. **test_concurrentCreates_mixedScenario_correctConflictDetection**
    - Four threads, workspace-1:
      - Thread 1: POST [10:00, 11:00] (should succeed)
      - Thread 2: POST [10:30, 11:30] (overlap with Thread 1, should conflict)
      - Thread 3: POST [11:00, 12:00] (adjacent to first attempt, should succeed)
      - Thread 4: POST [12:00, 13:00] (no conflict, should succeed)
    - Expected: Three succeed (Threads 1, 3, 4), one conflicts (Thread 2)
    - Verify: Store contains exactly 3 reservations

20. **test_concurrentCreates_with_cancellation_noCrash**
    - Thread A: Create ACTIVE reservation [10:00, 11:00]
    - Thread B: Wait for A to complete, then create ACTIVE reservation [11:00, 12:00] (adjacent, should succeed)
    - Thread C: Concurrently cancel Thread A's reservation
    - Thread D: Concurrently create ACTIVE reservation [10:00, 11:00] (should succeed now, since cancelled)
    - Expected: No crashes, deadlocks, or data corruption
    - Verify: Final state is consistent (3 reservations: B and D ACTIVE, A CANCELLED)

#### Group F: Store API for Conflict Detection (Method Contract)

21. **test_store_provides_conflict_checking_capability**
    - Store must expose a method to check conflict before create (or integrate conflict check into create)
    - Verify: Store has a method or create() method that:
      - Checks for overlapping ACTIVE reservations in same workspace
      - Raises exception if conflict found
      - Creates reservation if no conflict

---

## Ordered Implementation Changes

**Phase**: RED (Tests Only)

1. **Create conflict detection test class**
   - `src/test/java/com/example/workspace/reservation/store/ReservationStoreConflictTest.java`
   - All 21 test cases (Groups A–F)
   - Tests should initially fail (no conflict detection logic implemented yet)
   - Tests use pure unit test framework (no Spring Boot annotations)

---

## Proposed Test Code Snippets

### ReservationStoreConflictTest.java Structure

```java
package com.example.workspace.reservation.store;

import com.example.workspace.reservation.domain.Reservation;
import com.example.workspace.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReservationStore - Conflict Detection and Concurrency")
class ReservationStoreConflictTest {

    private ReservationStore store;

    @BeforeEach
    void setUp() {
        store = new ReservationStore();
    }

    // Group A: Overlap Rule Definition and Adjacent Reservations

    @Test
    @DisplayName("should detect overlap when new reservation overlaps existing ACTIVE reservation for same workspace")
    void testOverlappingReservationsSameWorkspaceActiveBlocksOverlappingActive() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        
        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        // Attempt overlapping reservation
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);
        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);

        // This should throw a conflict exception (409)
        assertThatThrownBy(() -> store.createWithConflictCheck(res2))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("conflict", "overlap", "workspace");
    }

    @Test
    @DisplayName("should allow adjacent reservations without conflict (10:00-11:00 and 11:00-12:00)")
    void testAdjacentReservationsSameWorkspaceNoConflict() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        
        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        // Adjacent reservation starting exactly when first ends
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 12, 0);
        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);

        // Should succeed (adjacent times do not overlap)
        store.createWithConflictCheck(res2);

        // Verify both exist
        assertThat(store.findById(res1.getId())).isPresent();
        assertThat(store.findById(res2.getId())).isPresent();
    }

    @Test
    @DisplayName("should verify overlap rule: s1 < e2 AND s2 < e1 for conflict")
    void testOverlapRulePredicateS1LessE2AndS2LessE1() throws Exception {
        // Test case 1: [10:00, 11:00] vs [11:00, 12:00]
        // s1 (10:00) < e2 (12:00) = true
        // s2 (11:00) < e1 (11:00) = false
        // Result: NO overlap
        LocalDateTime s1_1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime e1_1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime s2_1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime e2_1 = LocalDateTime.of(2025, 9, 7, 12, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", s1_1, e1_1);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-1", "user-2", s2_1, e2_1);
        store.createWithConflictCheck(res2); // Should succeed

        // Test case 2: [10:00, 11:00] vs [10:30, 11:30]
        // s1 (10:00) < e2 (11:30) = true
        // s2 (10:30) < e1 (11:00) = true
        // Result: OVERLAP
        store = new ReservationStore(); // Reset
        LocalDateTime s1_2 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime e1_2 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime s2_2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime e2_2 = LocalDateTime.of(2025, 9, 7, 11, 30);

        Reservation res3 = Reservation.create("workspace-1", "user-1", s1_2, e1_2);
        store.create(res3);

        Reservation res4 = Reservation.create("workspace-1", "user-2", s2_2, e2_2);
        assertThatThrownBy(() -> store.createWithConflictCheck(res4))
            .isInstanceOf(ConflictException.class);
    }

    // Group B: ACTIVE vs CANCELLED Blocking Behavior

    @Test
    @DisplayName("should not block new ACTIVE reservation when CANCELLED reservation overlaps")
    void testCancelledReservationDoesNotBlockNewActiveReservationSameTime() throws Exception {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        // Create and cancel
        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        Reservation created1 = store.create(res1);
        store.cancel(created1.getId());

        // Verify cancelled
        assertThat(store.findById(created1.getId()).get().getStatus())
            .isEqualTo(ReservationStatus.CANCELLED);

        // Create new ACTIVE at exact same time - should succeed
        Reservation res2 = Reservation.create("workspace-1", "user-2", start, end);
        store.createWithConflictCheck(res2); // Should not throw

        assertThat(store.findById(res2.getId())).isPresent();
        assertThat(store.findById(res2.getId()).get().getStatus())
            .isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("should not block new ACTIVE reservation when CANCELLED reservation has overlapping time")
    void testCancelledReservationDoesNotBlockNewActiveReservationOverlap() throws Exception {
        // Create and cancel
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        Reservation created1 = store.create(res1);
        store.cancel(created1.getId());

        // Create overlapping ACTIVE - should succeed because CANCELLED doesn't block
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);
        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);
        store.createWithConflictCheck(res2); // Should not throw

        assertThat(store.findById(res2.getId())).isPresent();
    }

    // Group C: Workspace Isolation

    @Test
    @DisplayName("should allow same-time ACTIVE reservations for different workspaces (no conflict)")
    void testDifferentWorkspacesSameTimeNoConflict() throws Exception {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-2", "user-2", start, end);
        store.createWithConflictCheck(res2); // Should not throw

        assertThat(store.findById(res1.getId())).isPresent();
        assertThat(store.findById(res2.getId())).isPresent();
    }

    @Test
    @DisplayName("should allow overlapping ACTIVE reservations for different workspaces")
    void testDifferentWorkspacesOverlapTimeNoConflict() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-2", "user-2", start2, end2);
        store.createWithConflictCheck(res2); // Should not throw

        assertThat(store.findById(res1.getId())).isPresent();
        assertThat(store.findById(res2.getId())).isPresent();
    }

    // Group D: 409 Conflict Exception

    @Test
    @DisplayName("should throw ConflictException on overlap (409 semantics)")
    void testConflictDetectionThrowsConflictException() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);

        assertThatThrownBy(() -> store.createWithConflictCheck(res2))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should include conflict details in exception")
    void testConflictExceptionContains409Semantics() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);

        assertThatThrownBy(() -> store.createWithConflictCheck(res2))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("conflict", "overlap", "workspace");
    }

    // Group E: Concurrency - Race-Free Conflict Detection

    @Test
    @DisplayName("concurrent creates with overlapping times should result in exactly one success and one conflict")
    void testConcurrentCreatesOverlappingReservationsOnlyOneSucceeds() throws InterruptedException {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-1", start1, end1);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-2", start2, end2);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent creates with non-overlapping adjacent times should both succeed")
    void testConcurrentCreatesNonOverlappingReservationsAllSucceed() throws InterruptedException {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 12, 0);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-1", start1, end1);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-2", start2, end2);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(conflictCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("concurrent creates for different workspaces should both succeed regardless of time overlap")
    void testConcurrentCreatesDifferentWorkspacesAllSucceed() throws InterruptedException {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        AtomicInteger successCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-1", start, end);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-2", "user-2", start, end);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(successCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrent creates with three overlapping attempts should result in one success and two conflicts")
    void testConcurrentCreatesThreeThreadsOverlappingOnlyOneSucceeds() throws InterruptedException {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);
        LocalDateTime start3 = LocalDateTime.of(2025, 9, 7, 10, 15);
        LocalDateTime end3 = LocalDateTime.of(2025, 9, 7, 10, 45);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(3);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-1", start1, end1);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-2", start2, end2);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-3", start3, end3);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrent mixed scenario: some overlap, some adjacent, should detect conflicts correctly")
    void testConcurrentCreatesMixedScenarioCorrectConflictDetection() throws InterruptedException {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 30);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 30);
        LocalDateTime start3 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime end3 = LocalDateTime.of(2025, 9, 7, 12, 0);
        LocalDateTime start4 = LocalDateTime.of(2025, 9, 7, 12, 0);
        LocalDateTime end4 = LocalDateTime.of(2025, 9, 7, 13, 0);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(4);

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-1", start1, end1);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-2", start2, end2);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-3", start3, end3);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        Thread t4 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-1", "user-4", start4, end4);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception", e);
            }
        });

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t1.join();
        t2.join();
        t3.join();
        t4.join();

        // Expected: 3 succeed (T1, T3, T4), 1 conflict (T2)
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(conflictCount.get()).isEqualTo(1);
    }
}
```

**Supporting Exception Class** (stub for test compilation):

```java
// src/main/java/com/example/workspace/reservation/exception/ConflictException.java
package com.example.workspace.reservation.exception;

public class ConflictException extends RuntimeException {
    private final String errorCode;

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ConflictException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

**Store Method Signature** (test expectations for GREEN phase):

The test expects `ReservationStore` to expose a method similar to:

```java
/**
 * Create a reservation with conflict detection.
 * 
 * @param reservation the reservation to create
 * @return the created reservation
 * @throws ConflictException if an ACTIVE reservation exists for the same workspace with overlapping time
 */
public Reservation createWithConflictCheck(Reservation reservation) throws ConflictException {
    // Implementation in GREEN phase
}
```

---

## Verification Command and Expected RED Evidence

**Verification Command**:
```bash
./mvnw test -Dtest=ReservationStoreConflictTest
```

**Expected Outcome (RED Phase)**:
- **21 tests defined** in `ReservationStoreConflictTest`
- **All tests fail initially** (no conflict detection logic implemented yet)
- Maven compilation succeeds (test code is valid Java)
- Test runner output shows test count, failure count, and clear failure messages indicating:
  - `ConflictException` class does not exist or method `createWithConflictCheck` not found
  - Tests attempting to create overlapping reservations succeed when they should fail
  - Tests fail because no conflict detection exists
- No production conflict detection logic
- No 409 exception handler or response mapping

**Example Expected Output** (excerpt):
```
[INFO] --- maven-surefire-plugin:... ---
[INFO] Running com.example.workspace.reservation.store.ReservationStoreConflictTest

[ERROR] FAILURE: testOverlappingReservationsSameWorkspaceActiveBlocksOverlappingActive
[ERROR]  Cannot find method createWithConflictCheck in ReservationStore

[ERROR] FAILURE: testAdjacentReservationsSameWorkspaceNoConflict
[ERROR]  Cannot find method createWithConflictCheck in ReservationStore

[ERROR] Tests run: 21, Failures: 21, Skipped: 0

[INFO] BUILD FAILURE
```

**Evidence Captured**:
- Test file path and class name: `src/test/java/com/example/workspace/reservation/store/ReservationStoreConflictTest.java`
- Test method names and @DisplayName annotations
- Maven output showing test execution and failure count
- Test expectations for conflict detection logic
- Test expectations for concurrency-safe behavior
- No production conflict detection implementation

---

## Test Execution Notes

### Running Conflict Tests Only

**Full conflict test suite**:
```bash
./mvnw test -Dtest=ReservationStoreConflictTest
```

**Single test**:
```bash
./mvnw test -Dtest=ReservationStoreConflictTest#testOverlapRulePredicateS1LessE2AndS2LessE1
```

**Running all tests (including prior milestones)**:
```bash
./mvnw test
```

### Test Framework Details

- **Framework**: JUnit 5 (from spring-boot-starter-test)
- **Spring Test**: Not used (pure unit tests, not Spring Boot)
- **Concurrency Utilities**: java.util.concurrent (CyclicBarrier, CountDownLatch, ExecutorService, AtomicInteger)
- **JSON Assertion**: N/A (no JSON in unit tests)
- **Assertions**: AssertJ

### Test Structure Pattern

Each test follows:
1. Arrange: Create reservations with specific times and workspace IDs
2. Act: Attempt to create overlapping or non-overlapping reservations, or concurrent creates
3. Assert: Verify exception type (ConflictException) or success
4. Assert: Verify store state (contains expected number of reservations, correct status)

---

## Exclusions - What This Phase Does NOT Implement

**Explicitly Excluded** (per Milestone 5 scope):

1. **Conflict Detection Logic**
   - No overlap predicate implementation
   - No conflict checking in store
   - No query for existing reservations by workspace and time
   - (This is Milestone 6 - GREEN)

2. **ConflictException Class**
   - Tests assume this exception exists for compilation and error type checking
   - Implementation details are flexible (custom exception or standard exception)
   - (This is Milestone 6 - GREEN)

3. **409 Conflict Response Handler**
   - No HTTP @ExceptionHandler for 409
   - No response mapping
   - (This is Milestone 6 - GREEN and Milestone 8 - GREEN)

4. **HTTP Endpoint Integration**
   - No controller changes
   - No REST API testing at HTTP layer
   - (This is Milestone 7–8)

5. **Store Internal Implementation**
   - No requirement on locking strategy (synchronized, ReentrantReadWriteLock, atomic operations, etc.)
   - Concurrency mechanism is implementation detail (tested by behavior, not implementation)

---

## Success and Failure Criteria

### RED Phase Success Criteria

✓ **Success** means:
1. All 21 tests compile and are runnable via `./mvnw test -Dtest=ReservationStoreConflictTest`
2. All 21 tests **fail initially** (conflict detection not implemented, method not found, or logic returns incorrect result)
3. Test output clearly documents expected conflict detection behavior:
   - Overlap rule: `s1 < e2 AND s2 < e1`
   - Adjacent reservations do not conflict
   - ACTIVE blocks ACTIVE overlaps
   - CANCELLED does not block
   - Different workspaces do not interfere
   - Concurrent creates are race-free
   - 409 semantics (ConflictException)
4. No production conflict detection code exists
5. No 409 exception handler exists
6. Tests use pure unit test framework (no Spring Boot annotations)
7. Concurrency tests use CyclicBarrier or similar synchronization primitives
8. All test code follows naming conventions and includes @DisplayName annotations
9. Tests are independent and can run in any order
10. Store API contract is clear (tests define expected method signatures)

✗ **Failure** means:
- Tests pass before production implementation (cart before horse)
- Tests only document, don't verify
- Conflict detection logic already exists (GREEN phase work started)
- Tests don't use CyclicBarrier or proper concurrency primitives
- Tests fail to compile
- Tests are missing key scenarios (adjacent, CANCELLED, different workspaces, concurrency)
- Tests don't verify exception type or message
- Store method signature is vague or missing

---

## Repository Artifacts Summary

| File | Type | Status | Purpose |
|------|------|--------|---------|
| src/test/java/com/example/workspace/reservation/store/ReservationStoreConflictTest.java | Test | To Create | Conflict detection and concurrency tests (21 test cases) |

---

## Integration with Prior Milestones

**Milestone 1–4 Status**: All passing (33 tests total)

**Milestone 5 RED Status**: To create (21 new tests)

**Expected Test Summary After Milestone 5 RED**:
```
Tests run: 54 (33 from Milestones 1–4 + 21 from Milestone 5)
Failures: 21 (all Milestone 5 tests fail, no conflict detection yet)
Errors: 0
Skipped: 0
```

**After Milestone 6 GREEN**:
```
Tests run: 54 (all pass)
Failures: 0
Errors: 0
Skipped: 0
```

---

## Next Phase

Upon completion and human review of this RED phase (all 21 tests fail, no GREEN implementation):

- **Next Milestone**: Milestone 6 (Conflict Detection and Concurrency - GREEN)
- **Goal**: Implement conflict detection logic in ReservationStore to pass all RED tests
- **Deliverable**: Production code (overlap predicate, conflict checking, ConflictException, concurrency-safe create with conflict check)
- **Authorization**: Human review required before proceeding to GREEN

---

## Approval Status

**Status**: Pending human review  
**Not Approved**: This Implementation Plan is proposed for review. Human authorization is required before executing the RED phase (test creation).

**Reviewer Checklist**:
- [ ] Plan aligns with approved Milestone 5 scope (docs/.ai/Plan.md)
- [ ] Test cases cover overlap rule definition (s1 < e2 AND s2 < e1)
- [ ] Test cases cover adjacent reservations (non-conflicting)
- [ ] Test cases cover ACTIVE blocking ACTIVE
- [ ] Test cases cover CANCELLED not blocking
- [ ] Test cases cover different workspaces (no interference)
- [ ] Test cases cover 409 Conflict response semantics
- [ ] Test cases cover concurrent overlapping creates (race-free guarantee)
- [ ] Test cases cover concurrent non-overlapping creates (both succeed)
- [ ] Test cases cover concurrent creates for different workspaces (both succeed)
- [ ] Test cases cover concurrent mixed scenario (partial overlap, adjacency)
- [ ] Tests use proper concurrency primitives (CyclicBarrier, AtomicInteger, etc.)
- [ ] Tests verify exception type (ConflictException)
- [ ] Tests verify exception message contains conflict details
- [ ] Tests use pure unit test framework (no Spring annotations)
- [ ] All 21 test cases are clearly documented with expected behavior
- [ ] Tests verify store state (number of reservations, status) after operations
- [ ] No production conflict detection code is created (tests only)
- [ ] No 409 exception handler implementation
- [ ] No HTTP endpoint changes (conflict detection is store layer)
- [ ] Exclusions are explicitly documented
- [ ] Plan is ready for implementation without further clarification


