package com.skyblockrating.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class SessionAuthManager {
    public static final String DISCORD_URL = "https://discord.gg/KpVndhhNDr";
    public static final String AUTH_REQUIRED_MSG = "§c[SkyReview] A licensed Minecraft account is required! If you own a license, run §e/sr reload§c. If this is an error, please write to us on Discord: §b" + DISCORD_URL;
    public static final String AUTH_REQUIRED_MODAL = "§cLicense unverified! Run /sr reload. If this is an error, write to us on Discord: " + DISCORD_URL;

    private static volatile String cachedToken = null;
    private static volatile long tokenExpiry = 0L;
    private static final String HANDSHAKE_SALT = "SkyReviewMod_Auth_v1_998472bf301045d9";

    public static boolean isLicensedUser() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) return false;
        String accessToken = mc.getUser().getAccessToken();
        return accessToken != null && !accessToken.trim().isEmpty() && !"0".equals(accessToken.trim());
    }

    public static synchronized void invalidateCache() {
        cachedToken = null;
        tokenExpiry = 0L;
    }

    /**
     * Gets the current session token or fetches a fresh one from the Cloudflare Worker.
     * Thread-safe. Designed to be called from worker/async threads.
     */
    public static synchronized String getOrFetchSessionToken() {
        long now = System.currentTimeMillis();
        // Buffer of 5 minutes before actual expiry
        if (cachedToken != null && now < (tokenExpiry - 300_000L)) {
            return cachedToken;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            User user = mc.getUser();
            if (user == null) return null;

            String username = user.getName();
            UUID uuid = user.getProfileId();
            String accessToken = user.getAccessToken();

            if (username == null || username.trim().isEmpty()) {
                return null;
            }

            String cleanUser = username.trim();
            String cleanUuid = uuid != null ? uuid.toString().replace("-", "").toLowerCase() : "";

            // Check if player has a genuine Minecraft session access token
            if (accessToken == null || accessToken.trim().isEmpty() || "0".equals(accessToken.trim())) {
                System.err.println("[SkyReview] No valid Minecraft license session accessToken found. Cracked accounts cannot authenticate.");
                return null;
            }

            // Send genuine Mojang accessToken to Cloudflare Worker for cryptographic RS256 verification
            String workerUrl = RatingStorage.getWorkerUrl();
            JsonObject authReq = new JsonObject();
            authReq.addProperty("username", cleanUser);
            authReq.addProperty("uuid", cleanUuid);
            authReq.addProperty("accessToken", accessToken);

            JsonObject verifyResp = postJsonDirect(workerUrl + "/api/auth/session", authReq.toString());
            if (verifyResp != null && verifyResp.has("success") && verifyResp.get("success").getAsBoolean()) {
                cachedToken = verifyResp.get("token").getAsString();
                tokenExpiry = verifyResp.has("expiresAt") ? verifyResp.get("expiresAt").getAsLong() : (now + 24 * 3600 * 1000L);
                System.out.println("[SkyReview] Licensed Minecraft session verified via Mojang cryptographic signature! Token cached for @" + cleanUser);
                return cachedToken;
            } else {
                String err = (verifyResp != null && verifyResp.has("error")) ? verifyResp.get("error").getAsString() : "Verification failed";
                System.err.println("[SkyReview] Session token issue failed: " + err);
            }
        } catch (Exception e) {
            System.err.println("[SkyReview] Session authentication exception: " + e.getMessage());
        }

        return cachedToken;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonObject postJsonDirect(String urlStr, String json) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    return JsonParser.parseString(sb.toString()).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            System.err.println("[SkyReview] Direct POST failed: " + e.getMessage());
        }
        return null;
    }


    public static String getCachedToken() {
        return cachedToken;
    }

    public static void warmUpAsync() {
        new Thread(() -> {
            getOrFetchSessionToken();
        }, "SkyReview-AuthWarmup").start();
    }
}
