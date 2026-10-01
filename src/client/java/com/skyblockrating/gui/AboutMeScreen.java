package com.skyblockrating.gui;

import com.skyblockrating.data.UserProfileEntry;
import com.skyblockrating.storage.RatingStorage;
import com.skyblockrating.util.PlayerSkinEntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class AboutMeScreen extends Screen {
    private final Screen parentScreen;
    private final String targetPlayer;
    private final boolean isOwnProfile;

    private boolean isEditMode = false;
    private boolean profileFetched = false;
    private MultiLineEditBox bioBox;
    private EditBox discordBox;
    private String currentBio = "";
    private String currentDiscord = "";
    private String statusMessage = "";

    private static final String[] POPULAR_EMOJIS = {
        "⭐", "🔥", "⚔️", "🛡️", "🏹", "❤️", "👑", "🎮", 
        "✨", "💎", "💀", "⚡", "🏆", "🎯", "💥", "🍀", 
        "👾", "🍕", "☕", "👀", "👍", "💯", "🚀", "🎉", 
        "🌟", "😎", "💪", "🐉"
    };

    public AboutMeScreen(Screen parentScreen, String targetPlayer) {
        super(Component.literal("About " + targetPlayer));
        this.parentScreen = parentScreen;
        this.targetPlayer = targetPlayer != null ? targetPlayer.trim() : "Player";
        Minecraft mc = Minecraft.getInstance();
        this.isOwnProfile = (mc != null && mc.getUser() != null && mc.getUser().getName().equalsIgnoreCase(this.targetPlayer));
        this.isEditMode = false; // Always start in View Mode!
    }

    @Override
    protected void init() {
        super.init();

        // Fetch latest profile from Cloudflare D1 ONCE without resetting active editors
        if (!profileFetched) {
            profileFetched = true;
            RatingStorage.fetchUserProfileAsync(targetPlayer, profile -> {
                if (profile != null) {
                    this.currentBio = profile.getBio();
                    this.currentDiscord = profile.getDiscordTag();
                    if (bioBox != null) bioBox.setValue(this.currentBio);
                    if (discordBox != null) discordBox.setValue(this.currentDiscord);
                }
            });
        }

        // Preload player 3D mannequin in cache
        PlayerSkinEntityCache.getOrFetchMannequin(targetPlayer);

        int modalWidth = 540;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int leftX = modalX + 16;
        int leftW = 150;
        int leftY = modalY + 36;
        int rightX = modalX + 180;
        int rightW = modalWidth - 196;
        int rightY = modalY + 36;

        UserProfileEntry cached = RatingStorage.getUserProfile(targetPlayer);
        if (cached != null && this.currentBio.isEmpty() && this.currentDiscord.isEmpty()) {
            this.currentBio = cached.getBio();
            this.currentDiscord = cached.getDiscordTag();
        }

        if (isEditMode) {
            // --- EDIT MODE WIDGETS ---
            // 1. Discord single-line EditBox under skin box
            int discordY = leftY + 180;
            this.discordBox = new EditBox(this.font, leftX + 8, discordY, leftW - 16, 18, Component.literal("Discord"));
            this.discordBox.setMaxLength(32);
            this.discordBox.setHint(Component.literal("Discord (@user)..."));
            this.discordBox.setValue(this.currentDiscord);
            this.discordBox.setResponder(txt -> this.currentDiscord = txt);
            this.addRenderableWidget(this.discordBox);

            // 2. Multiline Bio Text Box (16 lines max)
            int cardY = rightY + 16;
            this.bioBox = MultiLineEditBox.builder()
                .build(this.font, rightW, 168, Component.literal("Bio"));
            this.bioBox.setPosition(rightX, cardY);
            this.bioBox.setCharacterLimit(200);
            this.bioBox.setLineLimit(16);
            this.bioBox.setValue(this.currentBio);
            this.bioBox.setValueListener(txt -> this.currentBio = txt);
            this.addRenderableWidget(this.bioBox);

            // 3. Emojis Button below bio input
            int actionY = cardY + 176;
            this.addRenderableWidget(
                Button.builder(Component.literal("😀 Emojis ▼"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SelectorPopupScreen(this, "Select Emoji to Insert", POPULAR_EMOJIS, emoji -> {
                            if (bioBox != null) {
                                String current = bioBox.getValue();
                                if (current.length() + emoji.length() <= 200) {
                                    bioBox.setValue(current.isEmpty() ? emoji : (current + " " + emoji));
                                    this.currentBio = bioBox.getValue();
                                }
                            }
                        }));
                    }
                })
                .bounds(rightX, actionY, 85, 20)
                .build()
            );

            // 4. Cancel Edit Button
            this.addRenderableWidget(
                Button.builder(Component.literal("✕ Cancel"), button -> {
                    this.isEditMode = false;
                    rebuildWidgets();
                })
                .bounds(rightX + rightW - 180, actionY, 65, 20)
                .build()
            );

            // 5. Save Changes Button
            this.addRenderableWidget(
                Button.builder(Component.literal("💾 Save Profile"), button -> {
                    if (!com.skyblockrating.auth.SessionAuthManager.isLicensedUser()) {
                        this.statusMessage = "§cLicense required! Run /sr reload";
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MSG));
                        }
                        return;
                    }
                    String bio = (bioBox != null) ? bioBox.getValue().trim() : this.currentBio.trim();
                    String discord = (discordBox != null) ? discordBox.getValue().trim() : this.currentDiscord.trim();
                    this.statusMessage = "§bSaving profile...";
                    RatingStorage.saveUserProfileAsync(targetPlayer, bio, discord, success -> {
                        if (success) {
                            this.currentBio = bio;
                            this.currentDiscord = discord;
                            this.isEditMode = false;
                            this.statusMessage = "§aProfile updated!";
                            rebuildWidgets();
                        } else {
                            this.statusMessage = "§cFailed to save profile.";
                        }
                    });
                })
                .bounds(rightX + rightW - 110, actionY, 110, 20)
                .build()
            );

        } else {
            // --- VIEW MODE WIDGETS ---
            // Copy Discord Button (if discord exists)
            if (!this.currentDiscord.isEmpty()) {
                int discordBtnY = leftY + 192;
                this.addRenderableWidget(
                    Button.builder(Component.literal("📋 Copy Discord"), button -> {
                        if (this.minecraft != null) {
                            this.minecraft.keyboardHandler.setClipboard(this.currentDiscord);
                            this.statusMessage = "§aCopied Discord tag: " + this.currentDiscord;
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Copied Discord tag: §b" + this.currentDiscord));
                            }
                        }
                    })
                    .bounds(leftX + 8, discordBtnY, leftW - 16, 18)
                    .build()
                );
            }

            if (isOwnProfile) {
                int editBtnY = modalY + modalHeight - 28;
                int myRevCount = RatingStorage.getMyReviews().size();
                String myRevLabel = "📝 My Reviews (" + myRevCount + ")";
                int revBtnW = 110;

                this.addRenderableWidget(
                    Button.builder(Component.literal(myRevLabel), button -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new MyReviewsScreen(this));
                        }
                    })
                    .bounds(rightX + rightW - 115 - revBtnW - 8, editBtnY, revBtnW, 20)
                    .build()
                );

                this.addRenderableWidget(
                    Button.builder(Component.literal("✏ Edit Profile"), button -> {
                        this.isEditMode = true;
                        this.statusMessage = "";
                        rebuildWidgets();
                    })
                    .bounds(rightX + rightW - 115, editBtnY, 115, 20)
                    .build()
                );
            } else {
                int reportBtnY = modalY + modalHeight - 28;
                this.addRenderableWidget(
                    Button.builder(Component.literal("🚩 Report About Me"), button -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new ReportAboutMeDialogScreen(this, targetPlayer, this.currentBio, this.currentDiscord));
                        }
                    })
                    .bounds(rightX + rightW - 135, reportBtnY, 135, 20)
                    .build()
                );
            }
        }

        // Back Button at Bottom Left
        int buttonY = modalY + modalHeight - 28;
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(leftX, buttonY, 80, 20)
            .build()
        );
    }

    private void renderPlayer3DSkin(GuiGraphicsExtractor graphics, int boxX, int boxY, int boxW, int boxH, int mouseX, int mouseY) {
        LivingEntity entity = PlayerSkinEntityCache.getOrFetchMannequin(targetPlayer);
        if (entity != null) {
            try {
                int x1 = boxX;
                int y1 = boxY;
                int x2 = boxX + boxW;
                int y2 = boxY + boxH;
                InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, 45, 0.0625F, (float) mouseX, (float) mouseY, entity);
                return;
            } catch (Throwable ignored) {}
        }

        String icon = "👤";
        int icW = this.font.width(icon);
        graphics.text(this.font, Component.literal(icon), boxX + (boxW - icW) / 2, boxY + (boxH / 2) - 4, GuiHelper.COLOR_GOLD, true);
    }

    private void renderScaledPlayerWithPrefix(GuiGraphicsExtractor graphics, String playerName, com.skyblockrating.data.UserPrefixEntry prefixEntry, int centerX, int y, int maxAllowedWidth) {
        String pfx = (prefixEntry != null && prefixEntry.getPrefixName() != null && !prefixEntry.getPrefixName().trim().isEmpty()) 
            ? ("[" + prefixEntry.getPrefixName() + "] ") 
            : "@";
        String combined = pfx + playerName;
        int totalW = this.font.width(combined);

        if (totalW <= maxAllowedWidth) {
            int startX = centerX - (totalW / 2);
            if (prefixEntry != null && prefixEntry.getPrefixName() != null && !prefixEntry.getPrefixName().trim().isEmpty()) {
                GuiHelper.renderPlayerWithPrefix(graphics, this.font, playerName, prefixEntry, startX, y, true);
            } else {
                graphics.text(this.font, Component.literal("§e" + combined), startX, y, GuiHelper.COLOR_YELLOW, true);
            }
        } else {
            float scale = (float) maxAllowedWidth / (float) totalW;
            int scaledW = (int) (totalW * scale);
            int startX = centerX - (scaledW / 2);

            graphics.pose().pushMatrix();
            graphics.pose().translate(startX, y);
            graphics.pose().scale(scale, scale);
            if (prefixEntry != null && prefixEntry.getPrefixName() != null && !prefixEntry.getPrefixName().trim().isEmpty()) {
                GuiHelper.renderPlayerWithPrefix(graphics, this.font, playerName, prefixEntry, 0, 0, true);
            } else {
                graphics.text(this.font, Component.literal("§e" + combined), 0, 0, GuiHelper.COLOR_YELLOW, true);
            }
            graphics.pose().popMatrix();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 540;
        int modalHeight = 310;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.COLOR_GOLD);

        // Header Title
        String title = isOwnProfile ? "§6§lSky§e§lReview §8| §fMy Profile" : ("§6§lSky§e§lReview §8| §fProfile: @" + targetPlayer);
        graphics.text(this.font, Component.literal(title), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);

        // --- Left Section (1/3 column) ---
        int leftX = modalX + 16;
        int leftW = 150;
        int leftY = modalY + 36;
        int leftH = modalHeight - 74;
        GuiHelper.renderCard(graphics, leftX, leftY, leftW, leftH, false);

        // 1. Top of Left Column: Player Nick & Custom Prefix (scaled dynamically on 1 line if wider than frame)
        com.skyblockrating.data.UserPrefixEntry prefixEntry = RatingStorage.getUserPrefix(targetPlayer);
        int maxTextW = leftW - 16;
        int centerX = leftX + (leftW / 2);
        renderScaledPlayerWithPrefix(graphics, targetPlayer, prefixEntry, centerX, leftY + 8, maxTextW);

        // 2. Middle of Left Column: Rectangular Skin Window Border & 3D Mannequin
        int rectW = 96;
        int rectH = 120;
        int rectX = leftX + (leftW - rectW) / 2;
        int rectY = leftY + 24;
        graphics.fill(rectX - 1, rectY - 1, rectX + rectW + 1, rectY + rectH + 1, GuiHelper.PANEL_BORDER_LIGHT);
        graphics.fill(rectX, rectY, rectX + rectW, rectY + rectH, 0xFF14151D);
        renderPlayer3DSkin(graphics, rectX, rectY, rectW, rectH, mouseX, mouseY);

        // 3. Bottom of Left Column: Discord Information
        int discordLabelY = leftY + 154;
        if (isEditMode) {
            graphics.text(this.font, Component.literal("§7Discord Tag:"), leftX + 8, discordLabelY + 10, GuiHelper.TEXT_MUTED, false);
        } else {
            if (currentDiscord.isEmpty()) {
                graphics.text(this.font, Component.literal("§7No Discord linked"), leftX + 10, discordLabelY + 16, GuiHelper.TEXT_MUTED, false);
            } else {
                String dText = "§9💬 §f" + currentDiscord;
                int dW = this.font.width(dText);
                if (dW > leftW - 16) {
                    dText = "§9💬 §f" + currentDiscord.substring(0, Math.min(currentDiscord.length(), 14)) + "..";
                }
                graphics.text(this.font, Component.literal(dText), leftX + 8, discordLabelY + 16, 0xFF5865F2, false);
            }
        }

        // --- Right Section (2/3 column) ---
        int rightX = modalX + 180;
        int rightW = modalWidth - 196;
        int rightY = modalY + 36;

        if (isEditMode) {
            graphics.text(this.font, Component.literal("§6§lEdit Bio (About Me):"), rightX, rightY + 2, GuiHelper.COLOR_GOLD, true);

            int charCount = (bioBox != null) ? bioBox.getValue().length() : currentBio.length();
            String counter = (charCount > 180 ? "§e" : "§8") + charCount + "/200";
            graphics.text(this.font, Component.literal(counter), rightX + rightW - this.font.width(counter), rightY + 2, GuiHelper.TEXT_MUTED, false);
        } else {
            graphics.text(this.font, Component.literal("§6§lAbout @" + targetPlayer + ":"), rightX, rightY + 2, GuiHelper.COLOR_GOLD, true);

            int bioCardY = rightY + 16;
            int bioCardH = modalHeight - 88;
            GuiHelper.renderCard(graphics, rightX, bioCardY, rightW, bioCardH, false);

            if (currentBio.isEmpty()) {
                graphics.text(this.font, Component.literal("§7This player hasn't written anything"), rightX + 14, bioCardY + 14, GuiHelper.TEXT_MUTED, false);
                graphics.text(this.font, Component.literal("§7about themselves yet."), rightX + 14, bioCardY + 28, GuiHelper.TEXT_MUTED, false);
            } else {
                // Multi-line rendering with support for \n linebreaks and wrap (16 lines max)
                String[] paragraphs = currentBio.split("\n", -1);
                int lineY = bioCardY + 12;
                int maxLines = 16;
                int renderedCount = 0;

                for (String p : paragraphs) {
                    if (renderedCount >= maxLines) break;
                    if (p.isEmpty()) {
                        lineY += 13;
                        renderedCount++;
                        continue;
                    }
                    List<net.minecraft.util.FormattedCharSequence> splitLines = this.font.split(Component.literal("§f" + p), rightW - 28);
                    for (net.minecraft.util.FormattedCharSequence seq : splitLines) {
                        if (renderedCount >= maxLines) break;
                        graphics.text(this.font, seq, rightX + 14, lineY, 0xFFFFFFFF, false);
                        lineY += 13;
                        renderedCount++;
                    }
                }
            }
        }

        // Status Toast Message
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, Component.literal(statusMessage), leftX + 90, modalY + modalHeight - 22, GuiHelper.COLOR_GREEN, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        boolean inDiscord = (this.discordBox != null && mouseX >= this.discordBox.getX() && mouseX <= this.discordBox.getX() + this.discordBox.getWidth()
                && mouseY >= this.discordBox.getY() && mouseY <= this.discordBox.getY() + this.discordBox.getHeight());
        boolean inBio = (this.bioBox != null && mouseX >= this.bioBox.getX() && mouseX <= this.bioBox.getX() + this.bioBox.getWidth()
                && mouseY >= this.bioBox.getY() && mouseY <= this.bioBox.getY() + this.bioBox.getHeight());

        if (inDiscord && this.discordBox.isFocused()) {
            this.discordBox.setFocused(false);
            this.setFocused(null);
            return true;
        }
        if (inBio && this.bioBox.isFocused()) {
            this.bioBox.setFocused(false);
            this.setFocused(null);
            return true;
        }

        boolean handled = super.mouseClicked(event, doubleClick);

        if (!inDiscord && this.discordBox != null && this.discordBox.isFocused()) {
            this.discordBox.setFocused(false);
            if (this.getFocused() == this.discordBox) {
                this.setFocused(null);
            }
        }
        if (!inBio && this.bioBox != null && this.bioBox.isFocused()) {
            this.bioBox.setFocused(false);
            if (this.getFocused() == this.bioBox) {
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
