package com.skyblockrating.gui;

import com.skyblockrating.api.HypixelApiFetcher;
import com.skyblockrating.config.AdminManager;
import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.data.ReviewEntry;
import com.skyblockrating.storage.RatingStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileScreen extends Screen {
    private final Screen parentScreen;
    private final PlayerRatingEntry playerData;
    private HypixelApiFetcher.HypixelStats hypixelStats;

    public enum StatCategory {
        DUNGEONS,
        KUUDRA
    }
    private StatCategory activeStat = StatCategory.DUNGEONS;

    // Filter controls
    private enum SortMode { NEWEST, TOP_RATED, LOWEST_RATED, MOST_LIKED, OLDEST }
    private SortMode currentSort = SortMode.NEWEST;
    private String selectedFilterTag = "All";

    private String getSortButtonText() {
        switch (currentSort) {
            case NEWEST: return "Sort: Newest";
            case TOP_RATED: return "Sort: Top Rated (5★→1★)";
            case LOWEST_RATED: return "Sort: Lowest (1★→5★)";
            case MOST_LIKED: return "Sort: Most Liked";
            case OLDEST: return "Sort: Oldest";
            default: return "Sort: Newest";
        }
    }

    // Mouse Scrolling for reviews
    private double reviewScrollOffset = 0;
    private static final int REVIEW_ITEM_HEIGHT = 78;

    private final String[] dungeonFilterOpts = {"All", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};
    private final String[] kuudraFilterOpts = {"All", "Basic", "Hot", "Burning", "Fiery", "Infernal"};
    private final String[] otherFilterOpts = {"All", "General", "Carry", "Helping"};

    private String highlightReviewId = null;

    public ProfileScreen(Screen parentScreen, PlayerRatingEntry playerData) {
        this(parentScreen, playerData, null);
    }

    public ProfileScreen(Screen parentScreen, PlayerRatingEntry playerData, String highlightReviewId) {
        super(Component.literal("Player Profile - " + playerData.getName()));
        this.parentScreen = parentScreen;
        this.playerData = playerData;
        this.highlightReviewId = highlightReviewId;
        if (playerData.getCategory() != null && playerData.getCategory().equalsIgnoreCase("Kuudra")) {
            this.activeStat = StatCategory.KUUDRA;
        } else {
            this.activeStat = StatCategory.DUNGEONS;
        }

        if (highlightReviewId != null && !highlightReviewId.isEmpty() && playerData.getReviews() != null) {
            for (int i = 0; i < playerData.getReviews().size(); i++) {
                if (highlightReviewId.equals(playerData.getReviews().get(i).getId())) {
                    this.reviewScrollOffset = Math.max(0, i * REVIEW_ITEM_HEIGHT - 30);
                    break;
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        if (this.hypixelStats == null) {
            // Fetch Hypixel API stats asynchronously
            HypixelApiFetcher.fetchStats(playerData.getName(), stats -> {
                this.hypixelStats = stats;
            });
        }

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int leftX = modalX + 14;
        int leftY = modalY + 36;
        int leftW = 185;

        // Stat Category Toggle Button (Dungeons <-> Kuudra)
        int switchBtnW = 20;
        int switchBtnH = 14;
        int switchBtnX = leftX + leftW - switchBtnW - 8;
        int switchBtnY = leftY + 114;

        this.addRenderableWidget(
            Button.builder(Component.literal("§e→"), button -> {
                this.activeStat = (this.activeStat == StatCategory.DUNGEONS) ? StatCategory.KUUDRA : StatCategory.DUNGEONS;
                this.rebuildWidgets();
            })
            .bounds(switchBtnX, switchBtnY, switchBtnW, switchBtnH)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal(this.activeStat == StatCategory.DUNGEONS ? "§eSwitch to §6Kuudra Info" : "§eSwitch to §bDungeon Info")
            ))
            .build()
        );

        // Category Detail Button (Floor Details or Kuudra Tiers)
        if (this.activeStat == StatCategory.KUUDRA) {
            this.addRenderableWidget(
                Button.builder(Component.literal("🌋 Kuudra Tiers →"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new KuudraInfoScreen(this, playerData.getName(), hypixelStats));
                    }
                })
                .bounds(leftX + 8, leftY + 160, leftW - 16, 18)
                .build()
            );
        } else {
            this.addRenderableWidget(
                Button.builder(Component.literal("📊 Floor Details →"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new DungeonFloorInfoScreen(this, playerData.getName(), hypixelStats));
                    }
                })
                .bounds(leftX + 8, leftY + 160, leftW - 16, 18)
                .build()
            );
        }

        // About Me Button
        this.addRenderableWidget(
            Button.builder(Component.literal("📝 About me"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new AboutMeScreen(this, playerData.getName()));
                }
            })
            .bounds(leftX + 8, leftY + 182, leftW - 16, 18)
            .build()
        );

        String selfName = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "";
        boolean isSelf = !selfName.isEmpty() && playerData.getName().equalsIgnoreCase(selfName);

        if (isSelf) {
            // Your Reviews Button (visible only in your own profile)
            this.addRenderableWidget(
                Button.builder(Component.literal("📝 Your Reviews"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MyReviewsScreen(this));
                    }
                })
                .bounds(leftX + 8, leftY + 204, leftW - 16, 20)
                .build()
            );
        } else {
            // Rate this Player Button
            this.addRenderableWidget(
                Button.builder(Component.literal("⭐ Rate this Player"), button -> {
                    if (this.minecraft != null) {
                        if (com.skyblockrating.storage.RatingStorage.isCurrentClientBlocked()) {
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal("§c[SkyReview] Your account is restricted from posting reviews. If you disagree with this block, please write to us on Discord!"));
                            }
                            return;
                        }
                        this.minecraft.setScreen(new RatePlayerScreen(playerData.getName(), playerData.getCategory()));
                    }
                })
                .bounds(leftX + 8, leftY + 204, leftW - 16, 20)
                .build()
            );

            // Blacklist Toggle Button
            boolean isBlocked = com.skyblockrating.util.BlacklistManager.isBlacklisted(playerData.getName());
            this.addRenderableWidget(
                Button.builder(Component.literal(isBlocked ? "§a✔ Unblacklist" : "§c⛔ Blacklist"), button -> {
                    String target = playerData.getName();
                    if (com.skyblockrating.util.BlacklistManager.isBlacklisted(target)) {
                        com.skyblockrating.util.BlacklistManager.removePlayer(target);
                        button.setMessage(Component.literal("§c⛔ Blacklist"));
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal("§a[SkyReview] Removed " + target + " from blacklist."));
                        }
                    } else {
                        com.skyblockrating.util.BlacklistManager.addPlayer(target, "Added from profile");
                        button.setMessage(Component.literal("§a✔ Unblacklist"));
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal("§c[SkyReview] Added " + target + " to blacklist."));
                        }
                    }
                })
                .bounds(leftX + 8, leftY + 228, leftW - 16, 18)
                .build()
            );
        }

        int rightX = modalX + 210;
        int rightY = modalY + 36;
        boolean isSafari = playerData.getCategory() != null && playerData.getCategory().equalsIgnoreCase("Safari");

        if (isSafari) {
            // Safari has no tags/subtags - Sort button takes full width
            this.addRenderableWidget(
                Button.builder(Component.literal(getSortButtonText()), button -> {
                    if (currentSort == SortMode.NEWEST) {
                        currentSort = SortMode.TOP_RATED;
                    } else if (currentSort == SortMode.TOP_RATED) {
                        currentSort = SortMode.LOWEST_RATED;
                    } else if (currentSort == SortMode.LOWEST_RATED) {
                        currentSort = SortMode.MOST_LIKED;
                    } else if (currentSort == SortMode.MOST_LIKED) {
                        currentSort = SortMode.OLDEST;
                    } else {
                        currentSort = SortMode.NEWEST;
                    }
                    reviewScrollOffset = 0;
                    button.setMessage(Component.literal(getSortButtonText()));
                })
                .bounds(rightX, rightY, modalWidth - 210 - 14, 18)
                .build()
            );
        } else {
            // Sort Mode Toggle Button (Newest -> Top Rated -> Lowest -> Most Liked -> Oldest)
            this.addRenderableWidget(
                Button.builder(Component.literal(getSortButtonText()), button -> {
                    if (currentSort == SortMode.NEWEST) {
                        currentSort = SortMode.TOP_RATED;
                    } else if (currentSort == SortMode.TOP_RATED) {
                        currentSort = SortMode.LOWEST_RATED;
                    } else if (currentSort == SortMode.LOWEST_RATED) {
                        currentSort = SortMode.MOST_LIKED;
                    } else if (currentSort == SortMode.MOST_LIKED) {
                        currentSort = SortMode.OLDEST;
                    } else {
                        currentSort = SortMode.NEWEST;
                    }
                    reviewScrollOffset = 0;
                    button.setMessage(Component.literal(getSortButtonText()));
                })
                .bounds(rightX, rightY, 150, 18)
                .build()
            );

            // Filter Button
            this.addRenderableWidget(
                Button.builder(Component.literal("Tag: " + selectedFilterTag + " ▼"), button -> {
                    if (this.minecraft == null) return;
                    if (playerData.getCategory().equalsIgnoreCase("Kuudra")) {
                        this.minecraft.setScreen(new SelectorPopupScreen(this, "Filter Kuudra Reviews", kuudraFilterOpts, opt -> {
                            this.selectedFilterTag = opt;
                            this.reviewScrollOffset = 0;
                            this.rebuildWidgets();
                        }));
                    } else if (playerData.getCategory().equalsIgnoreCase("Other")) {
                        this.minecraft.setScreen(new SelectorPopupScreen(this, "Filter Other Reviews", otherFilterOpts, opt -> {
                            this.selectedFilterTag = opt;
                            this.reviewScrollOffset = 0;
                            this.rebuildWidgets();
                        }));
                    } else {
                        this.minecraft.setScreen(new SelectorPopupScreen(this, "Filter Dungeons Floor", dungeonFilterOpts, opt -> {
                            this.selectedFilterTag = opt;
                            this.reviewScrollOffset = 0;
                            this.rebuildWidgets();
                        }));
                    }
                })
                .bounds(rightX + 155, rightY, modalWidth - 210 - 155 - 14, 18)
                .build()
            );
        }

        // Bottom Action Bar Buttons
        int buttonY = modalY + modalHeight - 26;

        // Back button
        this.addRenderableWidget(
            Button.builder(Component.literal("← Back"), button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parentScreen);
                }
            })
            .bounds(modalX + 14, buttonY, 75, 20)
            .build()
        );

        // Refresh API Button at the bottom
        this.addRenderableWidget(
            Button.builder(Component.literal("🔄 Refresh API"), button -> {
                HypixelApiFetcher.forceRefreshStats(playerData.getName(), stats -> {
                    this.hypixelStats = stats;
                });
            })
            .bounds(modalX + 94, buttonY, 105, 20)
            .build()
        );

        // Close button
        this.addRenderableWidget(
            Button.builder(Component.literal("✕ Close"), button -> this.onClose())
            .bounds(modalX + modalWidth - 84, buttonY, 70, 20)
            .build()
        );
    }

    private List<ReviewEntry> getFilteredReviews() {
        List<ReviewEntry> list = new ArrayList<>(playerData.getReviews());

        boolean isSafari = playerData.getCategory() != null && playerData.getCategory().equalsIgnoreCase("Safari");
        if (!isSafari && !selectedFilterTag.equalsIgnoreCase("All")) {
            list = list.stream()
                .filter(r -> r.getSubTag() != null && r.getSubTag().equalsIgnoreCase(selectedFilterTag))
                .collect(Collectors.toList());
        }

        if (currentSort == SortMode.TOP_RATED) {
            list.sort((a, b) -> {
                int cmp = Double.compare(b.getStars(), a.getStars());
                if (cmp != 0) return cmp;
                int likeCmp = Integer.compare(b.getLikes(), a.getLikes());
                if (likeCmp != 0) return likeCmp;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });
        } else if (currentSort == SortMode.LOWEST_RATED) {
            list.sort((a, b) -> {
                int cmp = Double.compare(a.getStars(), b.getStars());
                if (cmp != 0) return cmp;
                int likeCmp = Integer.compare(b.getLikes(), a.getLikes());
                if (likeCmp != 0) return likeCmp;
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            });
        } else if (currentSort == SortMode.MOST_LIKED) {
            list.sort(Comparator.comparingInt(ReviewEntry::getLikes).reversed().thenComparing(Comparator.comparingLong(ReviewEntry::getTimestamp).reversed()));
        } else if (currentSort == SortMode.OLDEST) {
            list.sort(Comparator.comparingLong(ReviewEntry::getTimestamp));
        } else {
            list.sort(Comparator.comparingLong(ReviewEntry::getTimestamp).reversed());
        }

        return list;
    }

    private void clampReviewScroll() {
        List<ReviewEntry> reviews = getFilteredReviews();
        int visibleH = 330 - 96;
        int maxScroll = Math.max(0, reviews.size() * REVIEW_ITEM_HEIGHT - visibleH);
        if (reviewScrollOffset > maxScroll) reviewScrollOffset = maxScroll;
        if (reviewScrollOffset < 0) reviewScrollOffset = 0;
    }

    private void renderReviewComment(GuiGraphicsExtractor graphics, String comment, int x, int startY, int maxWidth, int maxLines) {
        if (comment == null || comment.trim().isEmpty()) return;
        String fullText = "§7\"" + comment.trim() + "\"";

        List<net.minecraft.util.FormattedCharSequence> split = this.font.split(Component.literal(fullText), maxWidth);
        if (split.isEmpty()) return;

        int linesToRender = Math.min(split.size(), maxLines);
        int curY = startY;

        for (int k = 0; k < linesToRender; k++) {
            if (k == maxLines - 1 && split.size() > maxLines) {
                // Last visible line when comment exceeds maxLines: append "..."
                StringBuilder sb = new StringBuilder();
                split.get(k).accept((idx, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                String lineStr = sb.toString();
                while (!lineStr.isEmpty() && this.font.width(lineStr + "...") > maxWidth) {
                    lineStr = lineStr.substring(0, lineStr.length() - 1);
                }
                graphics.text(this.font, Component.literal(lineStr + "..."), x, curY, GuiHelper.TEXT_SECONDARY, false);
            } else {
                graphics.text(this.font, split.get(k), x, curY, GuiHelper.TEXT_SECONDARY, false);
            }
            curY += 10;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int rightX = modalX + 210;
        int rightY = modalY + 58;
        int rightWidth = modalWidth - 224;
        int visibleH = modalHeight - 96;

        if (mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= rightY && mouseY <= rightY + visibleH) {
            reviewScrollOffset -= verticalAmount * 24;
            clampReviewScroll();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, GuiHelper.BG_OVERLAY);

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        // Outer Container
        GuiHelper.renderPanel(graphics, modalX, modalY, modalWidth, modalHeight, 30, GuiHelper.HEADER_ACCENT);

        graphics.text(this.font, Component.literal("§6§lSky§e§lReview §8| §fPlayer Profile"), modalX + 14, modalY + 9, GuiHelper.TEXT_TITLE, true);
        if (OwnerLock.isOwner() && AdminManager.isDeleteReviewsEnabled()) {
            graphics.text(this.font, Component.literal("§c[Admin Active]"), modalX + modalWidth - 95, modalY + 9, GuiHelper.COLOR_RED, true);
        }

        // ==========================================
        // LEFT COLUMN: Player Card & Hypixel Stats
        // ==========================================
        int leftX = modalX + 14;
        int leftY = modalY + 36;
        int leftWidth = 185;
        int leftHeight = modalHeight - 70;

        GuiHelper.renderCard(graphics, leftX, leftY, leftWidth, leftHeight, false);

        // Player Name & Prefix
        GuiHelper.renderPlayerWithPrefix(graphics, this.font, playerData.getName(), RatingStorage.getUserPrefix(playerData.getName()), leftX + 10, leftY + 8, true);

        // Category & Rank badge
        String rankBadge = "§eRank #" + playerData.getRank() + " §8• §7" + playerData.getCategory();
        graphics.text(this.font, Component.literal(rankBadge), leftX + 10, leftY + 22, GuiHelper.COLOR_YELLOW, false);

        // Stars & Reviews Count
        String starsFormatted = GuiHelper.getFormattedStars(playerData.getRating());
        graphics.text(this.font, Component.literal(starsFormatted), leftX + 10, leftY + 38, GuiHelper.COLOR_GOLD, true);
        graphics.text(this.font, Component.literal("§a" + playerData.getReviewsCount() + " player reviews"), leftX + 10, leftY + 52, GuiHelper.COLOR_GREEN, false);

        // Separator
        graphics.fill(leftX + 8, leftY + 66, leftX + leftWidth - 8, leftY + 67, GuiHelper.PANEL_BORDER);

        // Hypixel SkyBlock Stats Card
        if (hypixelStats == null || !hypixelStats.loaded) {
            String status = hypixelStats != null ? hypixelStats.status : "Loading Hypixel stats...";
            graphics.text(this.font, Component.literal("§7" + status), leftX + 10, leftY + 76, GuiHelper.TEXT_MUTED, false);
        } else {
            int statY = leftY + 74;

            graphics.text(this.font, Component.literal("§e§lSkyBlock Stats:"), leftX + 10, statY, GuiHelper.COLOR_GOLD, false);
            graphics.text(this.font, Component.literal("§7Combat: §fLevel " + hypixelStats.combatLevel), leftX + 12, statY + 14, GuiHelper.TEXT_PRIMARY, false);
            graphics.text(this.font, Component.literal("§7Accessory: §d" + String.format("%,d MP", hypixelStats.accessoryPower)), leftX + 12, statY + 26, 0xFFE879F9, false);

            statY += 42;
            graphics.fill(leftX + 8, statY - 4, leftX + leftWidth - 8, statY - 3, GuiHelper.PANEL_BORDER);

            if (this.activeStat == StatCategory.DUNGEONS) {
                graphics.text(this.font, Component.literal("§b§lDungeon Info:"), leftX + 10, statY, GuiHelper.COLOR_AQUA, false);
                graphics.text(this.font, Component.literal("§7Catacombs: §fLevel " + hypixelStats.catacombsLevel), leftX + 12, statY + 14, GuiHelper.TEXT_PRIMARY, false);
                graphics.text(this.font, Component.literal("§7Secrets/Run: §a" + String.format("%.1f", hypixelStats.secretsPerRun)), leftX + 12, statY + 26, GuiHelper.COLOR_GREEN, false);
            } else {
                graphics.text(this.font, Component.literal("§6§lKuudra Info:"), leftX + 10, statY, GuiHelper.COLOR_ORANGE, false);
                graphics.text(this.font, Component.literal("§7Total Runs: §f" + String.format("%,d", hypixelStats.totalKuudraRuns)), leftX + 12, statY + 14, GuiHelper.TEXT_PRIMARY, false);
                int infernalRuns = (hypixelStats.kuudraTierRuns != null) ? hypixelStats.kuudraTierRuns.getOrDefault("Infernal", 0) : 0;
                graphics.text(this.font, Component.literal("§7Infernal: §c" + String.format("%,d runs", infernalRuns)), leftX + 12, statY + 26, 0xFFFF5555, false);
            }
        }

        // ==========================================
        // RIGHT COLUMN: Reviews Cards List (Scrollable)
        // ==========================================
        int rightX = modalX + 210;
        int rightY = modalY + 58;
        int rightWidth = modalWidth - 224;
        int visibleH = modalHeight - 96;

        List<ReviewEntry> reviews = getFilteredReviews();

        if (reviews.isEmpty()) {
            GuiHelper.renderCard(graphics, rightX, rightY, rightWidth, leftHeight - 22, false);
            String emptyMsg = "No reviews found for this filter.";
            int emX = rightX + (rightWidth - this.font.width(emptyMsg)) / 2;
            graphics.text(this.font, Component.literal("§7" + emptyMsg), emX, rightY + 80, GuiHelper.TEXT_MUTED, false);
        } else {
            String currentUser = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
            int cardH = 72;

            graphics.enableScissor(rightX, rightY, rightX + rightWidth, rightY + visibleH);

            int startIndex = Math.max(0, (int) (reviewScrollOffset / REVIEW_ITEM_HEIGHT));
            int endIndex = Math.min(reviews.size(), startIndex + (visibleH / REVIEW_ITEM_HEIGHT) + 2);

            for (int i = startIndex; i < endIndex; i++) {
                ReviewEntry r = reviews.get(i);
                int cardY = rightY + (i * REVIEW_ITEM_HEIGHT) - (int) reviewScrollOffset;

                if (cardY + cardH <= rightY || cardY >= rightY + visibleH) {
                    continue;
                }

                boolean isHighlighted = (highlightReviewId != null && highlightReviewId.equals(r.getId()));
                boolean cardHovered = mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= cardY && mouseY <= cardY + cardH;
                GuiHelper.renderCard(graphics, rightX, cardY, rightWidth, cardH, cardHovered);
                if (isHighlighted) {
                    graphics.fill(rightX - 1, cardY - 1, rightX + rightWidth + 1, cardY, 0xFFF59E0B);
                    graphics.fill(rightX - 1, cardY + cardH, rightX + rightWidth + 1, cardY + cardH + 1, 0xFFF59E0B);
                    graphics.fill(rightX - 1, cardY, rightX, cardY + cardH, 0xFFF59E0B);
                    graphics.fill(rightX + rightWidth, cardY, rightX + rightWidth + 1, cardY + cardH, 0xFFF59E0B);
                }

                // Author & Tag
                String authorText = isHighlighted ? ("§6§l@" + r.getAuthor() + " §e[Your Review]") : ("§f§l@" + r.getAuthor());
                graphics.text(this.font, Component.literal(authorText), rightX + 10, cardY + 6, GuiHelper.TEXT_PRIMARY, false);

                boolean reviewIsSafari = (r.getCategory() != null && r.getCategory().equalsIgnoreCase("Safari"))
                        || (playerData.getCategory() != null && playerData.getCategory().equalsIgnoreCase("Safari"));
                if (!reviewIsSafari) {
                    String sub = (r.getSubTag() != null && !r.getSubTag().isEmpty()) ? " " + r.getSubTag() : "";
                    String tagInfo = "§8[" + r.getCategory() + sub + "]";
                    graphics.text(this.font, Component.literal(tagInfo), rightX + 10 + this.font.width(authorText) + 6, cardY + 6, GuiHelper.TEXT_MUTED, false);
                }

                // Rating Stars
                String stars = GuiHelper.getFormattedStars(r.getStars());
                int starsW = this.font.width(stars);
                graphics.text(this.font, Component.literal(stars), rightX + rightWidth - starsW - 10, cardY + 6, GuiHelper.COLOR_GOLD, false);

                // Review Comment (Word wrapped with max 3 lines)
                renderReviewComment(graphics, r.getComment(), rightX + 10, cardY + 18, rightWidth - 20, 3);

                // Buttons on card bottom
                // 1. Like Button
                int likeX = rightX + 10;
                int likeY = cardY + 50;
                boolean isLiked = r.isLikedBy(currentUser);
                boolean hLike = mouseX >= likeX && mouseX <= likeX + 56 && mouseY >= likeY && mouseY <= likeY + 17;
                int likeBg = hLike ? 0xFF0284C7 : (isLiked ? 0xFF0369A1 : 0xFF1E293B);
                graphics.fill(likeX - 1, likeY - 1, likeX + 57, likeY + 18, hLike ? 0xFF38BDF8 : GuiHelper.PANEL_BORDER);
                graphics.fill(likeX, likeY, likeX + 56, likeY + 17, likeBg);
                graphics.text(this.font, Component.literal("👍 " + r.getLikes()), likeX + 8, likeY + 4, 0xFFFFFFFF, false);

                // 2. Report Button
                int repX = rightX + 72;
                int repY = cardY + 50;
                boolean hRep = mouseX >= repX && mouseX <= repX + 58 && mouseY >= repY && mouseY <= repY + 17;
                int repBg = hRep ? 0xFFC2410C : 0xFF1E293B;
                graphics.fill(repX - 1, repY - 1, repX + 59, repY + 18, hRep ? 0xFFF97316 : GuiHelper.PANEL_BORDER);
                graphics.fill(repX, repY, repX + 58, repY + 17, repBg);
                graphics.text(this.font, Component.literal("🚩 Report"), repX + 6, repY + 4, 0xFFFFFFFF, false);

                // 3. Delete Button (Admin or Review Author)
                String currentUuid = (this.minecraft != null && this.minecraft.getUser() != null && this.minecraft.getUser().getProfileId() != null)
                        ? this.minecraft.getUser().getProfileId().toString().replace("-", "") : "";
                boolean canDelete = (OwnerLock.isOwner() && AdminManager.isDeleteReviewsEnabled()) || r.isAuthor(currentUser, currentUuid);

                if (canDelete) {
                    int delX = rightX + rightWidth - 62;
                    int delY = cardY + 50;
                    boolean hDel = mouseX >= delX && mouseX <= delX + 54 && mouseY >= delY && mouseY <= delY + 17;
                    int delBg = hDel ? 0xFFDC2626 : 0xFF7F1D1D;
                    graphics.fill(delX - 1, delY - 1, delX + 55, delY + 18, hDel ? 0xFFEF4444 : GuiHelper.PANEL_BORDER);
                    graphics.fill(delX, delY, delX + 54, delY + 17, delBg);
                    graphics.text(this.font, Component.literal("🗑 Delete"), delX + 6, delY + 4, 0xFFFFFFFF, false);
                }

                // 4. Timestamp in bottom-right corner
                String timeStr = formatTime(r.getTimestamp());
                if (!timeStr.isEmpty()) {
                    int timeW = this.font.width(timeStr);
                    int timeX = canDelete ? (rightX + rightWidth - 62 - timeW - 8) : (rightX + rightWidth - timeW - 10);
                    graphics.text(this.font, Component.literal("§8" + timeStr), timeX, cardY + 54, GuiHelper.TEXT_MUTED, false);
                }
            }

            graphics.disableScissor();

            // Scrollbar indicator
            int maxScroll = Math.max(0, reviews.size() * REVIEW_ITEM_HEIGHT - visibleH);
            if (maxScroll > 0) {
                int scrollbarX = rightX + rightWidth + 2;
                int scrollbarY = rightY;
                int thumbH = Math.max(16, (int) ((float) visibleH / (reviews.size() * REVIEW_ITEM_HEIGHT) * visibleH));
                int thumbY = scrollbarY + (int) ((reviewScrollOffset / maxScroll) * (visibleH - thumbH));

                graphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + visibleH, 0x50000000);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xFFAAAAAA);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int modalWidth = 560;
        int modalHeight = 330;
        int modalX = (this.width - modalWidth) / 2;
        int modalY = (this.height - modalHeight) / 2;

        int rightX = modalX + 210;
        int rightY = modalY + 58;
        int rightWidth = modalWidth - 224;
        int visibleH = modalHeight - 96;

        List<ReviewEntry> reviews = getFilteredReviews();
        int cardH = 72;
        String currentUser = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";

        if (mouseX < rightX || mouseX > rightX + rightWidth || mouseY < rightY || mouseY > rightY + visibleH) {
            return super.mouseClicked(event, doubleClick);
        }

        int startIndex = Math.max(0, (int) (reviewScrollOffset / REVIEW_ITEM_HEIGHT));
        int endIndex = Math.min(reviews.size(), startIndex + (visibleH / REVIEW_ITEM_HEIGHT) + 2);

        for (int i = startIndex; i < endIndex; i++) {
            ReviewEntry r = reviews.get(i);
            int cardY = rightY + (i * REVIEW_ITEM_HEIGHT) - (int) reviewScrollOffset;

            if (cardY + cardH <= rightY || cardY >= rightY + visibleH) {
                continue;
            }

            // Like click
            int likeX = rightX + 10;
            int likeY = cardY + 50;
            if (mouseX >= likeX && mouseX <= likeX + 56 && mouseY >= likeY && mouseY <= likeY + 17) {
                if (!com.skyblockrating.auth.SessionAuthManager.isLicensedUser()) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(com.skyblockrating.auth.SessionAuthManager.AUTH_REQUIRED_MSG));
                    }
                    return true;
                }
                r.toggleLike(currentUser);
                RatingStorage.scheduleDebouncedLike(r.getId(), currentUser, r.isLikedBy(currentUser));
                return true;
            }

            // Report click
            int repX = rightX + 72;
            int repY = cardY + 50;
            if (mouseX >= repX && mouseX <= repX + 58 && mouseY >= repY && mouseY <= repY + 17) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ReportDialogScreen(this, playerData.getName(), r));
                }
                return true;
            }

            // Delete click (Admin or Review Author)
            String currentUuid = (this.minecraft != null && this.minecraft.getUser() != null && this.minecraft.getUser().getProfileId() != null)
                    ? this.minecraft.getUser().getProfileId().toString().replace("-", "") : "";
            boolean canDelete = (OwnerLock.isOwner() && AdminManager.isDeleteReviewsEnabled()) || r.isAuthor(currentUser, currentUuid);

            if (canDelete) {
                int delX = rightX + rightWidth - 62;
                int delY = cardY + 50;
                if (mouseX >= delX && mouseX <= delX + 54 && mouseY >= delY && mouseY <= delY + 17) {
                    String reviewId = r.getId();
                    playerData.removeReview(reviewId);
                    LeaderboardScreen.recalculateAllRanks();
                    RatingStorage.deleteReviewAsync(reviewId);
                    clampReviewScroll();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private static String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) diff = 0;
        if (diff < 60_000L) {
            return "just now";
        } else if (diff < 3600_000L) {
            return (diff / 60_000L) + "m ago";
        } else if (diff < 86400_000L) {
            return (diff / 3600_000L) + "h ago";
        } else if (diff < 7L * 86400_000L) {
            return (diff / 86400_000L) + "d ago";
        } else {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy");
            return sdf.format(new java.util.Date(timestamp));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
