package com.skyblockrating.gui;

import com.skyblockrating.config.AdminManager;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AdminControlScreen extends Screen {
    private final Screen parentScreen;

    public AdminControlScreen(Screen parentScreen) {
        super(Component.literal("SkyReview Admin Panel"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 360;
        int modalHeight = 295;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int currentY = modalY + 38;
        int fieldW = modalWidth - 40;

        // Toggle 0: Mod Maintenance Mode (Remotely enables/disables mod for all users)
        boolean isMaint = RatingStorage.isModDisabled();
        Button maintToggleBtn = Button.builder(
            Component.literal("Mod Status: " + (isMaint ? "§c[DISABLED (MAINTENANCE)]" : "§a[ACTIVE / ENABLED]")),
            btn -> {
                boolean nextState = !RatingStorage.isModDisabled();
                btn.active = false;
                btn.setMessage(Component.literal("Updating Server..."));
                RatingStorage.setMaintenanceModeAsync(nextState, "Mod is temporarily disabled for maintenance.", success -> {
                    btn.active = true;
                    btn.setMessage(Component.literal("Mod Status: " + (nextState ? "§c[DISABLED (MAINTENANCE)]" : "§a[ACTIVE / ENABLED]")));
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal(nextState 
                            ? "§c[SkyReview] Mod is now DISABLED for all players (Maintenance Mode ON)."
                            : "§a[SkyReview] Mod is now ACTIVE for all players (Maintenance Mode OFF)."));
                    }
                });
            }
        )
        .bounds(modalX + 20, currentY, fieldW, 20)
        .build();
        this.addRenderableWidget(maintToggleBtn);

        currentY += 25;

        // Toggle 1: Delete Reviews Option
        Button deleteToggleBtn = Button.builder(
            Component.literal("Delete Reviews: " + (AdminManager.isDeleteReviewsEnabled() ? "§a[ON]" : "§c[OFF]")),
            btn -> {
                boolean nextState = !AdminManager.isDeleteReviewsEnabled();
                AdminManager.setDeleteReviewsEnabled(nextState);
                btn.setMessage(Component.literal("Delete Reviews: " + (nextState ? "§a[ON]" : "§c[OFF]")));
            }
        )
        .bounds(modalX + 20, currentY, fieldW, 20)
        .build();
        this.addRenderableWidget(deleteToggleBtn);

        currentY += 25;

        // Toggle 2: Bypass Cooldown Option
        Button cooldownToggleBtn = Button.builder(
            Component.literal("Bypass Cooldown: " + (AdminManager.isBypassCooldownEnabled() ? "§a[ON]" : "§c[OFF]")),
            btn -> {
                boolean nextState = !AdminManager.isBypassCooldownEnabled();
                AdminManager.setBypassCooldownEnabled(nextState);
                btn.setMessage(Component.literal("Bypass Cooldown: " + (nextState ? "§a[ON]" : "§c[OFF]")));
            }
        )
        .bounds(modalX + 20, currentY, fieldW, 20)
        .build();
        this.addRenderableWidget(cooldownToggleBtn);

        currentY += 25;

        // Button 3: Create Prefix Template
        this.addRenderableWidget(
            Button.builder(Component.literal("✨ Create Prefix Template →"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminPrefixCreateScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, fieldW, 20)
            .build()
        );

        currentY += 26;

        // Button 4: Assign Prefix to Player
        this.addRenderableWidget(
            Button.builder(Component.literal("👑 Assign Prefix to Player →"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminPrefixAssignScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, fieldW, 20)
            .build()
        );

        currentY += 26;

        // Button 5: Moderator Reports
        int pendingReportsCount = RatingStorage.getPendingReports().size();
        String repLabel = "🚩 Moderator Reports " + (pendingReportsCount > 0 ? "§c(" + pendingReportsCount + " pending)" : "§7(0)") + " →";
        this.addRenderableWidget(
            Button.builder(Component.literal(repLabel), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminReportsChoiceScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, fieldW, 20)
            .build()
        );

        currentY += 26;

        // Button 6: Blocked Users Manager
        this.addRenderableWidget(
            Button.builder(Component.literal("🚫 Review Blacklist (Blocked Users) →"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminBlockedUsersScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, fieldW, 20)
            .build()
        );

        // Back Button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + (modalWidth - 100) / 2, modalY + modalHeight - 24, 100, 18)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 360;
        int modalHeight = 295;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fAdmin Control Panel"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);
        graphics.text(this.font, Component.literal("§c[ADMIN]"), modalX + modalWidth - 55, modalY + 9, GuiHelper.COLOR_RED, true);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
