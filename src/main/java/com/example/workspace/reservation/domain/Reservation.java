package com.example.workspace.reservation.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class Reservation {
    private final UUID id;
    private final String workspaceId;
    private final String userId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private ReservationStatus status;

    private Reservation(UUID id, String workspaceId, String userId,
                       LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ReservationStatus.ACTIVE;
    }

    public static Reservation create(String workspaceId, String userId,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        return new Reservation(UUID.randomUUID(), workspaceId, userId, startTime, endTime);
    }

    public void cancel() {
        if (this.status == ReservationStatus.ACTIVE) {
            this.status = ReservationStatus.CANCELLED;
        }
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getUserId() {
        return userId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

