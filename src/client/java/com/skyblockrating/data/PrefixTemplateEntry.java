package com.skyblockrating.data;

import com.google.gson.annotations.SerializedName;

public class PrefixTemplateEntry {
    @SerializedName(value = "id")
    private String id;

    @SerializedName(value = "name", alternate = {"prefix_name"})
    private String name;

    @SerializedName(value = "color", alternate = {"prefix_color"})
    private String color;

    @SerializedName(value = "colorFullName", alternate = {"color_full_name", "color_name", "colorName"})
    private boolean colorFullName = false;

    @SerializedName(value = "timestamp")
    private long timestamp;

    public PrefixTemplateEntry(String id, String name, String color, boolean colorFullName, long timestamp) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.colorFullName = colorFullName;
        this.timestamp = timestamp;
    }

    public PrefixTemplateEntry(String id, String name, String color, long timestamp) {
        this(id, name, color, false, timestamp);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
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
        String c = com.skyblockrating.gui.GuiHelper.normalizeColorCode(color);
        return c + (name != null ? name : "");
    }
}
