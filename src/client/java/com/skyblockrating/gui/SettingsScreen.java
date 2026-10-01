package com.skyblockrating.gui;

import com.skyblockrating.config.GuiSize;
import com.skyblockrating.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
    private final Screen parentScreen;
    private EditBox apiKeyBox;
    private String statusMessage = "";

    public enum CategoryTab { VISUAL, MISC, API }
    private CategoryTab activeTab = CategoryTab.VISUAL;

    public SettingsScreen(Screen parentScreen) {
        super(Component.literal("SkyReview Settings"));
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

        int cardWidth = 400;
        int cardHeight = 230;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        int sidebarWidth = 100;
        int contentX = cardX + sidebarWidth + 16;
        int contentW = cardWidth - sidebarWidth - 28;

        // --- Left Sidebar Buttons ---
        int sidebarY = cardY + 40;

        // Visual Tab
        boolean isVisual = (activeTab == CategoryTab.VISUAL);
        this.addRenderableWidget(
            Button.builder(Component.literal((isVisual ? "§6§l> " : "§7") + "Visual"), button -> {
                activeTab = CategoryTab.VISUAL;
                statusMessage = "";
                rebuildWidgets();
            })
            .bounds(cardX + 12, sidebarY, sidebarWidth - 12, 20)
            .build()
        );

        sidebarY += 25;

        // Misc Tab
        boolean isMisc = (activeTab == CategoryTab.MISC);
        this.addRenderableWidget(
            Button.builder(Component.literal((isMisc ? "§6§l> " : "§7") + "Misc"), button -> {
                activeTab = CategoryTab.MISC;
                statusMessage = "";
                rebuildWidgets();
            })
            .bounds(cardX + 12, sidebarY, sidebarWidth - 12, 20)
            .build()
        );

        sidebarY += 25;

        // API Tab (below all other tabs)
        boolean isApi = (activeTab == CategoryTab.API);
        this.addRenderableWidget(
            Button.builder(Component.literal((isApi ? "§6§l> " : "§7") + "API"), button -> {
                activeTab = CategoryTab.API;
                statusMessage = "";
                rebuildWidgets();
            })
            .bounds(cardX + 12, sidebarY, sidebarWidth - 12, 20)
            .build()
        );

        // Back Button at Bottom Left
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(cardX + 12, cardY + cardHeight - 26, sidebarWidth - 12, 20)
            .build()
        );

        // --- Right Content Panel ---
        int contentY = cardY + 42;

        if (activeTab == CategoryTab.VISUAL) {
            // GUI Size Button
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("GUI Size: §e" + ModConfig.getGuiSize().getDisplayName()),
                    button -> {
                        ModConfig.setGuiSize(ModConfig.getGuiSize().next());
                        button.setMessage(Component.literal("GUI Size: §e" + ModConfig.getGuiSize().getDisplayName()));
                    }
                )
                .bounds(contentX, contentY, contentW, 22)
                .build()
            );

            contentY += 32;

            // Theme Selection
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("Theme: §7Obsidian Dark (Default)"),
                    button -> {
                        this.statusMessage = "§eTheme is locked to native Obsidian Dark.";
                    }
                )
                .bounds(contentX, contentY, contentW, 22)
                .build()
            );

        } else if (activeTab == CategoryTab.MISC) {
            // Dungeon & Kuudra Run End Reminder Toggle
            int reminderY = contentY + 10;
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("Run Reminders: " + (ModConfig.isDungeonEndReminder() ? "§aEnabled" : "§cDisabled")),
                    button -> {
                        boolean newState = !ModConfig.isDungeonEndReminder();
                        ModConfig.setDungeonEndReminder(newState);
                        button.setMessage(Component.literal("Run Reminders: " + (newState ? "§aEnabled" : "§cDisabled")));
                        this.statusMessage = newState ? "§aRun review reminders enabled (Dungeons & Kuudra)!" : "§cRun review reminders disabled!";
                    }
                )
                .bounds(contentX, reminderY, contentW, 20)
                .build()
            );

            // Auto-Kick Blacklist Toggle
            int autoKickY = reminderY + 26;
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("Auto-Kick Blacklist: " + (ModConfig.isAutoKickBlacklist() ? "§aEnabled" : "§cDisabled")),
                    button -> {
                        boolean newState = !ModConfig.isAutoKickBlacklist();
                        ModConfig.setAutoKickBlacklist(newState);
                        button.setMessage(Component.literal("Auto-Kick Blacklist: " + (newState ? "§aEnabled" : "§cDisabled")));
                        this.statusMessage = newState ? "§aAuto-kick blacklisted players enabled!" : "§cAuto-kick disabled (warnings only)!";
                    }
                )
                .bounds(contentX, autoKickY, contentW, 20)
                .build()
            );

            // Party Finder Join Rating Toggle
            int pfRatingY = autoKickY + 26;
            this.addRenderableWidget(
                Button.builder(
                    Component.literal("Party Finder Rating: " + (ModConfig.isPartyFinderJoinRating() ? "§aEnabled" : "§cDisabled")),
                    button -> {
                        boolean newState = !ModConfig.isPartyFinderJoinRating();
                        ModConfig.setPartyFinderJoinRating(newState);
                        button.setMessage(Component.literal("Party Finder Rating: " + (newState ? "§aEnabled" : "§cDisabled")));
                        this.statusMessage = newState ? "§aParty Finder join rating in chat enabled!" : "§cParty Finder join rating disabled!";
                    }
                )
                .bounds(contentX, pfRatingY, contentW, 20)
                .build()
            );

        } else if (activeTab == CategoryTab.API) {
            int inputY = contentY + 16;
            this.apiKeyBox = new EditBox(this.font, contentX, inputY, contentW, 20, Component.literal("Hypixel API Key"));
            this.apiKeyBox.setMaxLength(64);

            if (ModConfig.isUsingDefaultApiKey()) {
                this.apiKeyBox.setHint(Component.literal("Cloudflare Server (Default)"));
                this.apiKeyBox.setValue("");
            } else {
                this.apiKeyBox.setValue(ModConfig.getHypixelApiKey());
            }

            this.addRenderableWidget(this.apiKeyBox);

            int btnY = contentY + 54;
            int halfBtnW = (contentW - 8) / 2;

            // Save Key
            this.addRenderableWidget(
                Button.builder(Component.literal("✔ Save Key"), button -> {
                    String val = this.apiKeyBox.getValue().trim();
                    if (!val.isEmpty()) {
                        ModConfig.setHypixelApiKey(val);
                        this.statusMessage = "§aCustom API Key Saved!";
                    } else {
                        ModConfig.resetToDefaultApiKey();
                        this.apiKeyBox.setValue("");
                        this.apiKeyBox.setHint(Component.literal("Cloudflare Server (Default)"));
                        this.statusMessage = "§eReverted to Cloudflare Server Default!";
                    }
                })
                .bounds(contentX, btnY, halfBtnW, 20)
                .build()
            );

            // Reset Default Key
            this.addRenderableWidget(
                Button.builder(Component.literal("🔄 Reset Default"), button -> {
                    ModConfig.resetToDefaultApiKey();
                    this.apiKeyBox.setValue("");
                    this.apiKeyBox.setHint(Component.literal("Cloudflare Server (Default)"));
                    this.statusMessage = "§eReverted to Cloudflare Server Default!";
                })
                .bounds(contentX + halfBtnW + 8, btnY, halfBtnW, 20)
                .build()
            );
        }

        // --- Top Right: Links Menu Button (aligned with Settings title) ---
        int linksBtnW = 75;
        int linksBtnH = 18;
        int linksBtnX = cardX + cardWidth - linksBtnW - 12;
        int linksBtnY = cardY + 5;

        this.addRenderableWidget(
            Button.builder(Component.literal("🔗 Links →"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new LinksModalScreen(this));
                }
            })
            .bounds(linksBtnX, linksBtnY, linksBtnW, linksBtnH)
            .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int cardWidth = 400;
        int cardHeight = 230;
        int cardX = (this.width - cardWidth) / 2;
        int cardY = (this.height - cardHeight) / 2;

        int sidebarWidth = 100;

        // Container
        GuiHelper.renderPanel(graphics, cardX, cardY, cardWidth, cardHeight, 30, GuiHelper.COLOR_GOLD);

        // Sidebar Separator
        graphics.fill(cardX + sidebarWidth + 6, cardY + 30, cardX + sidebarWidth + 7, cardY + cardHeight, GuiHelper.PANEL_BORDER);

        // Title
        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fSettings"), cardX + 14, cardY + 9, GuiHelper.TEXT_TITLE, true);

        // Content Area Labels
        int contentX = cardX + sidebarWidth + 16;
        int contentY = cardY + 42;

        if (activeTab == CategoryTab.API) {
            graphics.text(this.font, Component.literal("§7Custom Hypixel API Key:"), contentX, contentY + 2, GuiHelper.TEXT_SECONDARY, false);

            if (ModConfig.isUsingDefaultApiKey()) {
                graphics.text(this.font, Component.literal("§a✔ Using built-in protected API key"), contentX, contentY + 40, GuiHelper.COLOR_GREEN, false);
            }
        }

        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), contentX, cardY + cardHeight - 20, GuiHelper.COLOR_GREEN, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        boolean inBox = (this.apiKeyBox != null && mouseX >= this.apiKeyBox.getX() && mouseX <= this.apiKeyBox.getX() + this.apiKeyBox.getWidth()
                && mouseY >= this.apiKeyBox.getY() && mouseY <= this.apiKeyBox.getY() + this.apiKeyBox.getHeight());

        if (inBox && this.apiKeyBox.isFocused()) {
            this.apiKeyBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inBox && this.apiKeyBox != null && this.apiKeyBox.isFocused()) {
            this.apiKeyBox.setFocused(false);
            if (this.getFocused() == this.apiKeyBox) {
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
