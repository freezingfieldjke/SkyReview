package com.skyblockrating.gui;

import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.BlockedUserEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AdminBlockedUsersScreen extends Screen {
    private final Screen parentScreen;
    private EditBox usernameBox;
    private EditBox reasonBox;
    private String statusMessage = "";
    private double scrollOffset = 0;
    private static final int ITEM_HEIGHT = 32;

    private boolean initialFetchDone = false;

    public AdminBlockedUsersScreen(Screen parentScreen) {
        super(Component.literal("Blocked Users - Admin"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        String prevUser = (this.usernameBox != null) ? this.usernameBox.getValue() : "";
        String prevReason = (this.reasonBox != null) ? this.reasonBox.getValue() : "";

        // Fetch latest blocked users from Cloudflare D1 only once upon opening screen
        if (!initialFetchDone) {
            initialFetchDone = true;
            RatingStorage.fetchBlockedUsersAsync(list -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(this::rebuildWidgets);
                }
            });
        }

        int modalWidth = 480;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Top Form: Block a new user
        int formY = modalY + 36;
        this.usernameBox = new EditBox(this.font, modalX + 16, formY, 130, 18, Component.literal("IGN"));
        this.usernameBox.setMaxLength(16);
        this.usernameBox.setHint(Component.literal("Player IGN..."));
        if (!prevUser.isEmpty()) this.usernameBox.setValue(prevUser);
        this.addRenderableWidget(this.usernameBox);
        this.setInitialFocus(this.usernameBox);

        this.reasonBox = new EditBox(this.font, modalX + 152, formY, 180, 18, Component.literal("Reason"));
        this.reasonBox.setMaxLength(150);
        this.reasonBox.setHint(Component.literal("Reason (e.g. Spam / Fake)..."));
        if (!prevReason.isEmpty()) this.reasonBox.setValue(prevReason);
        this.addRenderableWidget(this.reasonBox);

        // Block Button
        this.addRenderableWidget(
            Button.builder(Component.literal("🚫 Block"), button -> {
                String uName = usernameBox.getValue().trim();
                String reason = reasonBox.getValue().trim();
                if (uName.isEmpty()) {
                    this.statusMessage = "§cEnter player IGN!";
                    return;
                }
                this.statusMessage = "§bBlocking " + uName + "...";
                RatingStorage.blockUserAsync(uName, reason.isEmpty() ? "Moderator restriction" : reason, success -> {
                    if (success) {
                        this.statusMessage = "§aRestricted @" + uName + " from posting reviews!";
                        this.usernameBox.setValue("");
                        this.reasonBox.setValue("");
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to block user.";
                    }
                });
            })
            .bounds(modalX + 338, formY, 126, 18)
            .build()
        );

        // Bottom Action Bar Buttons
        int buttonY = modalY + modalHeight - 26;

        // Back button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 14, buttonY, 80, 20)
            .build()
        );

        // Close button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Close"), button -> this.onClose())
            .bounds(modalX + modalWidth - 84, buttonY, 70, 20)
            .build()
        );
    }

    private void clampScroll() {
        List<BlockedUserEntry> list = RatingStorage.getBlockedUsers();
        int visibleH = 310 - 100;
        int maxScroll = Math.max(0, list.size() * ITEM_HEIGHT - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 480;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int cardX = modalX + 16;
        int cardY = modalY + 68;
        int cardW = modalWidth - 32;
        int visibleH = modalHeight - 100;

        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + visibleH) {
            scrollOffset -= verticalAmount * 20;
            clampScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 480;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fReview Blacklist (Blocked Authors)"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        if (!OwnerLock.isOwner()) {
            graphics.text(this.font, Component.literal("§cACCESS DENIED: Owner Privileges Required"), modalX + 120, modalY + 140, GuiHelper.COLOR_RED, true);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        int listX = modalX + 16;
        int listY = modalY + 68;
        int listW = modalWidth - 32;
        int visibleH = modalHeight - 100;

        GuiHelper.renderCard(graphics, listX, listY, listW, visibleH, false);

        List<BlockedUserEntry> blocked = new ArrayList<>(RatingStorage.getBlockedUsers());

        if (blocked.isEmpty()) {
            graphics.text(this.font, Component.literal("§8No authors currently restricted from reviewing."), listX + 16, listY + 30, GuiHelper.TEXT_MUTED, false);
        } else {
            int startIndex = Math.max(0, (int) (scrollOffset / ITEM_HEIGHT));
            int endIndex = Math.min(blocked.size(), startIndex + (visibleH / ITEM_HEIGHT) + 2);

            for (int i = startIndex; i < endIndex; i++) {
                BlockedUserEntry entry = blocked.get(i);
                int itemY = listY + 6 + (i * ITEM_HEIGHT) - (int) scrollOffset;

                if (itemY + ITEM_HEIGHT - 4 <= listY || itemY >= listY + visibleH) {
                    continue;
                }

                boolean hovered = mouseX >= listX + 6 && mouseX <= listX + listW - 6 && mouseY >= itemY && mouseY <= itemY + 24;
                graphics.fill(listX + 6, itemY, listX + listW - 6, itemY + 24, hovered ? GuiHelper.CARD_HOVER : GuiHelper.CARD_BG_ALT);

                // Player name & reason
                graphics.text(this.font, Component.literal("§c🚫 @" + entry.getUsername()), listX + 12, itemY + 4, GuiHelper.COLOR_RED, true);
                graphics.text(this.font, Component.literal("§7Reason: " + entry.getReason()), listX + 140, itemY + 4, GuiHelper.TEXT_SECONDARY, false);

                // Unblock Button
                int unbX = listX + listW - 74;
                int unbY = itemY + 3;
                boolean hUnb = mouseX >= unbX && mouseX <= unbX + 64 && mouseY >= unbY && mouseY <= unbY + 18;
                int unbBg = hUnb ? 0xFF059669 : 0xFF065F46;
                graphics.fill(unbX - 1, unbY - 1, unbX + 65, unbY + 19, hUnb ? 0xFF34D399 : GuiHelper.PANEL_BORDER);
                graphics.fill(unbX, unbY, unbX + 64, unbY + 18, unbBg);
                graphics.text(this.font, Component.literal("✔ Unblock"), unbX + 8, unbY + 5, 0xFFFFFFFF, false);
            }
        }

        // Status message
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), modalX + 105, modalY + modalHeight - 20, GuiHelper.TEXT_PRIMARY, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!OwnerLock.isOwner()) return super.mouseClicked(event, doubleClick);

        double mouseX = event.x();
        double mouseY = event.y();

        boolean inUser = (this.usernameBox != null && mouseX >= this.usernameBox.getX() && mouseX <= this.usernameBox.getX() + this.usernameBox.getWidth()
                && mouseY >= this.usernameBox.getY() && mouseY <= this.usernameBox.getY() + this.usernameBox.getHeight());
        boolean inReason = (this.reasonBox != null && mouseX >= this.reasonBox.getX() && mouseX <= this.reasonBox.getX() + this.reasonBox.getWidth()
                && mouseY >= this.reasonBox.getY() && mouseY <= this.reasonBox.getY() + this.reasonBox.getHeight());

        if (inUser && this.usernameBox.isFocused()) {
            this.usernameBox.setFocused(false);
            this.setFocused(null);
            return true;
        }
        if (inReason && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inUser && this.usernameBox != null && this.usernameBox.isFocused()) {
            this.usernameBox.setFocused(false);
            if (this.getFocused() == this.usernameBox) {
                this.setFocused(null);
            }
        }
        if (!inReason && this.reasonBox != null && this.reasonBox.isFocused()) {
            this.reasonBox.setFocused(false);
            if (this.getFocused() == this.reasonBox) {
                this.setFocused(null);
            }
        }

        if (handled) return true;

        int modalWidth = 480;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int listX = modalX + 16;
        int listY = modalY + 68;
        int listW = modalWidth - 32;
        int visibleH = modalHeight - 100;

        List<BlockedUserEntry> blocked = new ArrayList<>(RatingStorage.getBlockedUsers());

        int startIndex = Math.max(0, (int) (scrollOffset / ITEM_HEIGHT));
        int endIndex = Math.min(blocked.size(), startIndex + (visibleH / ITEM_HEIGHT) + 2);

        for (int i = startIndex; i < endIndex; i++) {
            BlockedUserEntry entry = blocked.get(i);
            int itemY = listY + 6 + (i * ITEM_HEIGHT) - (int) scrollOffset;

            if (itemY + ITEM_HEIGHT - 4 <= listY || itemY >= listY + visibleH) {
                continue;
            }

            int unbX = listX + listW - 74;
            int unbY = itemY + 3;
            if (mouseX >= unbX && mouseX <= unbX + 64 && mouseY >= unbY && mouseY <= unbY + 18) {
                this.statusMessage = "§bUnblocking @" + entry.getUsername() + "...";
                RatingStorage.unblockUserAsync(entry.getUsername(), success -> {
                    if (success) {
                        this.statusMessage = "§aUnblocked @" + entry.getUsername() + "!";
                        rebuildWidgets();
                    } else {
                        this.statusMessage = "§cFailed to unblock user.";
                    }
                });
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
