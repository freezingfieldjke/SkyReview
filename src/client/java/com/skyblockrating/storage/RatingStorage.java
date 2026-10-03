package com.skyblockrating.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.skyblockrating.config.AdminAuthManager;
import com.skyblockrating.data.PrefixTemplateEntry;
import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.data.UserPrefixEntry;
import com.skyblockrating.gui.LeaderboardScreen;
import com.skyblockrating.gui.PlayerRatingEntry;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RatingStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STORAGE_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating_data.json");
    private static final List<ReportEntry> pendingReports = new ArrayList<>();
    private static final Set<String> deletedReviewIds = new HashSet<>();
    private static final List<PrefixTemplateEntry> prefixTemplates = new ArrayList<>();
    private static final Map<String, UserPrefixEntry> userPrefixes = new ConcurrentHashMap<>();

    // Cloudflare Worker API URL
    private static String WORKER_URL = "https://skyreview-worker.skyreview-app.workers.dev";

    public static class StorageWrapper {
        public List<PlayerRatingEntry> players = new ArrayList<>();
        public List<ReportEntry> reports = new ArrayList<>();
        public Set<String> deletedReviewIds = new HashSet<>();
        public List<PrefixTemplateEntry> prefixTemplates = new ArrayList<>();
        public List<UserPrefixEntry> userPrefixes = new ArrayList<>();
        public List<com.skyblockrating.data.BlockedUserEntry> blockedUsers = new ArrayList<>();
        public List<com.skyblockrating.data.UserProfileEntry> userProfiles = new ArrayList<>();
        public boolean modDisabled = false;
        public String disabledReason = "Mod is temporarily disabled for maintenance.";
        public String minModVersion = "0.9.0";
        public String latestModVersion = "0.9.9";
        public String updateUrl = "https://discord.gg/KpVndhhNDr";
    }

    private static volatile boolean modDisabled = false;
    private static volatile String disabledReason = "Mod is temporarily disabled for maintenance.";
    private static volatile boolean modOutdated = false;
    private static volatile String minRequiredVersion = "0.9.0";
    private static volatile String latestVersion = "0.9.9";
    private static volatile String updateUrl = "https://discord.gg/KpVndhhNDr";

    public static boolean isModOutdated() {
        return modOutdated;
    }

    public static String getMinRequiredVersion() {
        return minRequiredVersion;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getUpdateUrl() {
        return updateUrl;
    }

    public static boolean isVersionOlder(String current, String required) {
        if (current == null || required == null) return false;
        try {
            String[] p1 = current.replaceAll("[^0-9.]", "").split("\\.");
            String[] p2 = required.replaceAll("[^0-9.]", "").split("\\.");
            int maxLen = Math.max(p1.length, p2.length);
            for (int i = 0; i < maxLen; i++) {
                int num1 = (i < p1.length && !p1[i].isEmpty()) ? Integer.parseInt(p1[i]) : 0;
                int num2 = (i < p2.length && !p2[i].isEmpty()) ? Integer.parseInt(p2[i]) : 0;
                if (num1 < num2) return true;
                if (num1 > num2) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isModDisabled() {
        return modDisabled || modOutdated;
    }

    public static String getDisabledReason() {
        if (modOutdated) {
            return "Mod version v" + com.skyblockrating.SkyBlockRatingMod.MOD_VERSION + " is outdated! Required: v" + minRequiredVersion + ". Please update via Discord!";
        }
        return (disabledReason != null && !disabledReason.trim().isEmpty())
                ? disabledReason
                : "Mod is temporarily disabled for maintenance.";
    }

    public static void setMaintenanceModeAsync(boolean disabled, String reason, Consumer<Boolean> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("disabled", disabled);
                body.addProperty("reason", (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Mod is temporarily disabled for maintenance.");
                postJson(WORKER_URL + "/api/admin/maintenance", body.toString(), true);
                modDisabled = disabled;
                if (reason != null && !reason.trim().isEmpty()) {
                    disabledReason = reason.trim();
                }
                Minecraft client = Minecraft.getInstance();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(true));
                }
            } catch (Exception e) {
                System.err.println("[SkyReview] Failed to update maintenance mode: " + e.getMessage());
                Minecraft client = Minecraft.getInstance();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(false));
                }
            }
        });
    }

    public static void setWorkerUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            WORKER_URL = url.trim().replaceAll("/+$", "");
        }
    }

    public static String getWorkerUrl() {
        return WORKER_URL;
    }

    public static PlayerRatingEntry getPlayerEntry(String ign) {
        return getPlayerEntry(ign, null);
    }

    public static PlayerRatingEntry getPlayerEntry(String ignOrUuid, String category) {
        if (ignOrUuid == null || ignOrUuid.trim().isEmpty()) return null;
        String cleanTarget = ignOrUuid.trim();
        String cleanUuid = cleanTarget.replace("-", "").toLowerCase();
        String cleanCat = (category != null) ? category.trim() : "";
        List<PlayerRatingEntry> players = com.skyblockrating.gui.LeaderboardScreen.getAllPlayers();
        if (players != null) {
            for (PlayerRatingEntry p : players) {
                boolean match = false;
                if (p.getUuid() != null && (p.getUuid().equalsIgnoreCase(cleanUuid) || p.getUuid().equalsIgnoreCase(cleanTarget))) {
                    match = true;
                } else if (p.getName().equalsIgnoreCase(cleanTarget)) {
                    match = true;
                }

                if (match) {
                    if (cleanCat.isEmpty() || p.getCategory().equalsIgnoreCase(cleanCat)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    public static List<ReportEntry> getPendingReports() {
        return pendingReports;
    }

    public static void addReport(ReportEntry report) {
        if (report == null) return;
        pendingReports.add(report);
        sendReportAsync(report);
    }

    public static void removeReport(String reportId) {
        if (reportId == null) return;
        pendingReports.removeIf(r -> r.getReportId().equalsIgnoreCase(reportId));
        deleteReportAsync(reportId);
    }

    public static void markReviewDeleted(String reviewId) {
        if (reviewId != null && !reviewId.isEmpty()) {
            deletedReviewIds.add(reviewId);
            deleteReviewAsync(reviewId);
        }
    }

    private static final Map<String, ScheduledFuture<?>> pendingLikeTasks = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> pendingLikeStates = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService likeDebounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyReview-LikeDebounce");
        t.setDaemon(true);
        return t;
    });

    /**
     * Debounces like updates: Waits 3 seconds after the user's last click before sending
     * the final network request to Cloudflare Worker, preventing rapid like/unlike spamming.
     */
    public static void scheduleDebouncedLike(String reviewId, String username, boolean isLiked) {
        if (reviewId == null || username == null) return;
        synchronized (pendingLikeTasks) {
            ScheduledFuture<?> existing = pendingLikeTasks.get(reviewId);
            if (existing != null && !existing.isDone()) {
                existing.cancel(false);
            }
            pendingLikeStates.put(reviewId, isLiked);

            ScheduledFuture<?> future = likeDebounceExecutor.schedule(() -> {
                Boolean finalState = pendingLikeStates.remove(reviewId);
                pendingLikeTasks.remove(reviewId);
                if (finalState == null) return;
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("reviewId", reviewId);
                    body.addProperty("username", username);
                    body.addProperty("action", finalState ? "like" : "unlike");
                    postJson(WORKER_URL + "/api/reviews/like", body.toString());
                    System.out.println("[SkyReview] Debounced like sent for review " + reviewId + " -> " + (finalState ? "like" : "unlike"));
                } catch (Exception e) {
                    System.err.println("[SkyReview] Cloudflare Workers debounced like failed: " + e.getMessage());
                }
            }, 3000, TimeUnit.MILLISECONDS);

            pendingLikeTasks.put(reviewId, future);
        }
    }

    public static void toggleLikeAsync(String reviewId, String username) {
        scheduleDebouncedLike(reviewId, username, true);
    }

    public static void reloadOnlineDataAsync(Consumer<Boolean> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<PlayerRatingEntry> players = loadData();
                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        com.skyblockrating.gui.LeaderboardScreen.getAllPlayers().clear();
                        if (players != null) {
                            com.skyblockrating.gui.LeaderboardScreen.getAllPlayers().addAll(players);
                        }
                        com.skyblockrating.gui.LeaderboardScreen.recalculateAllRanks();
                        com.skyblockrating.util.PlayerSkinEntityCache.clearCache();
                        if (callback != null) callback.accept(true);
                    });
                }
            } catch (Exception e) {
                System.err.println("[SkyReview] Error reloading online data: " + e.getMessage());
                Minecraft client = Minecraft.getInstance();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(false));
                }
            }
        });
    }

    public static void sendReviewAsync(ReviewEntry review) {
        if (review == null) return;
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                JsonObject body = new JsonObject();
                body.addProperty("id", review.getId());
                body.addProperty("author", review.getAuthor());
                if (review.getAuthorUuid() != null && !review.getAuthorUuid().isEmpty()) {
                    body.addProperty("authorUuid", review.getAuthorUuid());
                } else if (!uuid.isEmpty()) {
                    body.addProperty("authorUuid", uuid.replace("-", "").toLowerCase());
                }
                body.addProperty("targetPlayer", review.getTargetPlayer());
                if (review.getTargetUuid() != null && !review.getTargetUuid().isEmpty()) {
                    body.addProperty("targetUuid", review.getTargetUuid());
                }
                body.addProperty("category", review.getCategory());
                body.addProperty("subTag", review.getSubTag());
                body.addProperty("stars", review.getStars());
                body.addProperty("comment", review.getComment());
                body.addProperty("timestamp", review.getTimestamp());

                URL url = new URI(WORKER_URL + "/api/reviews").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                applyAdminHeaders(conn, uuid, username);
                applySessionHeaders(conn);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                }
                conn.disconnect();
                System.out.println("[SkyReview] Review sent to Cloudflare Worker! Code: " + code + ", Resp: " + sb.toString());
            } catch (Exception e) {
                System.err.println("[SkyReview] Cloudflare Worker review upload failed: " + e.getMessage());
            }
        });
    }

    public static void sendReportAsync(ReportEntry report) {
        sendReportAsync(report, null, null);
    }

    public static void sendReportAsync(ReportEntry report, Runnable onSuccess, Consumer<String> onFailure) {
        if (report == null) return;
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("reportId", report.getReportId());
                body.addProperty("reporter", report.getReporter());
                body.addProperty("targetPlayer", report.getTargetPlayer());
                body.add("review", GSON.toJsonTree(report.getReview()));
                body.addProperty("reason", report.getReason());
                body.addProperty("timestamp", report.getTimestamp());
                body.addProperty("reportType", report.getReportType());
                body.addProperty("reportedBio", report.getReportedBio());
                body.addProperty("reportedDiscord", report.getReportedDiscord());

                String respStr = postJson(WORKER_URL + "/api/reports", body.toString());
                JsonObject resp = GSON.fromJson(respStr, JsonObject.class);
                boolean success = resp != null && resp.has("success") && resp.get("success").getAsBoolean();

                Minecraft client = Minecraft.getInstance();
                if (success) {
                    System.out.println("[SkyReview] Report sent to Cloudflare Worker & D1 DB!");
                    if (client != null) client.execute(() -> { if (onSuccess != null) onSuccess.run(); });
                    else if (onSuccess != null) onSuccess.run();
                } else {
                    String err = (resp != null && resp.has("error")) ? resp.get("error").getAsString() : "Worker returned error";
                    System.err.println("[SkyReview] Cloudflare Worker report upload error: " + err);
                    if (client != null) client.execute(() -> { if (onFailure != null) onFailure.accept(err); });
                    else if (onFailure != null) onFailure.accept(err);
                }
            } catch (Exception e) {
                System.err.println("[SkyReview] Cloudflare Worker report upload failed: " + e.getMessage());
                Minecraft client = Minecraft.getInstance();
                if (client != null) client.execute(() -> { if (onFailure != null) onFailure.accept(e.getMessage()); });
                else if (onFailure != null) onFailure.accept(e.getMessage());
            }
        });
    }

    public static void verifyAdminAsync(String uuid, String username, Consumer<Boolean> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("uuid", uuid != null ? uuid : "");
                body.addProperty("username", username != null ? username : "");
                body.addProperty("key", AdminAuthManager.getAdminKey());

                String respStr = postJson(WORKER_URL + "/api/admin/verify", body.toString(), true);
                JsonObject resp = GSON.fromJson(respStr, JsonObject.class);
                boolean isAdmin = resp != null && resp.has("isAdmin") && resp.get("isAdmin").getAsBoolean();

                if (callback != null) {
                    callback.accept(isAdmin);
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static List<ReviewEntry> getMyReviews() {
        List<ReviewEntry> result = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) return result;
        String myName = client.getUser().getName().trim().toLowerCase();
        String myUuid = (client.getUser().getProfileId() != null)
                ? client.getUser().getProfileId().toString().replace("-", "").toLowerCase()
                : "";

        List<PlayerRatingEntry> allPlayers = LeaderboardScreen.getAllPlayers();
        if (allPlayers == null) return result;

        for (PlayerRatingEntry p : allPlayers) {
            if (p.getReviews() != null) {
                for (ReviewEntry r : p.getReviews()) {
                    boolean match = false;
                    if (!myUuid.isEmpty() && r.getAuthorUuid() != null) {
                        match = r.getAuthorUuid().replace("-", "").equalsIgnoreCase(myUuid);
                    }
                    if (!match && r.getAuthor() != null) {
                        match = r.getAuthor().trim().equalsIgnoreCase(myName);
                    }
                    if (match) {
                        result.add(r);
                    }
                }
            }
        }
        result.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return result;
    }

    public static void deleteReviewAsync(String reviewId) {
        deleteReviewAsync(reviewId, null, null);
    }

    public static void deleteReviewAsync(String reviewId, Runnable onSuccess, Consumer<String> onFailure) {
        if (reviewId == null) return;
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/reviews/" + reviewId).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);
                applySessionHeaders(conn);

                int code = conn.getResponseCode();
                boolean success = (code >= 200 && code < 300);

                if (success) {
                    deletedReviewIds.add(reviewId);
                    List<PlayerRatingEntry> all = LeaderboardScreen.getAllPlayers();
                    if (all != null) {
                        for (PlayerRatingEntry p : all) {
                            p.removeReview(reviewId);
                        }
                        all.removeIf(p -> p.getReviewsCount() == 0);
                        LeaderboardScreen.recalculateAllRanks();
                        saveLocalOnly(all);
                    }
                    System.out.println("[SkyReview] Deleted review " + reviewId + " from Cloudflare Worker, HTTP " + code);
                    if (client != null) client.execute(() -> { if (onSuccess != null) onSuccess.run(); });
                } else {
                    StringBuilder errSb = new StringBuilder();
                    InputStream errIs = conn.getErrorStream();
                    if (errIs != null) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(errIs, StandardCharsets.UTF_8))) {
                            String l;
                            while ((l = br.readLine()) != null) errSb.append(l);
                        }
                    }
                    String err = errSb.length() > 0 ? errSb.toString() : ("HTTP " + code);
                    System.err.println("[SkyReview] Delete review failed: " + err);
                    if (client != null) client.execute(() -> { if (onFailure != null) onFailure.accept(err); });
                }
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[SkyReview] Delete review failed: " + e.getMessage());
                Minecraft client = Minecraft.getInstance();
                if (client != null) client.execute(() -> { if (onFailure != null) onFailure.accept(e.getMessage()); });
            }
        });
    }

    public static void deleteReportAsync(String reportId) {
        if (reportId == null) return;
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/reports/" + reportId).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);

                int code = conn.getResponseCode();
                System.out.println("[SkyReview] Deleted report " + reportId + " from Cloudflare Worker, HTTP " + code);
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[SkyReview] Delete report failed: " + e.getMessage());
            }
        });
    }

    public static void saveData(List<PlayerRatingEntry> incomingPlayers) {
        if (incomingPlayers == null) incomingPlayers = new ArrayList<>();

        for (PlayerRatingEntry p : incomingPlayers) {
            if (p.getReviews() != null) {
                p.getReviews().removeIf(r -> deletedReviewIds.contains(r.getId()));
            }
        }
        incomingPlayers.removeIf(p -> p.getReviewsCount() == 0);
        saveLocalOnly(incomingPlayers);
    }

    public static List<PlayerRatingEntry> loadData() {
        // Attempt 1: Fetch live data from Cloudflare Worker API
        try {
            Minecraft client = Minecraft.getInstance();
            String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
            String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

            URL url = new URI(WORKER_URL + "/api/data").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            applyAdminHeaders(conn, uuid, username);
            applySessionHeaders(conn);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    StorageWrapper wrapper = GSON.fromJson(reader, StorageWrapper.class);
                    if (wrapper != null && wrapper.players != null) {
                        pendingReports.clear();
                        if (wrapper.reports != null) {
                            pendingReports.addAll(wrapper.reports);
                        }
                        if (wrapper.prefixTemplates != null) {
                            prefixTemplates.clear();
                            prefixTemplates.addAll(wrapper.prefixTemplates);
                        }
                        if (wrapper.userPrefixes != null) {
                            userPrefixes.clear();
                            for (UserPrefixEntry up : wrapper.userPrefixes) {
                                if (up.getUsername() != null) {
                                    userPrefixes.put(up.getUsername().toLowerCase().trim(), up);
                                }
                            }
                        }
                        if (wrapper.blockedUsers != null) {
                            blockedUsers.clear();
                            blockedUsers.addAll(wrapper.blockedUsers);
                        }
                        if (wrapper.userProfiles != null) {
                            userProfiles.clear();
                            for (com.skyblockrating.data.UserProfileEntry up : wrapper.userProfiles) {
                                if (up.getUsername() != null) {
                                    userProfiles.put(up.getUsername().toLowerCase().trim(), up);
                                }
                            }
                        }
                        modDisabled = wrapper.modDisabled;
                        if (wrapper.disabledReason != null && !wrapper.disabledReason.trim().isEmpty()) {
                            disabledReason = wrapper.disabledReason.trim();
                        }
                        if (wrapper.minModVersion != null && !wrapper.minModVersion.trim().isEmpty()) {
                            minRequiredVersion = wrapper.minModVersion.trim();
                            modOutdated = isVersionOlder(com.skyblockrating.SkyBlockRatingMod.MOD_VERSION, minRequiredVersion);
                        }
                        if (wrapper.latestModVersion != null && !wrapper.latestModVersion.trim().isEmpty()) {
                            latestVersion = wrapper.latestModVersion.trim();
                        }
                        if (wrapper.updateUrl != null && !wrapper.updateUrl.trim().isEmpty()) {
                            updateUrl = wrapper.updateUrl.trim();
                        }
                        System.out.println("[SkyReview] Loaded " + wrapper.players.size() + " entries, " + prefixTemplates.size() + " prefix templates, " + userPrefixes.size() + " user prefixes, " + blockedUsers.size() + " blocked users, " + userProfiles.size() + " profiles from Worker! (Maintenance: " + modDisabled + ", Outdated: " + modOutdated + ")");

                        // Cache locally
                        saveLocalOnly(wrapper.players);
                        return wrapper.players;
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[SkyReview] Cloudflare Worker offline/unreachable, using local fallback: " + e.getMessage());
        }

        // Attempt 2: Fallback to local storage file
        return loadDataFromLocalFile();
    }

    public static void fetchOnlineDataAsync(Consumer<List<PlayerRatingEntry>> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            List<PlayerRatingEntry> players = loadData();
            Minecraft client = Minecraft.getInstance();
            if (client != null && callback != null) {
                client.execute(() -> callback.accept(players));
            }
        });
    }

    public static List<PlayerRatingEntry> loadDataFromLocalFile() {
        if (!STORAGE_FILE.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(STORAGE_FILE)) {
            Type wrapperType = new TypeToken<StorageWrapper>() {}.getType();
            StorageWrapper wrapper = GSON.fromJson(reader, wrapperType);

            if (wrapper != null) {
                modDisabled = wrapper.modDisabled;
                if (wrapper.disabledReason != null && !wrapper.disabledReason.trim().isEmpty()) {
                    disabledReason = wrapper.disabledReason.trim();
                }
                if (wrapper.minModVersion != null && !wrapper.minModVersion.trim().isEmpty()) {
                    minRequiredVersion = wrapper.minModVersion.trim();
                    modOutdated = isVersionOlder(com.skyblockrating.SkyBlockRatingMod.MOD_VERSION, minRequiredVersion);
                }
                if (wrapper.latestModVersion != null && !wrapper.latestModVersion.trim().isEmpty()) {
                    latestVersion = wrapper.latestModVersion.trim();
                }
                if (wrapper.updateUrl != null && !wrapper.updateUrl.trim().isEmpty()) {
                    updateUrl = wrapper.updateUrl.trim();
                }
                if (wrapper.deletedReviewIds != null) {
                    deletedReviewIds.addAll(wrapper.deletedReviewIds);
                }
                if (wrapper.reports != null && !wrapper.reports.isEmpty()) {
                    pendingReports.clear();
                    pendingReports.addAll(wrapper.reports);
                }
                if (wrapper.prefixTemplates != null) {
                    prefixTemplates.clear();
                    prefixTemplates.addAll(wrapper.prefixTemplates);
                }
                if (wrapper.userPrefixes != null) {
                    userPrefixes.clear();
                    for (UserPrefixEntry up : wrapper.userPrefixes) {
                        if (up != null && up.getUsername() != null) {
                            userPrefixes.put(up.getUsername().toLowerCase().trim(), up);
                        }
                    }
                }
                if (wrapper.players != null) {
                    return wrapper.players;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    private static void saveLocalOnly(List<PlayerRatingEntry> players) {
        try {
            File configDir = STORAGE_FILE.getParentFile();
            if (!configDir.exists()) configDir.mkdirs();
            StorageWrapper wrapper = new StorageWrapper();
            wrapper.players = players;
            wrapper.reports = pendingReports;
            wrapper.deletedReviewIds = deletedReviewIds;
            wrapper.prefixTemplates = prefixTemplates;
            wrapper.userPrefixes = new ArrayList<>(userPrefixes.values());
            wrapper.modDisabled = modDisabled;
            wrapper.disabledReason = disabledReason;
            wrapper.minModVersion = minRequiredVersion;
            wrapper.latestModVersion = latestVersion;
            wrapper.updateUrl = updateUrl;
            try (FileWriter writer = new FileWriter(STORAGE_FILE)) {
                GSON.toJson(wrapper, writer);
            }
        } catch (Exception ignored) {}
    }

    public static void applyAdminHeaders(HttpURLConnection conn, String uuid, String username) {
        if (uuid != null && !uuid.isEmpty()) conn.setRequestProperty("x-admin-uuid", uuid);
        if (username != null && !username.isEmpty()) conn.setRequestProperty("x-admin-username", username);
        String adminKey = AdminAuthManager.getAdminKey();
        if (!adminKey.isEmpty()) conn.setRequestProperty("x-admin-key", adminKey);
    }

    public static void applySessionHeaders(HttpURLConnection conn) {
        try {
            conn.setRequestProperty("x-mod-version", com.skyblockrating.SkyBlockRatingMod.MOD_VERSION);
            String token = com.skyblockrating.auth.SessionAuthManager.getOrFetchSessionToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("x-session-token", token);
            }
        } catch (Exception ignored) {}
    }

    private static String postJson(String urlStr, String jsonBody) throws Exception {
        return postJson(urlStr, jsonBody, false);
    }

    private static String postJson(String urlStr, String jsonBody, boolean isAdmin) throws Exception {
        URL url = new URI(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (isAdmin || AdminAuthManager.hasAdminKey()) {
            Minecraft client = Minecraft.getInstance();
            String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
            String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";
            applyAdminHeaders(conn, uuid, username);
        }
        applySessionHeaders(conn);



        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        }
        conn.disconnect();
        return sb.toString();
    }

    public static List<PrefixTemplateEntry> getPrefixTemplates() {
        return prefixTemplates;
    }

    public static Map<String, UserPrefixEntry> getUserPrefixes() {
        return userPrefixes;
    }

    public static UserPrefixEntry getUserPrefix(String username) {
        if (username == null) return null;
        String clean = username.trim();
        if (clean.isEmpty()) return null;

        // 1. Exact match (case-insensitive)
        UserPrefixEntry exact = userPrefixes.get(clean.toLowerCase());
        if (exact != null) return exact;

        // 2. Fuzzy match across all userPrefixes (e.g. SanyaMalinkaAa vs sanyamalinkaaa)
        for (Map.Entry<String, UserPrefixEntry> e : userPrefixes.entrySet()) {
            String key = e.getKey();
            if (key.equalsIgnoreCase(clean)) return e.getValue();

            // Normalize repeated characters (e.g. "sanyamalinkaaa" -> "sanyamalinka" matching "sanyamalinkaa")
            String normKey = key.replaceAll("(.)\\1+", "$1");
            String normClean = clean.toLowerCase().replaceAll("(.)\\1+", "$1");
            if (normKey.equalsIgnoreCase(normClean) || key.startsWith(clean.toLowerCase()) || clean.toLowerCase().startsWith(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String getUserPrefixFormatted(String username) {
        UserPrefixEntry entry = getUserPrefix(username);
        if (entry != null) {
            return entry.getFormattedPrefix();
        }
        return "";
    }

    public static void savePrefixTemplateAsync(String id, String name, String color, boolean colorFullName, Consumer<Boolean> callback) {
        String templateId = (id != null && !id.isEmpty()) ? id : "t_" + System.currentTimeMillis();
        // Immediately add/update local in-memory prefix templates
        PrefixTemplateEntry newTemplate = new PrefixTemplateEntry(templateId, name, color, colorFullName, System.currentTimeMillis());
        prefixTemplates.removeIf(t -> t.getName().equalsIgnoreCase(name));
        prefixTemplates.add(0, newTemplate);
        saveLocalOnly(com.skyblockrating.gui.LeaderboardScreen.getAllPlayers());

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                JsonObject body = new JsonObject();
                body.addProperty("id", templateId);
                body.addProperty("name", name);
                body.addProperty("color", color);
                body.addProperty("colorFullName", colorFullName);

                URL url = new URI(WORKER_URL + "/api/admin/prefix-templates").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                applyAdminHeaders(conn, uuid, username);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void savePrefixTemplateAsync(String id, String name, String color, Consumer<Boolean> callback) {
        savePrefixTemplateAsync(id, name, color, false, callback);
    }

    public static void deletePrefixTemplateAsync(String templateId, Consumer<Boolean> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/admin/prefix-templates/" + templateId).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void assignUserPrefixAsync(String targetUser, String prefixId, String prefixName, String prefixColor, String nameColor, boolean colorFullName, Consumer<Boolean> callback) {
        // Immediately update local cache for instant UI feedback
        if (targetUser != null && !targetUser.trim().isEmpty()) {
            UserPrefixEntry newEntry = new UserPrefixEntry(targetUser.trim(), prefixId, prefixName, prefixColor, nameColor, colorFullName, System.currentTimeMillis());
            userPrefixes.put(targetUser.toLowerCase().trim(), newEntry);
            saveLocalOnly(com.skyblockrating.gui.LeaderboardScreen.getAllPlayers());
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                JsonObject body = new JsonObject();
                body.addProperty("username", targetUser);
                body.addProperty("prefixId", prefixId);
                body.addProperty("prefixName", prefixName);
                body.addProperty("prefixColor", prefixColor);
                body.addProperty("nameColor", nameColor);
                body.addProperty("colorFullName", colorFullName);

                URL url = new URI(WORKER_URL + "/api/admin/user-prefix").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                applyAdminHeaders(conn, uuid, username);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void assignUserPrefixAsync(String targetUser, String prefixId, String prefixName, String prefixColor, boolean colorFullName, Consumer<Boolean> callback) {
        assignUserPrefixAsync(targetUser, prefixId, prefixName, prefixColor, colorFullName ? prefixColor : "§f", colorFullName, callback);
    }

    public static void assignUserPrefixAsync(String targetUser, String prefixId, String prefixName, String prefixColor, Consumer<Boolean> callback) {
        assignUserPrefixAsync(targetUser, prefixId, prefixName, prefixColor, false, callback);
    }

    public static void removeUserPrefixAsync(String targetUser, Consumer<Boolean> callback) {
        // Immediately remove from local cache for instant UI feedback
        if (targetUser != null && !targetUser.trim().isEmpty()) {
            userPrefixes.remove(targetUser.toLowerCase().trim());
            saveLocalOnly(com.skyblockrating.gui.LeaderboardScreen.getAllPlayers());
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/admin/user-prefix/" + targetUser).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void recordTeammatesAsync(String author, Collection<String> teammates) {
        recordTeammatesAsync(author, "dungeons", teammates);
    }

    public static void recordTeammatesAsync(String author, String category, Collection<String> teammates) {
        if (author == null || teammates == null || teammates.isEmpty()) return;
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("author", author);
                body.addProperty("category", category != null ? category : "dungeons");
                JsonArray arr = new JsonArray();
                for (String tm : teammates) {
                    arr.add(tm);
                }
                body.add("teammates", arr);

                String res = postJson(WORKER_URL + "/api/record-teammates", body.toString());
                System.out.println("[SkyReview] D1 DB Teammates Record Response (" + category + "): " + res);
            } catch (Throwable t) {
                System.err.println("[SkyReview] Failed to record teammates to D1 DB: " + t.getMessage());
            }
        });
    }

    private static final List<com.skyblockrating.data.BlockedUserEntry> blockedUsers = new ArrayList<>();

    public static List<com.skyblockrating.data.BlockedUserEntry> getBlockedUsers() {
        return blockedUsers;
    }

    public static boolean isPlayerBlocked(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String clean = username.trim().toLowerCase();
        for (com.skyblockrating.data.BlockedUserEntry b : blockedUsers) {
            if (b.getUsername() != null && b.getUsername().equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCurrentClientBlocked() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getUser() != null) {
            return isPlayerBlocked(client.getUser().getName());
        }
        return false;
    }

    public static void blockUserAsync(String targetUser, String reason, Consumer<Boolean> callback) {
        if (targetUser == null || targetUser.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String cleanUser = targetUser.trim();
        blockedUsers.removeIf(b -> b.getUsername().equalsIgnoreCase(cleanUser));
        blockedUsers.add(new com.skyblockrating.data.BlockedUserEntry(cleanUser, reason, System.currentTimeMillis()));

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                JsonObject body = new JsonObject();
                body.addProperty("username", cleanUser);
                body.addProperty("reason", reason != null ? reason : "Moderator block");

                URL url = new URI(WORKER_URL + "/api/admin/block-user").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                applyAdminHeaders(conn, uuid, username);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void unblockUserAsync(String targetUser, Consumer<Boolean> callback) {
        if (targetUser == null || targetUser.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String cleanUser = targetUser.trim();
        blockedUsers.removeIf(b -> b.getUsername().equalsIgnoreCase(cleanUser));

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/admin/block-user/" + cleanUser).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void fetchBlockedUsersAsync(Consumer<List<com.skyblockrating.data.BlockedUserEntry>> callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String username = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/admin/blocked-users").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, username);

                if (conn.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        Type listType = new TypeToken<List<com.skyblockrating.data.BlockedUserEntry>>() {}.getType();
                        List<com.skyblockrating.data.BlockedUserEntry> list = GSON.fromJson(reader, listType);
                        if (list != null) {
                            blockedUsers.clear();
                            blockedUsers.addAll(list);
                        }
                    }
                }
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(new ArrayList<>(blockedUsers)));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(new ArrayList<>(blockedUsers));
            }
        });
    }

    private static final Map<String, com.skyblockrating.data.UserProfileEntry> userProfiles = new ConcurrentHashMap<>();

    public static com.skyblockrating.data.UserProfileEntry getUserProfile(String username) {
        if (username == null || username.trim().isEmpty()) return new com.skyblockrating.data.UserProfileEntry();
        com.skyblockrating.data.UserProfileEntry p = userProfiles.get(username.toLowerCase().trim());
        return (p != null) ? p : new com.skyblockrating.data.UserProfileEntry(username, "", "", 0);
    }

    public static void fetchUserProfileAsync(String username, Consumer<com.skyblockrating.data.UserProfileEntry> callback) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.accept(new com.skyblockrating.data.UserProfileEntry());
            return;
        }
        String cleanUser = username.toLowerCase().trim();
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                URL url = new URI(WORKER_URL + "/api/profile/" + cleanUser).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        com.skyblockrating.data.UserProfileEntry profile = GSON.fromJson(reader, com.skyblockrating.data.UserProfileEntry.class);
                        if (profile != null) {
                            userProfiles.put(cleanUser, profile);
                            Minecraft client = Minecraft.getInstance();
                            if (client != null && callback != null) {
                                client.execute(() -> callback.accept(profile));
                            }
                            conn.disconnect();
                            return;
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            com.skyblockrating.data.UserProfileEntry cached = getUserProfile(cleanUser);
            Minecraft client = Minecraft.getInstance();
            if (client != null && callback != null) {
                client.execute(() -> callback.accept(cached));
            }
        });
    }

    public static void saveUserProfileAsync(String username, String bio, String discordTag, Consumer<Boolean> callback) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String cleanUser = username.toLowerCase().trim();
        com.skyblockrating.data.UserProfileEntry entry = new com.skyblockrating.data.UserProfileEntry(cleanUser, bio, discordTag, System.currentTimeMillis());
        userProfiles.put(cleanUser, entry);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("username", cleanUser);
                body.addProperty("bio", bio != null ? bio : "");
                body.addProperty("discordTag", discordTag != null ? discordTag : "");

                URL url = new URI(WORKER_URL + "/api/profile").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String clientUser = (client != null && client.getUser() != null) ? client.getUser().getName() : "";
                applyAdminHeaders(conn, uuid, clientUser);
                applySessionHeaders(conn);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }

    public static void clearUserProfileAsync(String username, Consumer<Boolean> callback) {
        clearUserBioAsync(username, callback);
    }

    public static void clearUserBioAsync(String username, Consumer<Boolean> callback) {
        clearUserProfileAsync(username, "bio", callback);
    }

    public static void clearUserDiscordAsync(String username, Consumer<Boolean> callback) {
        clearUserProfileAsync(username, "discord", callback);
    }

    public static void clearUserProfileAsync(String username, String field, Consumer<Boolean> callback) {
        if (username == null || username.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String cleanUser = username.toLowerCase().trim();
        com.skyblockrating.data.UserProfileEntry existing = userProfiles.get(cleanUser);
        if (existing != null) {
            if ("discord".equalsIgnoreCase(field)) {
                userProfiles.put(cleanUser, new com.skyblockrating.data.UserProfileEntry(cleanUser, existing.getBio(), "", System.currentTimeMillis()));
            } else if ("all".equalsIgnoreCase(field)) {
                userProfiles.remove(cleanUser);
            } else {
                // "bio"
                userProfiles.put(cleanUser, new com.skyblockrating.data.UserProfileEntry(cleanUser, "", existing.getDiscordTag(), System.currentTimeMillis()));
            }
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Minecraft client = Minecraft.getInstance();
                String uuid = (client != null && client.getUser() != null && client.getUser().getProfileId() != null) ? client.getUser().getProfileId().toString() : "";
                String clientUser = (client != null && client.getUser() != null) ? client.getUser().getName() : "";

                URL url = new URI(WORKER_URL + "/api/admin/profile/" + cleanUser + "?field=" + (field != null ? field : "bio")).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                applyAdminHeaders(conn, uuid, clientUser);

                boolean success = conn.getResponseCode() == 200;
                conn.disconnect();
                if (client != null && callback != null) {
                    client.execute(() -> callback.accept(success));
                }
            } catch (Exception e) {
                if (callback != null) callback.accept(false);
            }
        });
    }
}
