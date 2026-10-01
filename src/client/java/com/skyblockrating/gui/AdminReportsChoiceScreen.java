package com.skyblockrating.gui;

import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.ReportEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AdminReportsChoiceScreen extends Screen {
    private final Screen parentScreen;

    public AdminReportsChoiceScreen(Screen parentScreen) {
        super(Component.literal("Moderator Reports"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int modalWidth = 360;
        int modalHeight = 220;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        List<ReportEntry> reports = RatingStorage.getPendingReports();
        int reviewRepCount = 0;
        int aboutMeRepCount = 0;
        for (ReportEntry r : reports) {
            if (r.isAboutMe()) aboutMeRepCount++;
            else reviewRepCount++;
        }

        int btnW = modalWidth - 40;
        int currentY = modalY + 56;

        // Button 1: Review Reports
        String revBadge = reviewRepCount > 0 ? " §c(" + reviewRepCount + " pending)" : " §7(0)";
        this.addRenderableWidget(
            Button.builder(Component.literal("📝 Review Reports" + revBadge + " →"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminReportsScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, btnW, 26)
            .build()
        );

        currentY += 38;

        // Button 2: About Me Reports
        String aboutBadge = aboutMeRepCount > 0 ? " §c(" + aboutMeRepCount + " pending)" : " §7(0)";
        this.addRenderableWidget(
            Button.builder(Component.literal("👤 About Me Reports" + aboutBadge + " →"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AdminAboutMeReportsScreen(this));
                }
            })
            .bounds(modalX + 20, currentY, btnW, 26)
            .build()
        );

        // Back Button
        int buttonY = modalY + modalHeight - 28;
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + (modalWidth - 100) / 2, buttonY, 100, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 360;
        int modalHeight = 220;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT_ADMIN);

        // Header Title
        graphics.text(this.font, Component.literal("§c§lSky§4§lReview §8| §fModerator Reports"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);
        graphics.text(this.font, Component.literal("§c[ADMIN]"), modalX + modalWidth - 55, modalY + 9, GuiHelper.COLOR_RED, true);

        if (!OwnerLock.isOwner()) {
            graphics.text(this.font, Component.literal("§cACCESS DENIED: Owner Privileges Required"), modalX + 50, modalY + 90, GuiHelper.COLOR_RED, true);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }

        // Subtitle instructions
        graphics.text(this.font, Component.literal("§7Select report category to moderate:"), modalX + 20, modalY + 38, GuiHelper.TEXT_SECONDARY, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
