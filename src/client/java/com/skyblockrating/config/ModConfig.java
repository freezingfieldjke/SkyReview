package com.skyblockrating.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    public static final String DEFAULT_API_KEY = "";
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/skyblockrating/config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static GuiSize guiSize = GuiSize.LARGE;
    private static String hypixelApiKey = DEFAULT_API_KEY;
    private static int menuKey = GLFW.GLFW_KEY_O;
    private static boolean showPrefixes = true;
    private static boolean dungeonEndReminder = true;
    private static boolean autoKickBlacklist = true;
    private static boolean partyFinderJoinRating = true;

    static {
        loadConfig();
    }

    public static class ConfigData {
        public String guiSize = GuiSize.LARGE.name();
        public String hypixelApiKey = DEFAULT_API_KEY;
        public int menuKey = GLFW.GLFW_KEY_O;
        public Boolean showPrefixes = true;
        public Boolean dungeonEndReminder = true;
        public Boolean autoKickBlacklist = true;
        public Boolean partyFinderJoinRating = true;
    }

    public static void loadConfig() {
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    ConfigData data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null) {
                        if (data.guiSize != null) {
                            try { guiSize = GuiSize.valueOf(data.guiSize); } catch (Exception ignored) {}
                        }
                        if (data.hypixelApiKey != null && !data.hypixelApiKey.trim().isEmpty()
                                && !data.hypixelApiKey.equalsIgnoreCase("9d872686-25e9-4a3e-8291-b92f849957e7")
                                && !data.hypixelApiKey.equalsIgnoreCase("f7eaeabc-5412-45ad-b116-5ae270cae9e8")
                                && !data.hypixelApiKey.equalsIgnoreCase("a826b79f-683f-4d1f-9f2d-264666d3ed61")) {
                            hypixelApiKey = data.hypixelApiKey.trim();
                        } else {
                            hypixelApiKey = DEFAULT_API_KEY;
                        }
                        menuKey = data.menuKey;
                        if (data.showPrefixes != null) {
                            showPrefixes = data.showPrefixes;
                        } else {
                            showPrefixes = true;
                        }
                        if (data.dungeonEndReminder != null) {
                            dungeonEndReminder = data.dungeonEndReminder;
                        } else {
                            dungeonEndReminder = true;
                        }
                        if (data.autoKickBlacklist != null) {
                            autoKickBlacklist = data.autoKickBlacklist;
                        } else {
                            autoKickBlacklist = true;
                        }
                        if (data.partyFinderJoinRating != null) {
                            partyFinderJoinRating = data.partyFinderJoinRating;
                        } else {
                            partyFinderJoinRating = true;
                        }
                    }
                }
            } else {
                saveConfig();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            ConfigData data = new ConfigData();
            data.guiSize = guiSize.name();
            data.hypixelApiKey = hypixelApiKey;
            data.menuKey = menuKey;
            data.showPrefixes = showPrefixes;
            data.dungeonEndReminder = dungeonEndReminder;
            data.autoKickBlacklist = autoKickBlacklist;
            data.partyFinderJoinRating = partyFinderJoinRating;

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GuiSize getGuiSize() {
        return guiSize;
    }

    public static void setGuiSize(GuiSize size) {
        if (size != null) {
            guiSize = size;
            saveConfig();
        }
    }

    public static String getHypixelApiKey() {
        return hypixelApiKey;
    }

    public static boolean isUsingDefaultApiKey() {
        return hypixelApiKey == null || hypixelApiKey.trim().isEmpty() || DEFAULT_API_KEY.equals(hypixelApiKey);
    }

    public static String getCustomHypixelApiKey() {
        return isUsingDefaultApiKey() ? null : hypixelApiKey;
    }

    public static void setHypixelApiKey(String key) {
        if (key != null) {
            hypixelApiKey = key.trim();
            saveConfig();
        }
    }

    public static void resetToDefaultApiKey() {
        hypixelApiKey = DEFAULT_API_KEY;
        saveConfig();
    }

    public static int getMenuKey() {
        return menuKey;
    }

    public static void setMenuKey(int key) {
        menuKey = key;
        saveConfig();
    }

    public static boolean isShowPrefixes() {
        return showPrefixes;
    }

    public static void setShowPrefixes(boolean show) {
        showPrefixes = show;
        saveConfig();
    }

    public static boolean isDungeonEndReminder() {
        return dungeonEndReminder;
    }

    public static void setDungeonEndReminder(boolean enabled) {
        dungeonEndReminder = enabled;
        saveConfig();
    }

    public static boolean isAutoKickBlacklist() {
        return autoKickBlacklist;
    }

    public static void setAutoKickBlacklist(boolean enabled) {
        autoKickBlacklist = enabled;
        saveConfig();
    }

    public static boolean isPartyFinderJoinRating() {
        return partyFinderJoinRating;
    }

    public static void setPartyFinderJoinRating(boolean enabled) {
        partyFinderJoinRating = enabled;
        saveConfig();
    }

    public static String getMenuKeyName() {
        String name = GLFW.glfwGetKeyName(menuKey, 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase();
        }
        if (menuKey == GLFW.GLFW_KEY_O) return "O";
        if (menuKey == GLFW.GLFW_KEY_K) return "K";
        if (menuKey == GLFW.GLFW_KEY_L) return "L";
        if (menuKey == GLFW.GLFW_KEY_P) return "P";
        if (menuKey == GLFW.GLFW_KEY_U) return "U";
        if (menuKey == GLFW.GLFW_KEY_I) return "I";
        return "KEY " + menuKey;
    }
}
