package com.skyblockrating.data;

public class BlockedUserEntry {
    private final String username;
    private final String reason;
    private final long timestamp;

    public BlockedUserEntry(String username, String reason, long timestamp) {
        this.username = username != null ? username.trim() : "";
        this.reason = reason != null ? reason.trim() : "Moderator block";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
