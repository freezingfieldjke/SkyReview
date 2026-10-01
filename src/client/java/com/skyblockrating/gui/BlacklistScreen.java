package com.skyblockrating.gui;

import com.skyblockrating.data.BlacklistEntry;
import com.skyblockrating.storage.RatingStorage;
import com.skyblockrating.util.BlacklistManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BlacklistScreen extends Screen {
    private final Screen parentScreen;
    private List<BlacklistEntry> entries = new ArrayList<>();
    private double scrollOffset = 0;
    private static final int ROW_HEIGHT = 28;

    private EditBox nameBox;
    private EditBox reasonBox;
    private Button btnAdd;
    private String statusMessage = "";

    private boolean showClearConfirm = false;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public BlacklistScreen(Screen parentScreen) {
        super(Component.literal("SkyReview - Blacklist"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.entries = BlacklistManager.getEntries();

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        if (showClearConfirm) {
            int confirmW = 260;
            int confirmH = 110;
            int confirmX = (this.width - confirmW) / 2;
            int confirmY = (this.height - confirmH) / 2;

            this.addRenderableWidget(
                Button.builder(Component.literal("§c✔ Clear All"), button -> {
                    BlacklistManager.clearBlacklist();
                    this.entries = BlacklistManager.getEntries();
                    this.statusMessage = "§cBlacklist cleared!";
                    this.showClearConfirm = false;
                    rebuildWidgets();
                })
                .bounds(confirmX + 16, confirmY + 70, 110, 22)
                .build()
            );

            this.addRenderableWidget(
                Button.builder(Component.literal("§7✕ Cancel"), button -> {
                    this.showClearConfirm = false;
                    rebuildWidgets();
                })
                .bounds(confirmX + 134, confirmY + 70, 110, 22)
                .build()
            );
            return;
        }

        int inputY = modalY + 34;

        // Player Name Input
        this.nameBox = new EditBox(this.font, modalX + 14, inputY, 130, 20, Component.literal("Player IGN"));
        this.nameBox.setMaxLength(16);
        this.nameBox.setHint(Component.literal("Player IGN"));
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        // Reason Input
        this.reasonBox = new EditBox(this.font, modalX + 150, inputY, 230, 20, Component.literal("Reason"));
        this.reasonBox.setMaxLength(100);
        this.reasonBox.setHint(Component.literal("Reason (Optional)"));
        this.addRenderableWidget(this.reasonBox);

        // Add Button
        this.btnAdd = this.addRenderableWidget(
            Button.builder(Component.literal("§a➕ Add Player"), button -> {
                String ign = this.nameBox.getValue().trim();
                String reason = this.reasonBox.getValue().trim();
                if (ign.isEmpty()) {
                    this.statusMessage = "§cPlease enter a player username!";
                    return;
                }
                if (BlacklistManager.addPlayer(ign, reason)) {
                    this.statusMessage = "§aAdded " + ign + " to blacklist!";
                    this.nameBox.setValue("");
                    this.reasonBox.setValue("");
                    this.entries = BlacklistManager.getEntries();
                } else {
                    this.statusMessage = "§cInvalid username!";
                }
            })
            .bounds(modalX + 386, inputY, 120, 20)
            .build()
        );

        // Bottom Controls
        int bottomY = modalY + modalHeight - 26;

        this.addRenderableWidget(
            Button.builder(Component.literal("§c🗑 Clear All"), button -> {
                if (!this.entries.isEmpty()) {
                    this.showClearConfirm = true;
                    rebuildWidgets();
                } else {
                    this.statusMessage = "§7Blacklist is already empty.";
                }
            })
            .bounds(modalX + 14, bottomY, 80, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + modalWidth - 14 - 70, bottomY, 70, 20)
            .build()
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;
        int tableX = modalX + 14;
        int tableY = modalY + 62;
        int tableW = modalWidth - 28;
        int tableH = modalHeight - 94;

        if (showClearConfirm) {
            return true;
        }
        if (mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= tableY && mouseY <= tableY + tableH) {
            int totalHeight = entries.size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, totalHeight - tableH);
            this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (verticalAmount * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Main Panel
        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.COLOR_GOLD);

        // Header Title
        graphics.text(this.font, Component.literal("§c§lSky§e§lReview §8| §fBlacklist"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        // Counter
        String countBadge = "§7Blocked: §e" + entries.size();
        graphics.text(this.font, Component.literal(countBadge), modalX + modalWidth - 14 - this.font.width(countBadge), modalY + 11, GuiHelper.TEXT_MUTED, false);

        int tableX = modalX + 14;
        int tableY = modalY + 62;
        int tableW = modalWidth - 28;
        int tableH = modalHeight - 94;

        // Table Background
        graphics.fill(tableX - 1, tableY - 1, tableX + tableW + 1, tableY + tableH + 1, GuiHelper.PANEL_BORDER);
        graphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xFF0B101B);

        if (entries.isEmpty()) {
            Component empty1 = Component.literal("§7No blacklisted players yet.");
            Component empty2 = Component.literal("§8Add unwanted teammates above or use §e/sr blacklist add <player>");
            graphics.text(this.font, empty1, tableX + (tableW - this.font.width(empty1)) / 2, tableY + 50, GuiHelper.TEXT_MUTED, false);
            graphics.text(this.font, empty2, tableX + (tableW - this.font.width(empty2)) / 2, tableY + 66, GuiHelper.TEXT_MUTED, false);
        } else {
            graphics.enableScissor(tableX, tableY, tableX + tableW, tableY + tableH);

            int maxVisibleRows = tableH / ROW_HEIGHT;
            int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
            int endIndex = Math.min(entries.size(), startIndex + maxVisibleRows + 2);

            for (int i = startIndex; i < endIndex; i++) {
                BlacklistEntry entry = entries.get(i);
                int rowY = tableY + (i * ROW_HEIGHT) - (int) scrollOffset;

                if (rowY + ROW_HEIGHT <= tableY || rowY >= tableY + tableH) {
                    continue;
                }

                boolean hovered = mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                int rowBg = hovered ? GuiHelper.CARD_HOVER : ((i % 2 == 0) ? GuiHelper.CARD_BG : GuiHelper.CARD_BG_ALT);

                graphics.fill(tableX, rowY, tableX + tableW, rowY + ROW_HEIGHT - 2, rowBg);

                // Icon + IGN
                String ignText = "§c⛔ §f" + entry.getUsername();
                graphics.text(this.font, Component.literal(ignText), tableX + 8, rowY + 7, 0xFFFFFFFF, false);

                // Reason (truncated if too long)
                String reasonStr = "§7Reason: §f" + entry.getReason();
                if (this.font.width(reasonStr) > 210) {
                    reasonStr = this.font.plainSubstrByWidth(reasonStr, 200) + "...";
                }
                graphics.text(this.font, Component.literal(reasonStr), tableX + 150, rowY + 7, GuiHelper.TEXT_MUTED, false);

                // Date
                String dateStr = "§8" + DATE_FORMAT.format(new Date(entry.getTimestamp()));
                graphics.text(this.font, Component.literal(dateStr), tableX + 370, rowY + 7, GuiHelper.TEXT_MUTED, false);

                // Action: Remove Button Pill
                int remW = 54;
                int remH = 16;
                int remX = tableX + tableW - 60;
                int remY = rowY + (ROW_HEIGHT - 2 - remH) / 2;
                boolean hRem = mouseX >= remX && mouseX <= remX + remW && mouseY >= remY && mouseY <= remY + remH;
                int remBg = hRem ? 0xFFDC2626 : 0xFF1E293B;
                graphics.fill(remX - 1, remY - 1, remX + remW + 1, remY + remH + 1, hRem ? 0xFFEF4444 : GuiHelper.PANEL_BORDER);
                graphics.fill(remX, remY, remX + remW, remY + remH, remBg);
                graphics.text(this.font, Component.literal("✕ Remove"), remX + 5, remY + 4, 0xFFFFFFFF, false);
            }

            graphics.disableScissor();
        }

        // Status Message at bottom center
        if (this.statusMessage != null && !this.statusMessage.isEmpty()) {
            Component statusComp = Component.literal(this.statusMessage);
            graphics.text(this.font, statusComp, modalX + (modalWidth - this.font.width(statusComp)) / 2, modalY + modalHeight - 20, GuiHelper.COLOR_GREEN, false);
        }

        // Confirmation Modal Overlay
        if (showClearConfirm) {
            graphics.fill(0, 0, this.width, this.height, 0x99000000);

            int confirmW = 260;
            int confirmH = 110;
            int confirmX = (this.width - confirmW) / 2;
            int confirmY = (this.height - confirmH) / 2;

            GuiHelper.renderPanel(graphics, confirmX, confirmY, confirmW, confirmH, 20, GuiHelper.COLOR_RED);

            Component cTitle = Component.literal("§c§lClear Blacklist?");
            graphics.text(this.font, cTitle, confirmX + (confirmW - this.font.width(cTitle)) / 2, confirmY + 16, GuiHelper.COLOR_RED, true);

            Component cMsg = Component.literal("§7Remove all §e" + entries.size() + " §7players from blacklist?");
            graphics.text(this.font, cMsg, confirmX + (confirmW - this.font.width(cMsg)) / 2, confirmY + 34, GuiHelper.TEXT_SECONDARY, false);

            Component cWarn = Component.literal("§8This action cannot be undone.");
            graphics.text(this.font, cWarn, confirmX + (confirmW - this.font.width(cWarn)) / 2, confirmY + 48, GuiHelper.TEXT_MUTED, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (showClearConfirm) {
            return super.mouseClicked(event, doubleClick);
        }

        double mouseX = event.x();
        double mouseY = event.y();

        boolean inName = (this.nameBox != null && mouseX >= this.nameBox.getX() && mouseX <= this.nameBox.getX() + this.nameBox.getWidth()
                && mouseY >= this.nameBox.getY() && mouseY <= this.nameBox.getY() + this.nameBox.getHeight());
        boolean inReason = (this.reasonBox != null && mouseX >= this.reasonBox.getX() && mouseX <= this.reasonBox.getX() + this.reasonBox.getWidth()
                && mouseY >= this.reasonBox.getY() && mouseY <= this.reasonBox.getY() + this.reasonBox.getHeight());

        if (inName && this.nameBox.isFocused()) {
            this.nameBox.setFocused(false);
            this.setFocused(null);
            return true;
        }
        if (inReason && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        if (!inName && this.nameBox != null && this.nameBox.isFocused()) {
            this.nameBox.setFocused(false);
            if (this.getFocused() == this.nameBox) {
                this.setFocused(null);
            }
        }
        if (!inReason && this.reasonBox != null && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            if (this.getFocused() == this.reasonBox) {
                this.setFocused(null);
            }
        }

        int modalWidth = 520;
        int modalHeight = 320;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;
        int tableX = modalX + 14;
        int tableY = modalY + 62;
        int tableW = modalWidth - 28;
        int tableH = modalHeight - 94;

        if (mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= tableY && mouseY <= tableY + tableH) {
            int maxVisibleRows = tableH / ROW_HEIGHT;
            int startIndex = Math.max(0, (int) (scrollOffset / ROW_HEIGHT));
            int endIndex = Math.min(entries.size(), startIndex + maxVisibleRows + 2);

            for (int i = startIndex; i < endIndex; i++) {
                BlacklistEntry entry = entries.get(i);
                int rowY = tableY + (i * ROW_HEIGHT) - (int) scrollOffset;

                if (rowY + ROW_HEIGHT <= tableY || rowY >= tableY + tableH) {
                    continue;
                }

                int remW = 54;
                int remH = 16;
                int remX = tableX + tableW - 60;
                int remY = rowY + (ROW_HEIGHT - 2 - remH) / 2;

                if (mouseX >= remX && mouseX <= remX + remW && mouseY >= remY && mouseY <= remY + remH) {
                    BlacklistManager.removePlayer(entry.getUsername());
                    this.statusMessage = "§cRemoved " + entry.getUsername() + " from blacklist!";
                    this.entries = BlacklistManager.getEntries();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }
}
