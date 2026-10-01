package com.skyblockrating.data;

public class UserProfileEntry {
    private String username;
    private String bio;
    private String discordTag;
    private long updatedAt;

    public UserProfileEntry() {
        this.username = "";
        this.bio = "";
        this.discordTag = "";
        this.updatedAt = 0;
    }

    public UserProfileEntry(String username, String bio, String discordTag, long updatedAt) {
        this.username = username != null ? username.trim() : "";
        this.bio = bio != null ? bio.trim() : "";
        this.discordTag = discordTag != null ? discordTag.trim() : "";
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return username != null ? username : "";
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getBio() {
        return bio != null ? bio : "";
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getDiscordTag() {
        return discordTag != null ? discordTag : "";
    }

    public void setDiscordTag(String discordTag) {
        this.discordTag = discordTag;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
