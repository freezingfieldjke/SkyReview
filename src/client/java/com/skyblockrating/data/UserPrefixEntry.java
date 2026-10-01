package com.skyblockrating.data;

import com.google.gson.annotations.SerializedName;

public class UserPrefixEntry {
    @SerializedName(value = "username", alternate = {"user", "targetPlayer"})
    private String username;

    @SerializedName(value = "prefixId", alternate = {"prefix_id"})
    private String prefixId;

    @SerializedName(value = "prefixName", alternate = {"prefix_name", "name"})
    private String prefixName;

    @SerializedName(value = "prefixColor", alternate = {"prefix_color", "color"})
    private String prefixColor;

    @SerializedName(value = "nameColor", alternate = {"name_color", "nickColor", "nick_color"})
    private String nameColor = "§f";

    @SerializedName(value = "colorFullName", alternate = {"color_full_name", "color_name", "colorName"})
    private boolean colorFullName = false;

    @SerializedName(value = "timestamp")
    private long timestamp;

    public UserPrefixEntry(String username, String prefixId, String prefixName, String prefixColor, String nameColor, boolean colorFullName, long timestamp) {
        this.username = username;
        this.prefixId = prefixId;
        this.prefixName = prefixName;
        this.prefixColor = prefixColor;
        this.nameColor = (nameColor != null && !nameColor.isEmpty()) ? nameColor : (colorFullName ? prefixColor : "§f");
        this.colorFullName = colorFullName;
        this.timestamp = timestamp;
    }

    public UserPrefixEntry(String username, String prefixId, String prefixName, String prefixColor, boolean colorFullName, long timestamp) {
        this(username, prefixId, prefixName, prefixColor, colorFullName ? prefixColor : "§f", colorFullName, timestamp);
    }

    public UserPrefixEntry(String username, String prefixId, String prefixName, String prefixColor, long timestamp) {
        this(username, prefixId, prefixName, prefixColor, "§f", false, timestamp);
    }

    public String getUsername() {
        return username;
    }

    public String getPrefixId() {
        return prefixId;
    }

    public String getPrefixName() {
        return prefixName;
    }

    public String getPrefixColor() {
        return prefixColor;
    }

    public String getNameColor() {
        if (nameColor != null && !nameColor.isEmpty()) {
            return nameColor;
        }
        return colorFullName ? (prefixColor != null ? prefixColor : "§f") : "§f";
    }

    public void setNameColor(String nameColor) {
        this.nameColor = nameColor;
    }

    public boolean isColorFullName() {
        return colorFullName;
    }

    public void setColorFullName(boolean colorFullName) {
        this.colorFullName = colorFullName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedPrefix() {
        String c = com.skyblockrating.gui.GuiHelper.normalizeColorCode(prefixColor);
        return c + (prefixName != null ? prefixName : "");
    }
}
