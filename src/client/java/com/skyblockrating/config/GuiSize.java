package com.skyblockrating.config;

public enum GuiSize {
    NORMAL("Normal"),
    LARGE("Large"),
    FULLSCREEN("Fullscreen");

    private final String displayName;

    GuiSize(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GuiSize next() {
        GuiSize[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
