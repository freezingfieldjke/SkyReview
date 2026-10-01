package com.skyblockrating.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportRateLimitManager {
    public static final int DAILY_LIMIT = 8;
    public static final long COOLDOWN_MS = 60 * 1000L; // 1 minute
    public static final long DAY_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static final File HISTORY_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/report_history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Long> reportTimestamps = new ArrayList<>();

    static {
        loadHistory();
    }

    private static synchronized void cleanOldTimestamps() {
        long cutoff = System.currentTimeMillis() - DAY_MS;
        reportTimestamps.removeIf(ts -> ts < cutoff);
    }

    public static synchronized void loadHistory() {
        try {
            if (HISTORY_FILE.exists()) {
                try (FileReader reader = new FileReader(HISTORY_FILE)) {
                    Type listType = new TypeToken<List<Long>>() {}.getType();
                    List<Long> loaded = GSON.fromJson(reader, listType);
                    if (loaded != null) {
                        reportTimestamps.clear();
                        reportTimestamps.addAll(loaded);
                    }
                }
            }
            cleanOldTimestamps();
        } catch (Exception ignored) {}
    }

    public static synchronized void saveHistory() {
        try {
            cleanOldTimestamps();
            if (!HISTORY_FILE.getParentFile().exists()) {
                HISTORY_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                GSON.toJson(reportTimestamps, writer);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized long getRemainingCooldownSeconds() {
        if (reportTimestamps.isEmpty()) return 0;
        long last = reportTimestamps.get(reportTimestamps.size() - 1);
        long diff = System.currentTimeMillis() - last;
        if (diff < COOLDOWN_MS) {
            return Math.max(1, (COOLDOWN_MS - diff) / 1000L);
        }
        return 0;
    }

    public static synchronized int getDailyReportCount() {
        cleanOldTimestamps();
        return reportTimestamps.size();
    }

    public static synchronized int getRemainingDailyReports() {
        return Math.max(0, DAILY_LIMIT - getDailyReportCount());
    }

    public static synchronized boolean canSubmitReport() {
        return getRemainingCooldownSeconds() == 0 && getDailyReportCount() < DAILY_LIMIT;
    }

    public static synchronized long getTimeUntilNextAvailableReportMs() {
        cleanOldTimestamps();
        if (reportTimestamps.size() < DAILY_LIMIT) return 0;
        Collections.sort(reportTimestamps);
        long oldest = reportTimestamps.get(0);
        long expiresAt = oldest + DAY_MS;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    public static synchronized void recordReportSubmission() {
        cleanOldTimestamps();
        reportTimestamps.add(System.currentTimeMillis());
        saveHistory();
    }
}
