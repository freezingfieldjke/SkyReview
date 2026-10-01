package com.skyblockrating.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class GuiHelper {
    // Colors - Sleek Obsidian & Slate theme with SkyBlock Gold / Cyan accents
    public static final int BG_OVERLAY = 0x90000000;
    public static final int PANEL_BG = 0xF212141A;
    public static final int PANEL_BORDER = 0xFF2A2D3A;
    public static final int PANEL_BORDER_LIGHT = 0xFF3D4255;
    
    public static final int HEADER_BG = 0xFF191B24;
    public static final int HEADER_ACCENT = 0xFFFFAA00; // Gold
    public static final int HEADER_ACCENT_ADMIN = 0xFFEF4444; // Red
    public static final int HEADER_ACCENT_KUUDRA = 0xFFF97316; // Orange
    
    public static final int CARD_BG = 0xFF191B24;
    public static final int CARD_BG_ALT = 0xFF14151D;
    public static final int CARD_HOVER = 0xFF232736;
    public static final int CARD_BORDER = 0xFF282B38;
    
    public static final int TEXT_TITLE = 0xFFFFFFFF;
    public static final int TEXT_PRIMARY = 0xFFF1F5F9;
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    public static final int TEXT_MUTED = 0xFF64748B;
    
    public static final int COLOR_GOLD = 0xFFFFAA00;
    public static final int COLOR_YELLOW = 0xFFFFD700;
    public static final int COLOR_AQUA = 0xFF55FFFF;
    public static final int COLOR_GREEN = 0xFF55FF55;
    public static final int COLOR_RED = 0xFFFF5555;
    public static final int COLOR_ORANGE = 0xFFF97316;
    public static final int COLOR_BLUE = 0xFF38BDF8;

    /**
     * Draws a sleek modern framed panel with subtle 1px border.
     */
    public static void renderPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int headerHeight, int accentColor) {
        // Drop shadow / border
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_BORDER);
        // Main panel body
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        
        if (headerHeight > 0) {
            // Header background
            graphics.fill(x, y, x + width, y + headerHeight, HEADER_BG);
            // Header bottom separator
            graphics.fill(x, y + headerHeight - 1, x + width, y + headerHeight, PANEL_BORDER);
            // Accent top line
            if (accentColor != 0) {
                graphics.fill(x, y, x + width, y + 2, accentColor);
            }
        }
    }

    /**
     * Draws a card container inside a screen.
     */
    public static void renderCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean isHovered) {
        int border = isHovered ? PANEL_BORDER_LIGHT : CARD_BORDER;
        int bg = isHovered ? CARD_HOVER : CARD_BG;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, border);
        graphics.fill(x, y, x + width, y + height, bg);
    }

    /**
     * Draws a badge / chip pill with colored text and background.
     */
    public static void renderBadge(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int textColor, int bgColor, int borderColor) {
        int textWidth = font.width(text);
        int badgeWidth = textWidth + 8;
        int badgeHeight = 12;
        
        graphics.fill(x - 1, y - 1, x + badgeWidth + 1, y + badgeHeight + 1, borderColor);
        graphics.fill(x, y, x + badgeWidth, y + badgeHeight, bgColor);
        graphics.text(font, Component.literal(text), x + 4, y + 2, textColor, false);
    }

    /**
     * Formats star rating with active gold and inactive dark gray stars.
     */
    public static String getFormattedStars(double rating) {
        if (rating <= 0.0) {
            return "§8☆☆☆☆☆ §7(0.0)";
        }
        int full = (int) Math.floor(rating);
        boolean half = (rating - full) >= 0.5;
        int empty = 5 - full - (half ? 1 : 0);

        StringBuilder sb = new StringBuilder("§6");
        for (int i = 0; i < full; i++) sb.append("★");
        if (half) sb.append("½");
        sb.append("§8");
        for (int i = 0; i < empty; i++) sb.append("☆");
        sb.append(String.format(" §e%.1f", rating));
        return sb.toString();
    }

    /**
     * Renders a player name with their custom colored prefix.
     * Supports:
     * - RGB Chroma Wave (§z)
     * - HEX Color codes (#RRGGBB)
     * - Standard Minecraft formatting (§c, §6, §e, §l, etc.)
     * - Configurable color scope: Prefix Only (white nickname) vs Full Name (both colored)
     */
    public static void renderPlayerWithPrefix(GuiGraphicsExtractor graphics, Font font, String playerName, com.skyblockrating.data.UserPrefixEntry prefixEntry, int x, int y, boolean isBoldName) {
        if (playerName == null) playerName = "Player";
        if (prefixEntry == null || prefixEntry.getPrefixName() == null || prefixEntry.getPrefixName().trim().isEmpty()) {
            graphics.text(font, Component.literal((isBoldName ? "§6§l" : "§f") + playerName), x, y, TEXT_PRIMARY, isBoldName);
            return;
        }

        renderCustomPrefixAndPlayer(graphics, font, prefixEntry.getPrefixName(), prefixEntry.getPrefixColor(), prefixEntry.getNameColor(), playerName, x, y, isBoldName);
    }

    public static int getPlayerNameWidth(Font font, String playerName, com.skyblockrating.data.UserPrefixEntry prefixEntry, boolean isBoldName) {
        if (playerName == null) playerName = "Player";
        int w = 0;
        if (prefixEntry != null && prefixEntry.getPrefixName() != null && !prefixEntry.getPrefixName().trim().isEmpty()) {
            String cleanPrefix = prefixEntry.getPrefixName().trim().replaceAll("[§&][0-9a-fk-orzA-FK-ORZ]", "");
            w += font.width(cleanPrefix) + font.width(" ");
        }
        w += font.width(playerName) + (isBoldName ? playerName.length() : 0);
        return w;
    }

    /**
     * Normalizes and cleans up any corrupted or variant color codes (e.g., "A z", "Â§z", "&z", "rgb", "#HEX").
     */
    public static String normalizeColorCode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "§f";
        String s = raw.trim();
        // RGB Chroma Wave
        if (s.toLowerCase().contains("rgb") || s.toLowerCase().contains("rainbow") || s.toLowerCase().contains("chroma")
                || s.contains("§z") || s.contains("&z") || s.contains("Â§z") || s.contains("A z") || s.endsWith("z") || s.endsWith("Z")) {
            boolean isBold = s.contains("l") || s.contains("L");
            return "§z" + (isBold ? "§l" : "");
        }
        // Hex
        if (s.startsWith("#")) {
            return s;
        }
        // Section sign corruption cleanup
        s = s.replace("Â§", "§").replace("Â", "").replace("A ", "§").replace("&", "§");
        if (!s.contains("§")) {
            s = "§" + s;
        }
        return s;
    }

    /**
     * Converts a Minecraft color code or hex string into a 32-bit ARGB color integer.
     */
    public static int getMinecraftColorInt(String code) {
        if (code == null || code.isEmpty()) return TEXT_PRIMARY;
        if (code.startsWith("#")) {
            try {
                return 0xFF000000 | Integer.parseInt(code.substring(1).trim(), 16);
            } catch (Exception e) {
                return TEXT_PRIMARY;
            }
        }
        if (code.contains("§0")) return 0xFF000000;
        if (code.contains("§1")) return 0xFF0000AA;
        if (code.contains("§2")) return 0xFF00AA00;
        if (code.contains("§3")) return 0xFF00AAAA;
        if (code.contains("§4")) return 0xFFAA0000;
        if (code.contains("§5")) return 0xFFAA00AA;
        if (code.contains("§6")) return 0xFFFFAA00;
        if (code.contains("§7")) return 0xFFAAAAAA;
        if (code.contains("§8")) return 0xFF555555;
        if (code.contains("§9")) return 0xFF5555FF;
        if (code.contains("§a")) return 0xFF55FF55;
        if (code.contains("§b")) return 0xFF55FFFF;
        if (code.contains("§c")) return 0xFFFF5555;
        if (code.contains("§d")) return 0xFFFF55FF;
        if (code.contains("§e")) return 0xFFFFFF55;
        if (code.contains("§f")) return 0xFFFFFFFF;
        return TEXT_PRIMARY;
    }

    /**
     * Renders a prefix with prefixColor and a player nickname with nameColor independently.
     */
    public static void renderCustomPrefixAndPlayer(GuiGraphicsExtractor graphics, Font font, String prefixName, String prefixColor, String nameColor, String playerName, int x, int y, boolean isBoldName) {
        if (prefixName == null) prefixName = "";
        if (playerName == null) playerName = "";

        int curX = x;
        if (!prefixName.trim().isEmpty()) {
            curX = renderColoredString(graphics, font, prefixName, prefixColor, curX, y, false);
            curX += font.width(" ");
        }

        if (!playerName.isEmpty()) {
            renderColoredString(graphics, font, playerName, nameColor, curX, y, isBoldName);
        }
    }

    /**
     * Renders a string with specific color (RGB Wave, HEX, or Minecraft Section § color).
     * Returns the ending X coordinate.
     */
    public static int renderColoredString(GuiGraphicsExtractor graphics, Font font, String text, String colorCode, int x, int y, boolean defaultBold) {
        if (text == null || text.isEmpty()) return x;
        colorCode = normalizeColorCode(colorCode);

        boolean isBold = colorCode.contains("§l") || defaultBold;
        boolean isRgb = colorCode.contains("§z");

        if (isRgb) {
            String cleanText = text.replace("§z", "").replace("§l", "").replace("&z", "").replace("&l", "");
            int curX = x;
            for (int i = 0; i < cleanText.length(); i++) {
                char ch = cleanText.charAt(i);
                float hue = (float) ((System.currentTimeMillis() + (x + i * 14) * 5L) % 2500L) / 2500.0f;
                int chroma = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) | 0xFF000000;
                String chStr = String.valueOf(ch);
                graphics.text(font, Component.literal(chStr), curX, y, chroma, isBold);
                curX += font.width(chStr) + (isBold ? 1 : 0);
            }
            return curX;
        } else {
            int colorInt = getMinecraftColorInt(colorCode);
            graphics.text(font, Component.literal((isBold ? "§l" : "") + text), x, y, colorInt, isBold);
            return x + font.width(text) + (isBold ? text.length() : 0);
        }
    }

    /**
     * Renders a prefix and sample/player name according to color code and colorFullName scope (backwards compatible).
     */
    public static void renderPrefixAndName(GuiGraphicsExtractor graphics, Font font, String prefixName, String colorCode, boolean colorFullName, String playerName, int x, int y, boolean isBoldName) {
        renderCustomPrefixAndPlayer(graphics, font, prefixName, colorCode, colorFullName ? colorCode : "§f", playerName, x, y, isBoldName);
    }
}
