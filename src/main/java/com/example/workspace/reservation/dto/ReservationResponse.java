package com.example.workspace.reservation.dto;

public class ReservationResponse {
    private String id;
    private String workspaceId;
    private String userId;
    private String startTime;
    private String endTime;
    private String status;

    public ReservationResponse(String id, String workspaceId, String userId,
                             String startTime, String endTime, String status) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }
}

