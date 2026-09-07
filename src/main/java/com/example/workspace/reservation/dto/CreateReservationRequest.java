package com.example.workspace.reservation.dto;

public class CreateReservationRequest {
    private String workspaceId;
    private String userId;
    private String startTime;
    private String endTime;

    public CreateReservationRequest() {
    }

    public CreateReservationRequest(String workspaceId, String userId, String startTime, String endTime) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}

