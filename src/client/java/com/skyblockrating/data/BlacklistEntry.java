package com.skyblockrating.data;

public class BlacklistEntry {
    private String username;
    private String reason;
    private long timestamp;

    public BlacklistEntry() {}

    public BlacklistEntry(String username, String reason, long timestamp) {
        this.username = username != null ? username.trim() : "";
        this.reason = reason != null ? reason.trim() : "";
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim() : "";
    }

    public String getReason() {
        return reason != null && !reason.isEmpty() ? reason : "No reason provided";
    }

    public void setReason(String reason) {
        this.reason = reason != null ? reason.trim() : "";
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
