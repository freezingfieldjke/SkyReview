package com.skyblockrating.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class AdminAuthManager {
    public static final String DEFAULT_MASTER_KEY = "";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String adminKey = DEFAULT_MASTER_KEY;
    private static boolean loaded = false;

    private static File getConfigFile() {
        try {
            Minecraft client = Minecraft.getInstance();
            File gameDir = (client != null && client.gameDirectory != null) ? client.gameDirectory : new File(".");
            return new File(gameDir, "config/skyblockrating/admin_key.json");
        } catch (Exception e) {
            return new File("config/skyblockrating/admin_key.json");
        }
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            File file = getConfigFile();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                    if (obj != null && obj.has("adminKey")) {
                        adminKey = obj.get("adminKey").getAsString();
                    }
                }
            }
            if (adminKey == null || adminKey.trim().isEmpty()) {
                // Fallback to standard %APPDATA%/.minecraft directory if using a separate launcher instance
                try {
                    String appdata = System.getenv("APPDATA");
                    if (appdata != null) {
                        File fallback = new File(appdata, ".minecraft/config/skyblockrating/admin_key.json");
                        if (fallback.exists()) {
                            try (FileReader reader = new FileReader(fallback)) {
                                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                                if (obj != null && obj.has("adminKey") && !obj.get("adminKey").getAsString().trim().isEmpty()) {
                                    adminKey = obj.get("adminKey").getAsString().trim();
                                    save(); // copy to local instance
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (!file.exists()) {
                save();
            }
        } catch (Exception e) {
            adminKey = DEFAULT_MASTER_KEY;
        }
    }

    public static synchronized void save() {
        try {
            File file = getConfigFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("adminKey", adminKey != null ? adminKey : "");
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized String getAdminKey() {
        if (!loaded) load();
        return adminKey != null ? adminKey.trim() : "";
    }

    public static synchronized void setAdminKey(String key) {
        adminKey = key != null ? key.trim() : "";
        save();
        OwnerLock.invalidateCache();
    }

    public static synchronized boolean hasAdminKey() {
        String key = getAdminKey();
        return !key.isEmpty();
    }

    public static synchronized void clearAdminKey() {
        setAdminKey("");
    }
}
