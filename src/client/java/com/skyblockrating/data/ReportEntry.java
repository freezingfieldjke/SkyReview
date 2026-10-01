package com.skyblockrating.data;

import java.util.UUID;

public class ReportEntry {
    private final String reportId;
    private final String reporter;
    private final String targetPlayer;
    private final ReviewEntry review;
    private final String reason;
    private final long timestamp;
    private final String reportType;
    private final String reportedBio;
    private final String reportedDiscord;

    public ReportEntry(String reporter, String targetPlayer, ReviewEntry review, String reason) {
        this.reportId = UUID.randomUUID().toString().substring(0, 8);
        this.reporter = reporter;
        this.targetPlayer = targetPlayer;
        this.review = review;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        this.reportType = "review";
        this.reportedBio = "";
        this.reportedDiscord = "";
    }

    public ReportEntry(String reporter, String targetPlayer, String bio, String discordTag, String reason) {
        this.reportId = UUID.randomUUID().toString().substring(0, 8);
        this.reporter = reporter;
        this.targetPlayer = targetPlayer;
        this.review = null;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
        this.reportType = "about_me";
        this.reportedBio = bio != null ? bio : "";
        this.reportedDiscord = discordTag != null ? discordTag : "";
    }

    public String getReportId() {
        return reportId;
    }

    public String getReporter() {
        return reporter;
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    public ReviewEntry getReview() {
        return review;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReportType() {
        return reportType != null ? reportType : (isAboutMe() ? "about_me" : "review");
    }

    public String getReportedBio() {
        return reportedBio != null ? reportedBio : "";
    }

    public String getReportedDiscord() {
        return reportedDiscord != null ? reportedDiscord : "";
    }

    public boolean isAboutMe() {
        return "about_me".equalsIgnoreCase(reportType)
            || (reportedBio != null && !reportedBio.isEmpty())
            || (reportedDiscord != null && !reportedDiscord.isEmpty());
    }
}
