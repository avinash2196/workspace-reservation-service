# Implementation Plan: Milestone 6 - Conflict Detection and Concurrency - GREEN

**Milestone**: 6  
**Phase**: GREEN  
**Title**: Conflict Detection and Concurrency - GREEN  
**Status**: Ready for Human Review  
**Not Approved**: This plan requires human review before authorization to proceed with production implementation.

---

## Traceability

**Approved Baseline**:
- docs/requirements.md § Create Reservation (Conflict Detection, Concurrency Guarantee)
- docs/.ai/Plan.md § Milestone 6: Conflict Detection and Concurrency - GREEN
- docs/.ai/005_Implementation_Plan_Conflict_Concurrency_RED.md (all 21 tests pass acceptance criteria)
- docs/.ai/0_API_Contract.md § Conflict Condition, Concurrency Guarantee, 409 Conflict Response

**This Plan Addresses**: Implementation of overlap detection, conflict prevention, concurrency-safe reservation creation, and 409 Conflict response at the application/store layer to satisfy all Milestone 5 RED tests.

---

## Current Repository State

**Completed (Milestones 1–5)**:
- Domain layer: `Reservation.java` (immutable, created with ACTIVE status), `ReservationStatus.java`
- Store layer: `ReservationStore.java` with thread-safe ConcurrentHashMap and stub `createWithConflictCheck` method
- Validation layer: `ReservationValidator.java`
- Exception layer: `ConflictException.java` (already created in RED for compilation)
- Test layer: 54 tests total (33 passing from Milestones 1–4, 21 failing from Milestone 5 RED)
- All tests execute via `./mvnw test`

**RED Phase Evidence**:
- Milestone 5 test execution shows:
  - 21 new conflict detection tests created in `ReservationStoreConflictTest.java`
  - 9 tests failing, demonstrating missing conflict detection logic:
    - 6 tests for overlap detection fail because no exception is thrown
    - 3 tests for concurrent race-free creation fail (2-4 concurrent overlaps succeed when only 1 should)
  - 12 tests passing (adjacent, different workspaces, CANCELLED not blocking, non-overlapping scenarios)

**Incomplete (Blocking Final Success)**:
- Conflict detection logic (overlap predicate implementation)
- Concurrency-safe conflict checking (synchronized/locked check-and-create)
- Filtering to include ACTIVE reservations only
- Workspace isolation via per-workspace query
- Exception throwing on conflict (ConflictException with message)

---

## Milestone 6 Scope: Conflict Detection and Concurrency - GREEN

**Goal**: Implement conflict detection and concurrency-safe reservation creation to pass all Milestone 5 RED tests.

**Success Criteria**:
- All 54 tests pass (33 from Milestones 1–4, 21 from Milestone 5)
- Concurrency tests verify exactly one success and one conflict in overlapping scenarios
- Overlap detection follows rule: `s1 < e2 AND s2 < e1`
- Adjacent reservations ([10:00, 11:00] and [11:00, 12:00]) do NOT conflict
- ACTIVE reservations block overlapping ACTIVE reservations for same workspace
- CANCELLED reservations do NOT block any reservation
- Different workspaces do NOT interfere with overlap detection
- Concurrency-safe check-and-create prevents race conditions
- 409 Conflict behavior (ConflictException thrown and caught by HTTP layer)
- No unrelated refactoring or scope creep
- Production code only (no test changes)

**Exclusions**:
- No HTTP controller or endpoint integration (that is Milestone 7–8)
- No validation changes (those remain in ReservationValidator)
- No Reservation domain changes (already correctly initialized with ACTIVE status)
- No test implementation (all tests already exist from RED)

---

## Files to Modify / Create

### 1. `src/main/java/com/example/workspace/reservation/store/ReservationStore.java`

**Status**: Modify  
**Priority**: Critical (GREEN phase core implementation)  
**Current Content**: Stub `createWithConflictCheck` method (from RED phase)  
**Changes Required**:
- Implement overlap detection predicate (private helper method)
- Implement conflict checking logic (find existing ACTIVE reservations for workspace)
- Implement `createWithConflictCheck` with atomic conflict check-and-create
- Concurrency safety: synchronized method or equivalent locking

---

## Proposed Production Code

### ReservationStore.java (Complete Implementation)

```java
package com.example.workspace.reservation.store;

import com.example.workspace.reservation.domain.Reservation;
import com.example.workspace.reservation.domain.ReservationStatus;
import com.example.workspace.reservation.exception.ConflictException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ReservationStore {
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();

    public Reservation create(Reservation reservation) {
        reservations.put(reservation.getId(), reservation);
        return reservation;
    }

    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }

    public void cancel(UUID id) {
        Optional<Reservation> reservation = findById(id);
        reservation.ifPresent(Reservation::cancel);
    }

    /**
     * Create a reservation with conflict detection.
     * 
     * Atomically checks for overlapping ACTIVE reservations in the same workspace
     * and creates the reservation if no conflict exists. If a conflict is detected,
     * throws ConflictException and does not create the reservation.
     * 
     * This method is synchronized to ensure atomic check-and-create behavior,
     * preventing race conditions where concurrent requests could both succeed
     * when creating overlapping ACTIVE reservations for the same workspace.
     * 
     * @param reservation the reservation to create
     * @return the created reservation with status ACTIVE
     * @throws ConflictException if an ACTIVE reservation exists for the same workspace 
     *                          with overlapping time range
     */
    public synchronized Reservation createWithConflictCheck(Reservation reservation) 
            throws ConflictException {
        
        // Check for conflict: find any ACTIVE reservation for the same workspace that overlaps
        boolean hasConflict = hasConflictingReservation(
            reservation.getWorkspaceId(),
            reservation.getStartTime(),
            reservation.getEndTime()
        );
        
        if (hasConflict) {
            throw new ConflictException(
                "CONFLICT",
                "An ACTIVE reservation already exists for workspace '" + 
                reservation.getWorkspaceId() + "' during the requested time range [" + 
                reservation.getStartTime() + ", " + reservation.getEndTime() + "]"
            );
        }
        
        // No conflict: safe to create
        return create(reservation);
    }

    /**
     * Check if a conflicting reservation exists for the given workspace and time range.
     * 
     * A conflict exists if:
     * 1. An ACTIVE reservation exists for the same workspace AND
     * 2. The time ranges overlap per the rule: newStart < existingEnd AND existingStart < newEnd
     * 
     * CANCELLED reservations do not contribute to conflicts.
     * 
     * @param workspaceId the workspace identifier
     * @param newStart the start time of the potential new reservation
     * @param newEnd the end time of the potential new reservation
     * @return true if a conflicting ACTIVE reservation exists, false otherwise
     */
    private boolean hasConflictingReservation(String workspaceId, LocalDateTime newStart, LocalDateTime newEnd) {
        return reservations.values().stream()
            .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
            .filter(r -> workspaceId.equals(r.getWorkspaceId()))
            .anyMatch(r -> overlaps(newStart, newEnd, r.getStartTime(), r.getEndTime()));
    }

    /**
     * Determine if two time ranges overlap.
     * 
     * Overlap rule per requirements: s1 < e2 AND s2 < e1
     * - s1 (newStart) must be strictly before e2 (existingEnd)
     * - s2 (existingStart) must be strictly before e1 (newEnd)
     * 
     * Adjacent times (one ends exactly when the other starts) do NOT overlap.
     * Example: [10:00, 11:00] and [11:00, 12:00] do not overlap.
     * 
     * @param s1 start time of first range (new reservation)
     * @param e1 end time of first range (new reservation)
     * @param s2 start time of second range (existing reservation)
     * @param e2 end time of second range (existing reservation)
     * @return true if ranges overlap, false if they are adjacent or non-overlapping
     */
    private boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }
}
```

---

## Implementation Details

### Overlap Detection Logic

**Predicate Implementation**:
- Private method `overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2)` 
- Rule: `s1 < e2 AND s2 < e1` (uses `isBefore()` for strict comparison)
- Adjacent times (equal endpoints) return false (e.g., 11:00 is NOT before 11:00)

**Examples**:
- `[10:00, 11:00]` vs `[10:30, 11:30]`: 10:00 < 11:30 AND 10:30 < 11:00 → TRUE (overlap)
- `[10:00, 11:00]` vs `[11:00, 12:00]`: 10:00 < 12:00 AND 11:00 < 11:00 → FALSE (adjacent, no overlap)
- `[10:00, 11:00]` vs `[12:00, 13:00]`: 10:00 < 13:00 AND 12:00 < 11:00 → FALSE (gap, no overlap)

### Active-Only Filtering

**Implementation**:
- Method `hasConflictingReservation` filters reservations using `.filter(r -> r.getStatus() == ReservationStatus.ACTIVE)`
- Only ACTIVE reservations can block new reservations
- CANCELLED reservations are ignored during conflict detection

### Workspace Isolation

**Implementation**:
- Method `hasConflictingReservation` filters by workspace using `.filter(r -> workspaceId.equals(r.getWorkspaceId()))`
- Each workspace maintains independent conflict detection
- Concurrent creates for different workspaces do not interfere

### Atomic Concurrency-Safe Check-and-Create

**Implementation**:
- Method `createWithConflictCheck` is `synchronized`
- Atomically:
  1. Checks for conflicting ACTIVE reservations via `hasConflictingReservation`
  2. If conflict found, throws `ConflictException` (no state change)
  3. If no conflict, calls `create()` to store reservation and returns it
- Synchronization ensures only one thread can execute the entire check-and-create operation
- Prevents race condition where multiple threads could both see no conflict and both create overlapping reservations

**Why Synchronized Works**:
- ReservationStore is a @Component (Spring singleton)
- The synchronized method lock is held for the entire conflict check operation
- Subsequent threads wait for the lock, then perform their own check-and-create atomically
- This prevents the race condition identified in Milestone 5 RED tests (expecting 1 success, getting 2-4)

### Conflict Exception Throwing

**Implementation**:
- ConflictException (already created in RED) is thrown with:
  - `errorCode`: "CONFLICT" (maps to 409 HTTP status)
  - `message`: Descriptive message including workspace ID and time range
- Exception is caught by HTTP layer (Milestone 8 GREEN) and mapped to 409 response

### Initial ACTIVE State

**Implementation**:
- No changes required; already handled in `Reservation.create()` which sets `status = ReservationStatus.ACTIVE`
- All new reservations are created with ACTIVE status

---

## Verification and Testing

### RED Tests Passing Criteria

After implementation, all 54 tests must pass:

**Overlap Detection Tests** (6 tests should now pass):
- `test_overlappingReservationsSameWorkspaceActiveBlocksOverlappingActive` - overlap detected, exception thrown
- `test_overlappingReservationsSameWorkspaceMultipleOverlapScenarios` - multiple overlap scenarios detected
- `test_overlapRulePredicateS1LessE2AndS2LessE1` - predicate logic verified
- `test_conflictDetectionThrowsConflictException` - ConflictException type verified
- `test_conflictExceptionContains409Semantics` - exception message verified
- `test_sameWorkspaceConflictsDifferentWorkspaceNoConflict` - workspace isolation verified

**Concurrency Tests** (3 tests should now pass):
- `test_concurrentCreatesOverlappingReservationsOnlyOneSucceeds` - exactly 1 success, 1 conflict (atomic behavior)
- `test_concurrentCreatesThreeThreadsOverlappingOnlyOneSucceeds` - exactly 1 success, 2 conflicts (3 threads)
- `test_concurrentCreatesMixedScenarioCorrectConflictDetection` - correct conflict count in mixed scenario

### Verification Command

```bash
./mvnw test
```

**Expected Output**:
```
[INFO] Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Specific Concurrency Test Verification

**Test**: `test_concurrentCreatesOverlappingReservationsOnlyOneSucceeds`
- Two threads attempt concurrent create with [10:00, 11:00] and [10:30, 11:30]
- Thread 1 acquires synchronized lock first, checks (no prior reservation), creates
- Thread 2 waits for lock, then checks (finds Thread 1's reservation), throws ConflictException
- Result: `successCount = 1`, `conflictCount = 1` ✓

**Test**: `test_concurrentCreatesMixedScenarioCorrectConflictDetection`
- Four threads: [10:00, 11:00], [10:30, 11:30], [11:00, 12:00], [12:00, 13:00]
- Race for lock determines order, but final result is deterministic:
  - One of first or second thread succeeds (depending on lock acquisition)
  - Other three succeed (only one overlaps; adjacent and later times don't conflict)
- Result: `successCount = 3`, `conflictCount = 1` ✓

---

## Code Review Checklist

Before implementation, reviewer should verify:

- [ ] Overlap predicate correctly implements `s1 < e2 AND s2 < e1`
- [ ] Adjacent times (equal endpoints) are correctly handled as non-overlapping
- [ ] ACTIVE filtering is present and correct
- [ ] Workspace isolation via workspace ID comparison is correct
- [ ] Conflict exception is thrown with appropriate error code and message
- [ ] `createWithConflictCheck` is synchronized for atomic check-and-create
- [ ] No other changes to ReservationStore (minimal GREEN implementation)
- [ ] No changes to Reservation domain
- [ ] No changes to existing store methods (create, findById, cancel)
- [ ] No validation logic in store (remains in ReservationValidator)
- [ ] No HTTP concerns in store implementation
- [ ] Concurrency mechanism (synchronized) is appropriate for single-service scenario

---

## Integration with Prior Milestones

**Milestones 1–4 (Completed)**: All 33 tests pass, no changes required.

**Milestone 5 (RED - Complete)**: 21 conflict detection tests created and initially failing. This GREEN implementation makes all 21 tests pass.

**Expected Test Summary After Milestone 6 GREEN**:
```
Tests run: 54 (33 from Milestones 1–4 + 21 from Milestone 5)
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

**Downstream Milestones**:
- Milestone 7 (RED): HTTP endpoint tests will call this store method and verify 409 response
- Milestone 8 (GREEN): HTTP layer will catch ConflictException and map it to 409 Conflict response

---

## Concurrency Safety Analysis

### Race Condition Scenario (Without Synchronization)

**Problem**: Without synchronization, two threads could both:
1. Check for conflict (both see empty or non-overlapping reservations)
2. Both create overlapping reservations for same workspace
3. Result: Two ACTIVE reservations overlap for same workspace (violates contract)

**Example**:
```
Thread 1: Check [10:00, 11:00] → no conflict found → [CONTEXT SWITCH]
Thread 2: Check [10:30, 11:30] → no conflict found (Thread 1 hasn't created yet)
Thread 1: Create [10:00, 11:00] ✓
Thread 2: Create [10:30, 11:30] ✓ [BUG: Should have failed]
```

### Solution: Synchronized Check-and-Create

The `synchronized` keyword on `createWithConflictCheck` ensures:
- Only one thread can execute the method at a time
- Conflict check and creation are atomic (indivisible)
- Subsequent threads see the result of prior creations

**Example**:
```
Thread 1: [LOCK] Check [10:00, 11:00] → no conflict → Create [10:00, 11:00] → [UNLOCK]
Thread 2: [WAIT FOR LOCK] ... [LOCK] Check [10:30, 11:30] → conflict found → Throw Exception
```

### ConcurrentHashMap Limitation

While the underlying store uses ConcurrentHashMap for thread-safe individual operations, conflict detection requires checking multiple reservations and making a decision based on the collective state. ConcurrentHashMap does not provide transaction-like semantics across multiple operations. Synchronization at the method level is required to ensure check-and-create atomicity.

---

## Non-Blocking Implementation Details

The following are left flexible and do not block plan approval:

- **Exact Exception Message Wording**: Message text is implementation detail; error code "CONFLICT" is contract.
- **Concurrency Mechanism**: Synchronized method is proposed; alternatives like ReentrantReadWriteLock or atomic operations are also acceptable if they provide the same atomic guarantee.
- **Stream vs Loop**: Implementation uses Java Streams for readability; imperative loops are also acceptable.

---

## Exclusions - What This Phase Does NOT Implement

**Explicitly Excluded** (per Milestone 6 scope):

1. **HTTP Controller Integration**
   - No changes to REST endpoints
   - No request/response mapping
   - (This is Milestone 8 - GREEN)

2. **409 Exception Handler**
   - Store throws ConflictException
   - HTTP layer catches and maps to 409
   - (This is Milestone 8 - GREEN)

3. **Validation Changes**
   - Validation remains in ReservationValidator
   - Conflict detection is separate from validation
   - (Milestones 3–4 completed)

4. **Reservation Domain Changes**
   - Reservation class remains immutable
   - ACTIVE status already set at creation time
   - (No changes required)

5. **Test Implementation**
   - All tests already exist from Milestone 5 RED
   - This phase implements production code only
   - (Tests frozen, no modifications)

---

## Success and Failure Criteria

### GREEN Phase Success Criteria

✓ **Success** means:
1. All 54 tests pass via `./mvnw test` (Milestones 1–5)
2. Concurrency tests verify race-free behavior (exactly expected successes/conflicts)
3. Overlap detection follows rule: `s1 < e2 AND s2 < e1`
4. Adjacent reservations do NOT conflict
5. ACTIVE reservations block overlapping ACTIVE for same workspace
6. CANCELLED reservations do NOT block any reservation
7. Different workspaces do NOT interfere
8. ConflictException is thrown with appropriate message
9. No unrelated refactoring or scope creep
10. ReservationStore is the only file modified
11. Production code is minimal and focused
12. Concurrency safety prevents race conditions

✗ **Failure** means:
- Tests still fail after implementation
- Race conditions still exist (concurrent overlaps both succeed)
- Adjacent times incorrectly conflict
- CANCELLED reservations incorrectly block
- Different workspaces interfere with each other
- ConflictException not thrown or caught
- Scope creep (unnecessary refactoring or new methods)
- HTTP layer changes (should be Milestone 8)
- Validation changes (should be Milestones 3–4)

---

## Repository Artifacts Summary

| File | Type | Status | Purpose |
|------|------|--------|---------|
| src/main/java/com/example/workspace/reservation/store/ReservationStore.java | Production | Modify | Implement overlap detection, conflict checking, atomic check-and-create |

---

## Next Phase

Upon completion and human review of this GREEN phase (all 54 tests pass, no race conditions):

- **Next Milestone**: Milestone 7 (HTTP API Endpoints - RED)
- **Goal**: Define test contract for HTTP endpoint behavior and conflict response mapping
- **Deliverable**: Test code in src/test (integration tests for POST, GET, DELETE endpoints)
- **Scope**: HTTP layer tests calling store and verifying 409 response mapping
- **Authorization**: Human review required before proceeding to Milestone 7 RED

---

## Approval Status

**Status**: Pending human review  
**Not Approved**: This Implementation Plan is proposed for review. Human authorization is required before executing the GREEN phase (production implementation).

**Reviewer Checklist**:
- [ ] Plan aligns with approved Milestone 6 scope (docs/.ai/Plan.md)
- [ ] Overlap predicate correctly implements `s1 < e2 AND s2 < e1`
- [ ] Adjacent reservations are correctly handled as non-overlapping
- [ ] ACTIVE filtering ensures only ACTIVE reservations block
- [ ] CANCELLED filtering ensures CANCELLED reservations don't block
- [ ] Workspace isolation logic is correct
- [ ] ConflictException is thrown with appropriate error code and message
- [ ] `createWithConflictCheck` is synchronized for atomic behavior
- [ ] Concurrency analysis shows race condition prevention
- [ ] All 54 tests pass after implementation
- [ ] No HTTP controller changes
- [ ] No validation changes
- [ ] No Reservation domain changes
- [ ] No test modifications
- [ ] No scope creep or unrelated refactoring
- [ ] Proposed code is minimal and focused
- [ ] Integration with Milestones 1–5 is correct
- [ ] Plan is ready for implementation without further clarification

---

## Implementation Checklist for Developer

After human approval, implement in this order:

1. [ ] Read current ReservationStore.java to confirm stub method
2. [ ] Implement `overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2)` private method
3. [ ] Implement `hasConflictingReservation(String workspaceId, LocalDateTime newStart, LocalDateTime newEnd)` private method
4. [ ] Implement `createWithConflictCheck` synchronized method with conflict check and exception throwing
5. [ ] Verify code compiles: `./mvnw clean compile`
6. [ ] Run all tests: `./mvnw test`
7. [ ] Verify all 54 tests pass
8. [ ] Verify no race conditions (concurrent tests show expected counts)
9. [ ] Code review by team
10. [ ] Commit and document completion


