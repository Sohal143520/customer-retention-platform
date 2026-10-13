package com.retention.platform.customer_retention_platform.dto;

public class UploadResponse {
    private String message;
    private int recordsLoaded;

    public UploadResponse() {
    }

    public UploadResponse(String message, int recordsLoaded) {
        this.message = message;
        this.recordsLoaded = recordsLoaded;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRecordsLoaded() {
        return recordsLoaded;
    }

    public void setRecordsLoaded(int recordsLoaded) {
        this.recordsLoaded = recordsLoaded;
    }
}
