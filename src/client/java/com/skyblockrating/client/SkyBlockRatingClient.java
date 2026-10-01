package com.skyblockrating.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyblockrating.config.AdminAuthManager;
import com.skyblockrating.config.OwnerLock;
import com.skyblockrating.gui.AdminControlScreen;
import com.skyblockrating.gui.AdminReportsScreen;
import com.skyblockrating.gui.BlacklistScreen;
import com.skyblockrating.gui.LeaderboardScreen;
import com.skyblockrating.gui.ModDisabledScreen;
import com.skyblockrating.gui.MyReviewsScreen;
import com.skyblockrating.gui.PlayerRatingEntry;
import com.skyblockrating.gui.ProfileScreen;
import com.skyblockrating.gui.RatePlayerScreen;
import com.skyblockrating.gui.SettingsScreen;
import com.skyblockrating.gui.TeammateHistoryScreen;
import com.skyblockrating.storage.RatingStorage;
import com.skyblockrating.util.BlacklistManager;
import com.skyblockrating.util.PartyFinderChatHelper;
import com.skyblockrating.util.PartyFinderTooltipHelper;
import com.skyblockrating.util.PlayerSkinEntityCache;
import com.skyblockrating.util.TeammateHistoryManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyBlockRatingClient implements ClientModInitializer {
    private static int tickCounter = 0;
    private static long lastReloadTimestamp = 0L;

    private static KeyMapping.Category createCategory(String categoryName) {
        try {
            for (java.lang.reflect.Method m : KeyMapping.Category.class.getDeclaredMethods()) {
                if (m.getName().equals("register") || m.getName().equals("create")) {
                    m.setAccessible(true);
                    Class<?>[] pTypes = m.getParameterTypes();
                    if (pTypes.length == 1) {
                        if (pTypes[0] == String.class) {
                            return (KeyMapping.Category) m.invoke(null, categoryName);
                        } else {
                            try {
                                Object loc = pTypes[0].getMethod("parse", String.class).invoke(null, "skyreview:" + categoryName);
                                return (KeyMapping.Category) m.invoke(null, loc);
                            } catch (Throwable t2) {
                                try {
                                    Object loc = pTypes[0].getMethod("of", String.class, String.class).invoke(null, "skyreview", categoryName);
                                    return (KeyMapping.Category) m.invoke(null, loc);
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return KeyMapping.Category.GAMEPLAY;
    }

    // Standard Minecraft KeyMapping registered under "SkyReview" category in Minecraft Controls
    public static final KeyMapping openMenuKeyBinding = new KeyMapping(
        "key.skyreview.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        createCategory("skyreview")
    );

    @Override
    public void onInitializeClient() {
        System.out.println("[SkyReviewClient] Initializing client key binding & commands...");

        // Register KeyMapping into Minecraft Controls -> Key Binds menu
        KeyMappingHelper.registerKeyMapping(openMenuKeyBinding);

        // Register Tooltip Hook for Party Finder ratings display
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            try {
                PartyFinderTooltipHelper.processTooltipLines(lines);
            } catch (Throwable ignored) {}
        });

        // Intercept & hide /p list chat response lines from player's chat box, format Party Finder messages, and detect triggers
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            try {
                if (message != null && !overlay) {
                    Component formatted = PartyFinderChatHelper.formatPartyFinderJoinMessage(message);
                    if (formatted != null && formatted != message) {
                        TeammateHistoryManager.handleIncomingChatMessage(message);
                        Minecraft client = Minecraft.getInstance();
                        if (client != null && client.player != null) {
                            client.player.sendSystemMessage(formatted);
                            return false; // Suppress unformatted original!
                        }
                    }

                    boolean showMessage = TeammateHistoryManager.handleIncomingChatMessage(message);
                    if (!showMessage) {
                        return false; // HIDE MESSAGE FROM CHAT HUD!
                    }
                }
            } catch (Throwable ignored) {}
            return true;
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            try {
                if (message != null) {
                    Component formatted = PartyFinderChatHelper.formatPartyFinderJoinMessage(message);
                    if (formatted != null && formatted != message) {
                        TeammateHistoryManager.handleIncomingChatMessage(message);
                        Minecraft client = Minecraft.getInstance();
                        if (client != null && client.player != null) {
                            client.player.sendSystemMessage(formatted);
                            return false; // Suppress unformatted original!
                        }
                    }

                    TeammateHistoryManager.handleIncomingChatMessage(message);
                }
            } catch (Throwable ignored) {}
            return true;
        });

        // Pre-fetch online rating data in background so /sr opens with zero delay
        RatingStorage.fetchOnlineDataAsync(players -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    LeaderboardScreen.getAllPlayers().clear();
                    if (players != null) LeaderboardScreen.getAllPlayers().addAll(players);
                    LeaderboardScreen.recalculateAllRanks();
                });
            }
        });

        // Register native Brigadier Client Commands with tab completion and valid coloring (/sr, /skyreview, /rate, /ar)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /sr and /skyreview with subcommands: profile, rate, me, history, settings, help
            registerSkyReviewCommandTree(dispatcher, "sr");
            registerSkyReviewCommandTree(dispatcher, "skyreview");

            // /rate and /rate <player> (requires nickname)
            dispatcher.register(ClientCommands.literal("rate")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§c[SkyReview] Usage: /rate <player>"));
                    }
                    return 1;
                })
                .then(ClientCommands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String player = StringArgumentType.getString(context, "player");
                        executeRatePlayer(player);
                        return 1;
                    })
                )
            );

            // /refreshskin shortcut command
            dispatcher.register(ClientCommands.literal("refreshskin")
                .executes(context -> {
                    PlayerSkinEntityCache.clearCache();
                    Minecraft client = Minecraft.getInstance();
                    if (client != null && client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§a[SkyReview] Skin cache cleared! Reloading player skins..."));
                    }
                    return 1;
                })
            );

            // /ar (Admin Control Panel) - UNTOUCHED
            dispatcher.register(ClientCommands.literal("ar")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (OwnerLock.isOwner()) {
                            client.setScreen(new AdminControlScreen(null));
                        } else if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§c[SkyReview] Access Denied: Admin privileges required."));
                        }
                    });
                    return 1;
                })
            );
        });

        // Key Listeners & Teammate Scanner
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.getWindow() == null) return;

            tickCounter++;
            if (tickCounter % 20 == 0) {
                TeammateHistoryManager.scanTabAndScoreboard();
            }

            // Auto-kick from any normal mod menu if mod is disabled (except admin panel)
            if (RatingStorage.isModDisabled()) {
                if (client.screen != null 
                    && client.screen.getClass().getName().startsWith("com.skyblockrating.gui.") 
                    && !(client.screen instanceof ModDisabledScreen)
                    && !(client.screen instanceof AdminControlScreen)
                    && !client.screen.getClass().getName().contains("Admin")) {
                    client.setScreen(new ModDisabledScreen(null));
                }
            }

            // Periodic background sync: every 5 seconds (100 ticks) when inside a menu, or every 30 seconds (600 ticks) otherwise
            boolean insideMenu = client.screen != null && client.screen.getClass().getName().startsWith("com.skyblockrating.gui.");
            if ((insideMenu && tickCounter % 100 == 0) || (tickCounter % 600 == 0)) {
                RatingStorage.fetchOnlineDataAsync(null);
            }

            // Open Leaderboard using standard Minecraft KeyBinding
            while (openMenuKeyBinding.consumeClick()) {
                if (client.screen == null) {
                    if (RatingStorage.isModDisabled()) {
                        client.setScreen(new ModDisabledScreen(null));
                    } else {
                        client.setScreen(new LeaderboardScreen());
                    }
                }
            }
        });
    }

    private static void registerSkyReviewCommandTree(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher, String rootName) {
        dispatcher.register(ClientCommands.literal(rootName)
            .executes(context -> {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (RatingStorage.isModDisabled()) {
                        client.setScreen(new ModDisabledScreen(null));
                    } else {
                        client.setScreen(new LeaderboardScreen());
                    }
                });
                return 1;
            })
            // /<root> profile <player>
            .then(ClientCommands.literal("profile")
                .then(ClientCommands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String player = StringArgumentType.getString(context, "player");
                        openPlayerProfile(player);
                        return 1;
                    })
                )
            )
            // Shortcut: /<root> <player> (e.g. /sr LyNtlk)
            .then(ClientCommands.argument("player", StringArgumentType.word())
                .executes(context -> {
                    String player = StringArgumentType.getString(context, "player");
                    openPlayerProfile(player);
                    return 1;
                })
            )
            // /<root> rate and /<root> rate <player> (requires nickname)
            .then(ClientCommands.literal("rate")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§c[SkyReview] " + RatingStorage.getDisabledReason()));
                        }
                        client.execute(() -> client.setScreen(new ModDisabledScreen(null)));
                        return 1;
                    }
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§c[SkyReview] Usage: /" + rootName + " rate <player>"));
                    }
                    return 1;
                })
                .then(ClientCommands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String player = StringArgumentType.getString(context, "player");
                        executeRatePlayer(player);
                        return 1;
                    })
                )
            )
            // /<root> me
            .then(ClientCommands.literal("me")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        String selfName = (client.getUser() != null) ? client.getUser().getName() : "Player";
                        PlayerRatingEntry selfEntry = RatingStorage.getPlayerEntry(selfName);
                        if (selfEntry == null) {
                            selfEntry = new PlayerRatingEntry(0, selfName, "General", 0.0, 0, null);
                        }
                        client.setScreen(new ProfileScreen(null, selfEntry));
                    });
                    return 1;
                })
            )
            // /<root> reviews and /<root> myreviews
            .then(ClientCommands.literal("reviews")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        client.setScreen(new MyReviewsScreen(null));
                    });
                    return 1;
                })
            )
            .then(ClientCommands.literal("myreviews")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        client.setScreen(new MyReviewsScreen(null));
                    });
                    return 1;
                })
            )
            // /<root> history [dungeons|kuudra|safari]
            .then(ClientCommands.literal("history")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        client.setScreen(new TeammateHistoryScreen(null, "dungeons"));
                    });
                    return 1;
                })
                .then(ClientCommands.literal("dungeons")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                                client.setScreen(new ModDisabledScreen(null));
                                return;
                            }
                            client.setScreen(new TeammateHistoryScreen(null, "dungeons"));
                        });
                        return 1;
                    })
                )
                .then(ClientCommands.literal("dungeon")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                                client.setScreen(new ModDisabledScreen(null));
                                return;
                            }
                            client.setScreen(new TeammateHistoryScreen(null, "dungeons"));
                        });
                        return 1;
                    })
                )
                .then(ClientCommands.literal("kuudra")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                                client.setScreen(new ModDisabledScreen(null));
                                return;
                            }
                            client.setScreen(new TeammateHistoryScreen(null, "kuudra"));
                        });
                        return 1;
                    })
                )
                .then(ClientCommands.literal("safari")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                                client.setScreen(new ModDisabledScreen(null));
                                return;
                            }
                            client.setScreen(new TeammateHistoryScreen(null, "safari"));
                        });
                        return 1;
                    })
                )
            )
            // /<root> settings
            .then(ClientCommands.literal("settings")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        client.setScreen(new SettingsScreen(null));
                    });
                    return 1;
                })
            )
            // /<root> blacklist [add|remove|clear|list]
            .then(ClientCommands.literal("blacklist")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                            client.setScreen(new ModDisabledScreen(null));
                            return;
                        }
                        client.setScreen(new BlacklistScreen(null));
                    });
                    return 1;
                })
                .then(ClientCommands.literal("add")
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            BlacklistManager.addPlayer(player, "Added via command");
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) {
                                client.player.sendSystemMessage(Component.literal("§a[SkyReview] Added §e" + player + " §ato blacklist."));
                            }
                            return 1;
                        })
                        .then(ClientCommands.argument("reason", StringArgumentType.greedyString())
                            .executes(context -> {
                                String player = StringArgumentType.getString(context, "player");
                                String reason = StringArgumentType.getString(context, "reason");
                                BlacklistManager.addPlayer(player, reason);
                                Minecraft client = Minecraft.getInstance();
                                if (client.player != null) {
                                    client.player.sendSystemMessage(Component.literal("§a[SkyReview] Added §e" + player + " §ato blacklist. §7(Reason: " + reason + ")"));
                                }
                                return 1;
                            })
                        )
                    )
                )
                .then(ClientCommands.literal("remove")
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            String player = StringArgumentType.getString(context, "player");
                            boolean removed = BlacklistManager.removePlayer(player);
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) {
                                if (removed) {
                                    client.player.sendSystemMessage(Component.literal("§a[SkyReview] Removed §e" + player + " §afrom blacklist."));
                                } else {
                                    client.player.sendSystemMessage(Component.literal("§c[SkyReview] Player §e" + player + " §cnot found in blacklist."));
                                }
                            }
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("clear")
                    .executes(context -> {
                        BlacklistManager.clearBlacklist();
                        Minecraft client = Minecraft.getInstance();
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§c[SkyReview] Blacklist cleared."));
                        }
                        return 1;
                    })
                )
                .then(ClientCommands.literal("list")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> client.setScreen(new BlacklistScreen(null)));
                        return 1;
                    })
                )
            )
            // /<root> reload
            .then(ClientCommands.literal("reload")
                .executes(context -> {
                    Minecraft client = Minecraft.getInstance();
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastReloadTimestamp;
                    if (elapsed < 5000L && !OwnerLock.isOwner()) {
                        long remSec = Math.max(1L, (5000L - elapsed + 999L) / 1000L);
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§c[SkyReview] Please wait §e" + remSec + "s §cbefore reloading again."));
                        }
                        return 1;
                    }
                    lastReloadTimestamp = now;

                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§e[SkyReview] Reloading mod data and status from server..."));
                    }
                    com.skyblockrating.auth.SessionAuthManager.invalidateCache();
                    com.skyblockrating.auth.SessionAuthManager.warmUpAsync();
                    RatingStorage.reloadOnlineDataAsync(success -> {
                        if (client.player != null) {
                            if (RatingStorage.isModDisabled()) {
                                client.player.sendSystemMessage(Component.literal("§c[SkyReview] Reload complete. Server status: DISABLED (Maintenance Mode)."));
                                if (!OwnerLock.isOwner() && client.screen != null && client.screen.getClass().getName().startsWith("com.skyblockrating.gui.")) {
                                    client.setScreen(new ModDisabledScreen(null));
                                }
                            } else {
                                client.player.sendSystemMessage(Component.literal("§a[SkyReview] Reload complete! All data, ratings & server status are up-to-date."));
                            }
                        }
                    });
                    return 1;
                })
            )
            // /<root> help
            .then(ClientCommands.literal("help")
                .executes(context -> {
                    sendHelpMessage();
                    return 1;
                })
            )
        );
    }

    private static void openPlayerProfile(String player) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                client.setScreen(new ModDisabledScreen(null));
                return;
            }
            PlayerRatingEntry entry = RatingStorage.getPlayerEntry(player);
            if (entry == null) {
                entry = new PlayerRatingEntry(0, player, "General", 0.0, 0, null);
            }
            client.setScreen(new ProfileScreen(null, entry));
        });
    }

    private static void executeRatePlayer(String player) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("§c[SkyReview] " + RatingStorage.getDisabledReason() + " §7(Run §e/sr reload §7to re-check)"));
                }
                client.setScreen(new ModDisabledScreen(null));
                return;
            }
            String selfName = (client.getUser() != null) ? client.getUser().getName() : "";
            if (RatingStorage.isCurrentClientBlocked()) {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("§c[SkyReview] Your account is restricted from posting reviews. If you disagree with this block, please write to us on Discord!"));
                }
            } else if (!selfName.isEmpty() && player.equalsIgnoreCase(selfName)) {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("§c[SkyReview] r u srsly?"));
                }
            } else {
                client.setScreen(new RatePlayerScreen(player));
            }
        });
    }

    private static void sendHelpMessage() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (RatingStorage.isModDisabled() && !OwnerLock.isOwner()) {
            client.player.sendSystemMessage(Component.literal("§c[SkyReview] " + RatingStorage.getDisabledReason() + " §7(Run §e/sr reload §7to re-check)"));
            return;
        }
        client.player.sendSystemMessage(Component.literal("§6§m-----------------------------------------------------"));
        client.player.sendSystemMessage(Component.literal("§6§lSky§e§lReview §8| §fMod Commands:"));
        client.player.sendSystemMessage(Component.literal(" §e/sr §8- §7Open main leaderboard menu"));
        client.player.sendSystemMessage(Component.literal(" §e/sr <player> §8- §7Quick view player profile & reviews"));
        client.player.sendSystemMessage(Component.literal(" §e/sr profile <player> §8- §7View player profile & reviews"));
        client.player.sendSystemMessage(Component.literal(" §e/sr rate <player> §8- §7Rate and write a review for a player"));
        client.player.sendSystemMessage(Component.literal(" §e/sr me §8- §7View your own profile & 'About Me'"));
        client.player.sendSystemMessage(Component.literal(" §e/sr reviews §8- §7View and manage your own reviews"));
        client.player.sendSystemMessage(Component.literal(" §e/sr history [dungeon | kuudra | safari] §8- §7Teammates from last 3 runs"));
        client.player.sendSystemMessage(Component.literal(" §e/sr blacklist §8- §7Manage blacklist and auto-kick"));
        client.player.sendSystemMessage(Component.literal(" §e/sr reload §8- §7Reload data & server status"));
        client.player.sendSystemMessage(Component.literal(" §e/sr settings §8- §7Open mod settings"));
        client.player.sendSystemMessage(Component.literal(" §e/sr help §8- §7Show this command list"));
        client.player.sendSystemMessage(Component.literal("§6§m-----------------------------------------------------"));
    }
}
