package com.skyblockrating.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviewEntry {
    private final String id;
    private final String author;
    private String authorUuid;
    private final String targetPlayer;
    private String targetUuid;
    private final String category;
    private final String subTag;
    private final double stars;
    private final String comment;
    private final long timestamp;
    private int likes;
    private List<String> likedUsers;

    public ReviewEntry(String author, String authorUuid, String targetPlayer, String targetUuid, String category, String subTag, double stars, String comment) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.author = author;
        this.authorUuid = (authorUuid != null) ? authorUuid.replace("-", "").toLowerCase() : null;
        this.targetPlayer = targetPlayer;
        this.targetUuid = (targetUuid != null) ? targetUuid.replace("-", "").toLowerCase() : null;
        this.category = category;
        if ("Safari".equalsIgnoreCase(category)) {
            this.subTag = "";
        } else {
            this.subTag = (subTag != null && !subTag.isEmpty()) ? subTag : "General";
        }
        this.stars = Math.min(5.0, Math.max(1.0, stars));
        this.comment = comment;
        this.timestamp = System.currentTimeMillis();
        this.likes = 0;
        this.likedUsers = new ArrayList<>();
    }

    public ReviewEntry(String author, String targetPlayer, String category, String subTag, double stars, String comment) {
        this(author, null, targetPlayer, null, category, subTag, stars, comment);
    }

    public ReviewEntry(String author, String targetPlayer, String category, double stars, String comment) {
        this(author, null, targetPlayer, null, category, "General", stars, comment);
    }

    public String getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getAuthorUuid() {
        return authorUuid;
    }

    public void setAuthorUuid(String authorUuid) {
        this.authorUuid = (authorUuid != null) ? authorUuid.replace("-", "").toLowerCase() : null;
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    public String getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(String targetUuid) {
        this.targetUuid = (targetUuid != null) ? targetUuid.replace("-", "").toLowerCase() : null;
    }


    public String getCategory() {
        return category;
    }

    public String getSubTag() {
        return subTag;
    }

    public double getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getLikes() {
        return likes;
    }

    private void ensureLikedUsers() {
        if (this.likedUsers == null) {
            this.likedUsers = new ArrayList<>();
        }
    }

    public boolean isLikedBy(String username) {
        if (username == null) return false;
        ensureLikedUsers();
        return likedUsers.contains(username.toLowerCase());
    }

    public boolean toggleLike(String username) {
        if (username == null || username.isEmpty()) return false;
        ensureLikedUsers();
        String key = username.toLowerCase();
        if (likedUsers.contains(key)) {
            likedUsers.remove(key);
            likes = Math.max(0, likes - 1);
            return false;
        } else {
            likedUsers.add(key);
            likes++;
            return true;
        }
    }

    public String getStarsString() {
        int fullStars = (int) Math.floor(stars);
        boolean hasHalf = (stars - fullStars) >= 0.5;
        int emptyStars = 5 - fullStars - (hasHalf ? 1 : 0);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%.1f ", stars));
        for (int i = 0; i < fullStars; i++) {
            sb.append("★");
        }
        if (hasHalf) {
            sb.append("½");
        }
        return sb.toString();
    }

    public boolean isAuthor(String username, String uuid) {
        if (uuid != null && !uuid.isEmpty() && this.authorUuid != null && !this.authorUuid.isEmpty()) {
            return this.authorUuid.replace("-", "").equalsIgnoreCase(uuid.replace("-", ""));
        }
        if (username != null && this.author != null) {
            return this.author.trim().equalsIgnoreCase(username.trim());
        }
        return false;
    }
}
