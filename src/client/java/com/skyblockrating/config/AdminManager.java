package com.skyblockrating.config;

public class AdminManager {
    private static boolean deleteReviewsEnabled = false;
    private static boolean bypassCooldownEnabled = false;

    private static long lastReviewTimestamp = 0;
    private static final long COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes

    public static boolean isDeleteReviewsEnabled() {
        return deleteReviewsEnabled;
    }

    public static void setDeleteReviewsEnabled(boolean enabled) {
        deleteReviewsEnabled = enabled;
    }

    public static boolean isBypassCooldownEnabled() {
        return bypassCooldownEnabled;
    }

    public static void setBypassCooldownEnabled(boolean enabled) {
        bypassCooldownEnabled = enabled;
    }

    public static long getRemainingCooldownSeconds() {
        if (bypassCooldownEnabled || OwnerLock.isOwner()) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastReviewTimestamp;
        if (elapsed >= COOLDOWN_MS) {
            return 0;
        }
        return (COOLDOWN_MS - elapsed + 999L) / 1000L;
    }

    public static boolean canSubmitReview() {
        if (bypassCooldownEnabled || OwnerLock.isOwner()) {
            return true;
        }
        return (System.currentTimeMillis() - lastReviewTimestamp) >= COOLDOWN_MS;
    }

    public static void recordReviewSubmission() {
        lastReviewTimestamp = System.currentTimeMillis();
    }
}
