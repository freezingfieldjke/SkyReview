package com.skyblockrating.config;

import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.Minecraft;

public class OwnerLock {
    private static Boolean cachedAdminState = null;
    private static long lastCheckTimestamp = 0;

    public static void invalidateCache() {
        cachedAdminState = null;
        lastCheckTimestamp = 0;
    }

    public static boolean isOwner() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) {
            return false;
        }

        // Without a valid configured master key, admin privileges are strictly denied
        if (!AdminAuthManager.hasAdminKey()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (cachedAdminState != null && (now - lastCheckTimestamp < 60000)) {
            return cachedAdminState;
        }

        try {
            String username = client.getUser().getName();
            String uuid = client.getUser().getProfileId() != null ? client.getUser().getProfileId().toString() : "";

            // Fast-path: only if master key is present AND username is in the known list
            if (username != null && (username.equalsIgnoreCase("freezingfield") || username.equalsIgnoreCase("golf67"))) {
                cachedAdminState = true;
                lastCheckTimestamp = now;

                // Asynchronously confirm with server using master key
                RatingStorage.verifyAdminAsync(uuid, username, isAdmin -> {
                    cachedAdminState = isAdmin;
                    lastCheckTimestamp = System.currentTimeMillis();
                });
                return true;
            }

            // Strictly check admin privileges via Cloudflare Worker server API using master key
            RatingStorage.verifyAdminAsync(uuid, username, isAdmin -> {
                cachedAdminState = isAdmin;
                lastCheckTimestamp = System.currentTimeMillis();
            });

            if (cachedAdminState != null) {
                return cachedAdminState;
            }
        } catch (Exception ignored) {}

        return false;
    }
}
