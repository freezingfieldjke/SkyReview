package com.skyblockrating.gui;

import com.skyblockrating.util.PlayerResolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class RatePlayerPromptScreen extends Screen {
    private final Screen parentScreen;
    private EditBox ignBox;
    private String errorMessage = "";

    public RatePlayerPromptScreen(Screen parentScreen) {
        super(Component.literal("Rate Player Prompt"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int cardWidth = 300;
        int cardHeight = 160;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        this.ignBox = new EditBox(this.font, cardX + 20, cardY + 54, cardWidth - 40, 20, Component.literal("Player IGN"));
        this.ignBox.setHint(Component.literal("Enter exact Minecraft IGN..."));
        this.addRenderableWidget(this.ignBox);
        this.setInitialFocus(this.ignBox);

        int buttonY = cardY + cardHeight - 28;

        this.addRenderableWidget(
            Button.builder(Component.literal("Continue →"), button -> {
                String ign = this.ignBox.getValue().trim();
                if (ign.isEmpty()) {
                    this.errorMessage = "§cPlease enter an IGN!";
                    return;
                }

                if (com.skyblockrating.storage.RatingStorage.isCurrentClientBlocked()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[SkyReview] Your account is restricted from posting reviews. If you disagree with this block, please write to us on Discord!"));
                    }
                    this.errorMessage = "§cAccount restricted from reviewing!";
                    return;
                }

                String selfName = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "";
                if (!selfName.isEmpty() && ign.equalsIgnoreCase(selfName)) {
                    this.errorMessage = "§cr u srsly?";
                    return;
                }

                this.errorMessage = "§bVerifying with Mojang...";
                PlayerResolver.validateSkyBlockPlayerAsync(ign, officialIgn -> {
                    if (officialIgn != null) {
                        if (!selfName.isEmpty() && officialIgn.equalsIgnoreCase(selfName)) {
                            this.errorMessage = "§cr u srsly?";
                            return;
                        }
                        if (this.minecraft != null) {
                            this.minecraft.execute(() -> this.minecraft.setScreen(new RatePlayerScreen(officialIgn)));
                        }
                    } else {
                        this.errorMessage = "§cPlayer not found!";
                    }
                });
            })
            .bounds(cardX + 20, buttonY, 120, 20)
            .build()
        );

        this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(cardX + cardWidth - 130, buttonY, 110, 20)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int cardWidth = 300;
        int cardHeight = 160;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        GuiHelper.renderPanel(graphics, cardX, cardY, cardWidth, cardHeight, 30, GuiHelper.COLOR_GOLD);

        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fRate Player"), cardX + 14, cardY + 9, GuiHelper.TEXT_TITLE, true);
        graphics.text(this.font, Component.literal("§7Target Player Username:"), cardX + 20, cardY + 40, GuiHelper.TEXT_SECONDARY, false);

        if (!errorMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(errorMessage), cardX + 20, cardY + 82, GuiHelper.COLOR_RED, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        boolean inBox = (this.ignBox != null && mouseX >= this.ignBox.getX() && mouseX <= this.ignBox.getX() + this.ignBox.getWidth()
                && mouseY >= this.ignBox.getY() && mouseY <= this.ignBox.getY() + this.ignBox.getHeight());

        if (inBox && this.ignBox.isFocused()) {
            this.ignBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inBox && this.ignBox != null && this.ignBox.isFocused()) {
            this.ignBox.setFocused(false);
            if (this.getFocused() == this.ignBox) {
                this.setFocused(null);
            }
        }

        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
