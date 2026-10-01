package com.skyblockrating.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SelectorPopupScreen extends Screen {
    private final Screen parentScreen;
    private final String titleText;
    private final String[] options;
    private final Consumer<String> onSelect;
    private boolean hasResetOption = false;

    public SelectorPopupScreen(Screen parentScreen, String titleText, String[] options, Consumer<String> onSelect) {
        super(Component.literal(titleText));
        this.parentScreen = parentScreen;
        this.titleText = titleText;
        this.options = options;
        this.onSelect = onSelect;
        if (options != null) {
            for (String opt : options) {
                if ("All".equalsIgnoreCase(opt) || "Reset".equalsIgnoreCase(opt)) {
                    this.hasResetOption = true;
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        boolean isDungeons = titleText.contains("Dungeons");
        boolean isMultiCol = !isDungeons && options != null && options.length > 7;

        int modalWidth = isDungeons ? 300 : (isMultiCol ? 320 : 260);
        int modalHeight;
        if (isDungeons) {
            modalHeight = hasResetOption ? 266 : 240;
        } else if (isMultiCol) {
            int itemsPerCol = (options.length + 1) / 2;
            modalHeight = 38 + itemsPerCol * 20 + 32;
        } else {
            modalHeight = 40 + (options != null ? options.length * 24 : 0) + 30;
        }

        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int buttonWidth = 120;
        int buttonHeight = 18;

        if (isDungeons) {
            int startY = modalY + 46;

            // Reset / All Floors Button at the top when selecting filter
            if (hasResetOption) {
                this.addRenderableWidget(
                    Button.builder(Component.literal("§e★ All Floors (Reset Filter)"), button -> {
                        onSelect.accept("All");
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        }
                    })
                    .bounds(modalX + 20, modalY + 28, 260, 18)
                    .build()
                );
                startY = modalY + 68;
            }

            // 2-Column Layout: F1-F7 Left (Orange), M1-M7 Right (Red)
            String[] normalFloors = {"F1", "F2", "F3", "F4", "F5", "F6", "F7"};
            String[] masterFloors = {"M1", "M2", "M3", "M4", "M5", "M6", "M7"};

            int col1X = modalX + 20;
            int col2X = modalX + 160;

            for (int i = 0; i < 7; i++) {
                final String fNorm = normalFloors[i];
                final String fMaster = masterFloors[i];
                int y = startY + i * 22;

                // Left Column (Normal Floors F1-F7 - Orange)
                this.addRenderableWidget(
                    Button.builder(Component.literal("§6" + fNorm), button -> {
                        onSelect.accept(fNorm);
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        }
                    })
                    .bounds(col1X, y, buttonWidth, buttonHeight)
                    .build()
                );

                // Right Column (Master Floors M1-M7 - Red)
                this.addRenderableWidget(
                    Button.builder(Component.literal("§c" + fMaster), button -> {
                        onSelect.accept(fMaster);
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        }
                    })
                    .bounds(col2X, y, buttonWidth, buttonHeight)
                    .build()
                );
            }
        } else if (isMultiCol) {
            // 2-Column Compact Grid for Colors (16 options)
            int itemsPerCol = (options.length + 1) / 2;
            int startY = modalY + 34;
            int col1X = modalX + 16;
            int col2X = modalX + 164;
            int colWidth = 140;

            for (int i = 0; i < options.length; i++) {
                final String opt = options[i];
                int col = i / itemsPerCol;
                int row = i % itemsPerCol;
                int x = (col == 0) ? col1X : col2X;
                int y = startY + row * 20;

                this.addRenderableWidget(
                    Button.builder(Component.literal(opt), button -> {
                        onSelect.accept(opt);
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        }
                    })
                    .bounds(x, y, colWidth, 18)
                    .build()
                );
            }
        } else {
            // Standard Single Column List for Kuudra Tiers & General options
            int startY = modalY + 36;
            int colX = modalX + (modalWidth - 160) / 2;

            for (int i = 0; i < options.length; i++) {
                final String opt = options[i];
                int y = startY + i * 24;

                this.addRenderableWidget(
                    Button.builder(Component.literal(opt.equalsIgnoreCase("All") ? "§e★ All (Reset Filter)" : opt), button -> {
                        onSelect.accept(opt);
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parentScreen);
                        }
                    })
                    .bounds(colX, y, 160, 20)
                    .build()
                );
            }
        }

        // Cancel button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Cancel"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + (modalWidth - 80) / 2, modalY + modalHeight - 24, 80, 18)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        boolean isDungeons = titleText.contains("Dungeons");
        boolean isMultiCol = !isDungeons && options != null && options.length > 7;

        int modalWidth = isDungeons ? 300 : (isMultiCol ? 320 : 260);
        int modalHeight;
        if (isDungeons) {
            modalHeight = hasResetOption ? 266 : 240;
        } else if (isMultiCol) {
            int itemsPerCol = (options.length + 1) / 2;
            modalHeight = 38 + itemsPerCol * 20 + 32;
        } else {
            modalHeight = 40 + (options != null ? options.length * 24 : 0) + 30;
        }

        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 26, GuiHelper.COLOR_GOLD);

        graphics.text(this.font, Component.literal("§6§l" + titleText), modalX + 14, modalY + 8, GuiHelper.TEXT_TITLE, true);

        if (titleText.contains("Dungeons")) {
            int headerY = hasResetOption ? (modalY + 52) : (modalY + 34);
            graphics.text(this.font, Component.literal("§6Normal"), modalX + 22, headerY, GuiHelper.COLOR_ORANGE, false);
            graphics.text(this.font, Component.literal("§cMaster"), modalX + 162, headerY, GuiHelper.COLOR_RED, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
