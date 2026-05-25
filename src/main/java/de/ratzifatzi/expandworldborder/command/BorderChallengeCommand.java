package de.ratzifatzi.expandworldborder.command;

import de.ratzifatzi.expandworldborder.config.PluginConfig;
import de.ratzifatzi.expandworldborder.gui.GUIManager;
import de.ratzifatzi.expandworldborder.manager.AdvancementManager;
import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import de.ratzifatzi.expandworldborder.manager.PlayerStatsManager;
import de.ratzifatzi.expandworldborder.util.DurationFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BorderChallengeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
            "start",
            "pause",
            "resume",
            "reset",
            "stop",
            "status",
            "timer",
            "advancements",
            "missing",
            "border",
            "gui",
            "forcejoin",
            "debug",
            "reloadadvancements"
    );

    private final PluginConfig config;
    private final ChallengeManager challengeManager;
    private final GUIManager guiManager;

    public BorderChallengeCommand(PluginConfig config, ChallengeManager challengeManager, GUIManager guiManager) {
        this.config = config;
        this.challengeManager = challengeManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(config.prefixed("&eNutze /borderchallenge <start|pause|resume|reset|status|timer|advancements|missing|border|gui|forcejoin|debug>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (requireAdmin(sender)) {
                    challengeManager.startChallenge(sender);
                }
            }
            case "pause" -> {
                if (requireAdmin(sender)) {
                    challengeManager.pauseChallenge(sender);
                }
            }
            case "resume" -> {
                if (requireAdmin(sender)) {
                    challengeManager.resumeChallenge(sender);
                }
            }
            case "reset" -> {
                if (requireAdmin(sender)) {
                    challengeManager.manualReset(sender);
                }
            }
            case "stop" -> {
                if (requireAdmin(sender)) {
                    challengeManager.stopChallenge(sender);
                }
            }
            case "forcejoin" -> {
                if (requireAdmin(sender)) {
                    challengeManager.forceJoinLobbyPlayers(sender);
                }
            }
            case "reloadadvancements" -> {
                if (requireAdmin(sender)) {
                    challengeManager.reloadAdvancementPool(sender);
                }
            }
            case "status" -> sendStatus(sender);
            case "timer" -> {
                sender.sendMessage(config.prefixed("&eRun Timer: &f" + DurationFormatter.format(challengeManager.getTimerManager().getRunElapsedSeconds())));
                sender.sendMessage(config.prefixed("&eGlobal Timer: &f" + DurationFormatter.format(challengeManager.getTimerManager().getGlobalElapsedSeconds())));
            }
            case "advancements" -> sendAdvancementList(sender, challengeManager.getAdvancementManager().getCompletedViews(), "Erreichte Advancements");
            case "missing" -> sendAdvancementList(sender, challengeManager.getAdvancementManager().getMissingViews(), "Fehlende Advancements");
            case "border" -> {
                sender.sendMessage(config.prefixed("&eBorder-Durchmesser: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCurrentSize())));
                sender.sendMessage(config.prefixed("&eBorder-Radius: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCurrentRadius())));
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.prefixed("&cNur Spieler koennen die GUI oeffnen."));
                    return true;
                }
                if (!sender.hasPermission("expandworldborder.view")) {
                    sender.sendMessage(config.prefixed("&cDu hast keine Berechtigung."));
                    return true;
                }
                guiManager.openMain(player);
            }
            case "debug" -> sender.sendMessage(config.prefixed("&eDebug-Mode in config.yml: &f" + config.isDebugMode()));
            default -> sender.sendMessage(config.prefixed("&cUnbekannter Subcommand."));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : SUB_COMMANDS) {
            if (value.startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private void sendStatus(CommandSender sender) {
        AdvancementManager advancements = challengeManager.getAdvancementManager();
        sender.sendMessage(config.prefixed("&eState: &f" + challengeManager.getState()));
        sender.sendMessage(config.prefixed("&eAttempt: &f#" + challengeManager.getAttemptManager().getAttemptCounter()));
        sender.sendMessage(config.prefixed("&eBorder: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCurrentSize())));
        sender.sendMessage(config.prefixed("&eAdvancements: &f" + advancements.getCompletedCount() + "/" + advancements.getTotalCount()));
        sender.sendMessage(config.prefixed("&eTop Run: &f" + formatEntry(challengeManager.getTopRunAdvancer())));
        sender.sendMessage(config.prefixed("&eTop Lifetime: &f" + formatEntry(challengeManager.getTopLifetimeAdvancer())));
    }

    private void sendAdvancementList(CommandSender sender, List<AdvancementManager.AdvancementView> views, String title) {
        sender.sendMessage(config.prefixed("&e" + title + ": &f" + views.size()));
        int limit = Math.min(10, views.size());
        for (int i = 0; i < limit; i++) {
            AdvancementManager.AdvancementView view = views.get(i);
            sender.sendMessage(config.prefixed("&7- &f" + view.displayName() + " &8(" + view.key() + ")"));
        }
        if (views.size() > limit) {
            sender.sendMessage(config.prefixed("&7... und " + (views.size() - limit) + " weitere. Nutze /borderchallenge gui fuer die Liste."));
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("expandworldborder.admin")) {
            return true;
        }
        sender.sendMessage(config.prefixed("&cDu hast keine Berechtigung."));
        return false;
    }

    private String formatEntry(Optional<PlayerStatsManager.LeaderboardEntry> entry) {
        return entry.map(value -> value.playerName() + " (" + value.value() + ")").orElse("Keine Daten");
    }
}
