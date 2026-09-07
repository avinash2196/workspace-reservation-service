package com.example.workspace.reservation.store;

import com.example.workspace.reservation.domain.Reservation;
import com.example.workspace.reservation.domain.ReservationStatus;
import com.example.workspace.reservation.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

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
            .hasMessageContaining("conflict");
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

    @Test
    @DisplayName("should test multiple overlap scenarios")
    void testOverlappingReservationsSameWorkspaceMultipleOverlapScenarios() throws Exception {
        LocalDateTime base = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", base, end);
        store.create(res1);

        // Test exact match: [10:00, 11:00]
        Reservation res2 = Reservation.create("workspace-1", "user-2", base, end);
        assertThatThrownBy(() -> store.createWithConflictCheck(res2))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("should allow non-adjacent non-overlapping reservations")
    void testNonAdjacentNonOverlappingReservationsSameWorkspaceNoConflict() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 12, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 13, 0);

        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        store.create(res1);

        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);
        store.createWithConflictCheck(res2); // Should not throw

        assertThat(store.findById(res1.getId())).isPresent();
        assertThat(store.findById(res2.getId())).isPresent();
    }

    // Group B: ACTIVE vs CANCELLED Blocking Behavior

    @Test
    @DisplayName("should not block new ACTIVE reservation when CANCELLED reservation overlaps same time")
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

    @Test
    @DisplayName("should allow new ACTIVE reservation after cancelling existing ACTIVE at same time")
    void testActiveThenCancelledThenNewActive() throws Exception {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        // Create and cancel
        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        Reservation created1 = store.create(res1);
        store.cancel(created1.getId());

        // Create new at same time
        Reservation res2 = Reservation.create("workspace-1", "user-2", start, end);
        store.createWithConflictCheck(res2);

        // Both should exist
        assertThat(store.findById(created1.getId())).isPresent();
        assertThat(store.findById(created1.getId()).get().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(store.findById(res2.getId())).isPresent();
        assertThat(store.findById(res2.getId()).get().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    @DisplayName("should not block new ACTIVE when multiple CANCELLED reservations exist")
    void testMultipleActiveReservationsMultipleCancelledNonBlocking() throws Exception {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 9, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start3 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime end3 = LocalDateTime.of(2025, 9, 7, 12, 0);

        // Create 3 adjacent ACTIVE reservations
        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        Reservation created1 = store.create(res1);

        Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);
        Reservation created2 = store.create(res2);

        Reservation res3 = Reservation.create("workspace-1", "user-3", start3, end3);
        store.create(res3);

        // Cancel first and second
        store.cancel(created1.getId());
        store.cancel(created2.getId());

        // Try to create new - should succeed (no active overlaps)
        LocalDateTime newStart = LocalDateTime.of(2025, 9, 7, 9, 30);
        LocalDateTime newEnd = LocalDateTime.of(2025, 9, 7, 10, 30);
        Reservation newRes = Reservation.create("workspace-1", "user-4", newStart, newEnd);

        // This should succeed or fail based on whether it overlaps with the only ACTIVE reservation (res3)
        // Since cancelled ones don't block, this would only conflict if it overlaps [11:00-12:00]
        // [09:30-10:30] does not overlap [11:00-12:00], so should succeed
        store.createWithConflictCheck(newRes);
        assertThat(store.findById(newRes.getId())).isPresent();
    }

    // Group C: Workspace Isolation (Different Workspaces)

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

    @Test
    @DisplayName("should detect conflict in same workspace but allow same time in different workspace")
    void testSameWorkspaceConflictsDifferentWorkspaceNoConflict() throws Exception {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        // Create in workspace-1
        Reservation res1 = Reservation.create("workspace-1", "user-1", start, end);
        store.create(res1);

        // Attempt conflict in workspace-1
        Reservation res2 = Reservation.create("workspace-1", "user-2", start, end);
        assertThatThrownBy(() -> store.createWithConflictCheck(res2))
            .isInstanceOf(ConflictException.class);

        // Same time in workspace-2 should succeed
        Reservation res3 = Reservation.create("workspace-2", "user-3", start, end);
        store.createWithConflictCheck(res3);

        assertThat(store.findById(res1.getId())).isPresent();
        assertThat(store.findById(res3.getId())).isPresent();
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
            .hasMessageContaining("conflict");
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                Reservation res = Reservation.create("workspace-2", "user-2", start, end);
                store.createWithConflictCheck(res);
                successCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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
                throw new RuntimeException("Unexpected exception", e);
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

    @Test
    @DisplayName("concurrent creates with cancellation should not crash and handle state correctly")
    void testConcurrentCreatesWithCancellationNoCrash() throws InterruptedException {
        LocalDateTime start1 = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end1 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime start2 = LocalDateTime.of(2025, 9, 7, 11, 0);
        LocalDateTime end2 = LocalDateTime.of(2025, 9, 7, 12, 0);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Create first reservation in main thread
        Reservation res1 = Reservation.create("workspace-1", "user-1", start1, end1);
        Reservation created1 = store.create(res1);
        successCount.incrementAndGet();

        CyclicBarrier barrier = new CyclicBarrier(2);

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                // Create adjacent reservation
                Reservation res2 = Reservation.create("workspace-1", "user-2", start2, end2);
                store.createWithConflictCheck(res2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception in t2", e);
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                barrier.await();
                // Cancel first reservation
                store.cancel(created1.getId());
            } catch (Exception e) {
                fail("Unexpected exception in t3", e);
            }
        });

        t2.start();
        t3.start();
        t2.join();
        t3.join();

        // At least 2 operations should succeed without crashing
        assertThat(successCount.get()).isGreaterThanOrEqualTo(2);
    }

    // Group F: Store API for Conflict Detection

    @Test
    @DisplayName("store must provide createWithConflictCheck method")
    void testStoreProvidesConflictCheckingCapability() throws Exception {
        LocalDateTime start = LocalDateTime.of(2025, 9, 7, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 9, 7, 11, 0);

        Reservation res = Reservation.create("workspace-1", "user-1", start, end);

        // Method should exist and be callable
        assertThat(store).isNotNull();
        // Should not throw NoSuchMethodException or similar
        Reservation created = store.createWithConflictCheck(res);
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
    }
}

