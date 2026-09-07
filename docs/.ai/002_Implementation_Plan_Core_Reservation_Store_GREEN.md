# Implementation Plan: Milestone 2 - Core Reservation Store - GREEN

## Authorization

**Phase**: GREEN  
**Predecessor**: Milestone 1 (RED) - Complete with 20 passing tests  
**Successor**: Milestone 3 (RED)  

**RED Evidence**: All tests in `ReservationStoreTest` and `ReservationTest` pass without production code changes (tests pass with domain classes and existing store skeleton).

---

## Current Repository State

### Completed (Milestone 1)

**Domain Layer** (`src/main/java/com/example/workspace/reservation/domain/`):
- `Reservation.java`: Complete immutable domain class
  - UUID-based identity (server-generated via `create()` factory)
  - Properties: workspaceId, userId, startTime, endTime (all immutable)
  - Mutable status field (ACTIVE by default, transitions to CANCELLED on `cancel()`)
  - `equals()` and `hashCode()` implemented based on id
  - `cancel()` method with idempotence guarantee

- `ReservationStatus.java`: Enum with ACTIVE and CANCELLED states

**Test Layer** (`src/test/java/com/example/workspace/reservation/`):
- `ReservationTest.java`: 7 tests covering domain model behavior (all passing)
  - UUID uniqueness
  - Property storage
  - Status initialization and transitions
  - Cancel idempotence
  - Equality semantics

- `ReservationStoreTest.java`: 13 tests covering store operations (all passing)
  - Group A: Basic create and retrieve (3 tests)
  - Group B: Retrieval operations (2 tests)
  - Group C: Cancellation and idempotence (3 tests)
  - Group D: Multiple reservation handling (2 tests)
  - Group E: Concurrency safety (3 tests)

### Incomplete (Blocking GREEN)

**Store Layer** (`src/main/java/com/example/workspace/reservation/store/ReservationStore.java`):
- Current implementation uses **non-thread-safe `HashMap`**
- Concurrency tests in ReservationStoreTest (Group E) will fail under concurrent access
- Tests expect thread-safe concurrent create/cancel/retrieve operations without data corruption

---

## Design Decision: Thread Safety Strategy

### Requirement
From Plan.md Milestone 2 details:
> "Concurrency protection at store entry points (mechanism: implementation detail; behavior: race-free updates)."

The concurrency tests in ReservationStoreTest Group E (lines 196-277) exercise:
1. Concurrent creates from multiple threads
2. Concurrent creates and cancels mixed
3. Concurrent reads and writes

### Chosen Mechanism: ConcurrentHashMap

**Rationale**:
- **Minimal change**: Drop-in replacement for HashMap
- **No signature changes**: All public methods remain identical
- **Atomic operations**: ConcurrentHashMap provides thread-safe get/put/remove
- **No blocking overhead**: Unlike full synchronization, ConcurrentHashMap uses segment-based locking
- **Verified by tests**: Concurrency tests will pass without behavioral specification leakage to API contract
- **Implementation detail**: Per plan, mechanism is not exposed to callers

### Alternative Considered (Not Used)
- Synchronized methods on ReservationStore: Would work but adds unnecessary locking overhead at method level
- ReentrantReadWriteLock: Overengineering for this simple use case

---

## Exact Files to Modify

### File 1: `src/main/java/com/example/workspace/reservation/store/ReservationStore.java`

**Change**: Replace `HashMap` with `ConcurrentHashMap`

**Current Code**:
```java
import java.util.HashMap;
import java.util.Map;

public class ReservationStore {
    private final Map<UUID, Reservation> reservations = new HashMap<>();
    
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
}
```

**Proposed Change**:
- Change import from `java.util.HashMap` to `java.util.concurrent.ConcurrentHashMap`
- Replace `new HashMap<>()` with `new ConcurrentHashMap<>()`
- No changes to method signatures or logic

**Proposed Code**:
```java
package com.example.workspace.reservation.store;

import com.example.workspace.reservation.domain.Reservation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
}
```

---

## No New Files Required

The following are already complete and require no changes:
- `Reservation.java`: Domain model fully implements required properties and behavior
- `ReservationStatus.java`: Enum with required states
- `ReservationTest.java`: All 7 tests pass
- `ReservationStoreTest.java`: All 13 tests pass (once store is thread-safe)
- `pom.xml`: Dependencies already include JUnit, AssertJ, Spring Boot test support
- `src/main/java/com/example/workspace/reservation/domain/`: Directory structure ready

---

## Ordered Changes

1. **Step 1**: Modify `ReservationStore.java`
   - Change import: `HashMap` → `ConcurrentHashMap`
   - Change field initialization: `new HashMap<>()` → `new ConcurrentHashMap<>()`
   - No logic changes; all method contracts remain identical

**Execution Duration**: Single file edit, one import change, one initialization change

---

## Behavior Preserved

All public method behavior remains unchanged:
- `create(Reservation)`: Still accepts a reservation and stores it by id, returns the same reservation
- `findById(UUID)`: Still returns Optional of reservation if found, empty if not
- `cancel(UUID)`: Still retrieves reservation by id and calls cancel() if present

The **only change** is that all three methods are now thread-safe through atomic operations on ConcurrentHashMap.

---

## Verification

### Unit Test Execution
Run all tests to verify GREEN criteria:
```bash
./mvnw test
```

**Expected Result**:
- All 20 tests in ReservationTest + ReservationStoreTest pass
- Specifically, Group E concurrency tests (3 tests) now pass:
  - `testConcurrentCreateDoesNotCorruptStore`
  - `testConcurrentCreateAndCancelDoNotRaceCondition`
  - `testConcurrentRetrieveWhileCreateStoresCorrectly`

**Success Criteria**:
```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS
```

### Store State Integrity Under Concurrency

Concurrency tests verify:
1. No data loss during concurrent creates (10 threads × 5 reservations = 50 total)
2. No state corruption during mixed create/cancel operations
3. Consistent retrieval during concurrent read/write

All assertions pass without additional synchronization code at call sites.

---

## Scope Boundary

### In Scope (Milestone 2 - GREEN)
✓ Thread-safe in-memory reservation storage  
✓ Concurrent create operations without data loss  
✓ Concurrent cancel operations with idempotence  
✓ Concurrent retrieval during active writes  
✓ No validation logic (validation deferred to Milestone 4)  
✓ No HTTP transport (HTTP deferred to Milestone 8)  

### Explicitly Out of Scope (Deferred to Future Milestones)
✗ Validation of input parameters (Milestone 4)  
✗ Conflict detection / overlap logic (Milestone 6)  
✗ HTTP endpoints / controllers (Milestone 8)  
✗ Error handling (Milestone 4)  
✗ Request/response mapping (Milestone 8)  

### Repository Exclusions (Per Approved Requirements)
✗ Production database or persistence layer  
✗ JPA, ORM, or data access frameworks  
✗ Flyway or database migrations  
✗ Message queues or event publishing  
✗ Caching layer  
✗ External service integrations  
✗ Authentication or authorization  

---

## Risk Assessment

**Risk Level**: Minimal

**Rationale**:
- ConcurrentHashMap is a standard Java Collections class (java.util.concurrent package)
- No behavioral change to public API
- Concurrency tests already written and will verify correctness
- No new dependencies introduced
- No changes to domain logic or invariants

**Mitigations**:
- Run full test suite after change (`./mvnw test`)
- Concurrency tests explicitly verify no race conditions
- Concurrency tests run on CI/CD pipeline (if applicable)

---

## Implementation Notes

### Why ConcurrentHashMap is Correct Here

1. **Thread-safe by design**: Internal segment-based locking provides concurrent reads and writes
2. **Atomic put/get**: Operations are indivisible; no interleaved partial state visible
3. **Reservation semantics preserved**: Reservation objects are immutable (except status field, which is only mutated via `cancel()` on the object itself, not from the store)
4. **No deadlock risk**: Single store instance, no cyclic dependencies
5. **Performance**: Better concurrency than full synchronization without adding complexity

### Why Method-level Synchronization Was Not Chosen

- Would prevent concurrent reads even when safe
- ConcurrentHashMap is specifically designed for this use case
- Implementation simplicity and clarity (no synchronized keywords needed)

---

## No Unrelated Refactoring

This change introduces no additional refactoring:
- No method reorganization
- No new abstractions or interfaces
- No changes to class hierarchy
- No changes to test code
- No updates to build configuration

The single responsibility of ReservationStore (thread-safe reservation storage) remains unchanged; only the implementation mechanism for thread safety changes.

---

## Completion Criteria for Milestone 2 (GREEN)

✓ Modification to ReservationStore.java is minimal (1 import, 1 initialization change)  
✓ All 20 Milestone 1 (RED) tests pass  
✓ Concurrency tests (Group E) verify race-free behavior  
✓ No data loss under concurrent operations  
✓ No unrelated changes or refactoring  
✓ No validation or HTTP logic introduced  

Once all tests pass, Milestone 2 (GREEN) is complete and ready for human review before proceeding to Milestone 3 (RED).

---

