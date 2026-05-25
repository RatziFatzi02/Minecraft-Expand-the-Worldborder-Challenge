package de.ratzifatzi.expandworldborder.gui;

import de.ratzifatzi.expandworldborder.ExpandWorldborderChallengePlugin;
import de.ratzifatzi.expandworldborder.challenge.ChallengeState;
import de.ratzifatzi.expandworldborder.config.PluginConfig;
import de.ratzifatzi.expandworldborder.manager.AdvancementManager;
import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import de.ratzifatzi.expandworldborder.manager.PlayerStatsManager;
import de.ratzifatzi.expandworldborder.util.DurationFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GUIManager implements Listener {

    private static final int SIZE = 54;
    private static final int SLOT_NAV_MAIN = 45;
    private static final int SLOT_NAV_COMPLETED = 46;
    private static final int SLOT_NAV_MISSING = 47;
    private static final int SLOT_NAV_ALL = 48;
    private static final int SLOT_NAV_STATS = 49;
    private static final int SLOT_PREV = 50;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_NAV_CONFIG = 53;
    private static final int PAGE_SIZE = 36;

    private final ExpandWorldborderChallengePlugin plugin;
    private final PluginConfig config;
    private final ChallengeManager challengeManager;

    public GUIManager(ExpandWorldborderChallengePlugin plugin, PluginConfig config, ChallengeManager challengeManager) {
        this.plugin = plugin;
        this.config = config;
        this.challengeManager = challengeManager;
    }

    public void openMain(Player player) {
        open(player, GuiScreenType.MAIN, 1);
    }

    public void refreshAllOpenGuis() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof ChallengeGuiHolder holder)) {
                continue;
            }
            Inventory rebuilt = buildInventory(holder.screenType(), holder.page());
            top.setContents(rebuilt.getContents());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ChallengeGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }
        if (handleNavigation(player, holder, rawSlot)) {
            return;
        }
        if (holder.screenType() == GuiScreenType.CONFIG) {
            handleConfigClick(player, rawSlot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChallengeGuiHolder) {
            event.setCancelled(true);
        }
    }

    private boolean handleNavigation(Player player, ChallengeGuiHolder holder, int rawSlot) {
        switch (rawSlot) {
            case SLOT_NAV_MAIN -> open(player, GuiScreenType.MAIN, 1);
            case SLOT_NAV_COMPLETED -> open(player, GuiScreenType.ADV_COMPLETED, 1);
            case SLOT_NAV_MISSING -> open(player, GuiScreenType.ADV_MISSING, 1);
            case SLOT_NAV_ALL -> open(player, GuiScreenType.ADV_ALL, 1);
            case SLOT_NAV_STATS -> open(player, GuiScreenType.STATS, 1);
            case SLOT_NAV_CONFIG -> openConfig(player);
            case SLOT_PREV -> open(player, holder.screenType(), Math.max(1, holder.page() - 1));
            case SLOT_NEXT -> open(player, holder.screenType(), holder.page() + 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleConfigClick(Player player, int rawSlot) {
        if (!player.hasPermission("expandworldborder.admin")) {
            player.closeInventory();
            return;
        }
        switch (rawSlot) {
            case 10 -> challengeManager.startChallenge(player);
            case 11 -> {
                ChallengeState state = challengeManager.getState();
                if (state == ChallengeState.RUNNING) {
                    challengeManager.pauseChallenge(player);
                } else if (state == ChallengeState.PAUSED) {
                    challengeManager.resumeChallenge(player);
                } else {
                    player.sendMessage(config.prefixed("&ePause/Resume ist nur in RUNNING oder PAUSED moeglich."));
                }
            }
            case 12 -> challengeManager.manualReset(player);
            case 13 -> challengeManager.toggleScoreboard(player);
            case 14 -> challengeManager.toggleWorldSeedVisibility(player);
            case 15 -> challengeManager.forceJoinLobbyPlayers(player);
            case 16 -> challengeManager.reloadAdvancementPool(player);
            default -> {
                return;
            }
        }
        openConfig(player);
    }

    private void openConfig(Player player) {
        if (!player.hasPermission("expandworldborder.admin")) {
            player.sendMessage(config.prefixed("&cDu hast keine Berechtigung."));
            return;
        }
        open(player, GuiScreenType.CONFIG, 1);
    }

    private void open(Player player, GuiScreenType type, int page) {
        player.openInventory(buildInventory(type, Math.max(1, page)));
    }

    private Inventory buildInventory(GuiScreenType type, int page) {
        ChallengeGuiHolder holder = new ChallengeGuiHolder(type, page);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title(type));
        holder.bindInventory(inventory);
        fillBackground(inventory);
        switch (type) {
            case MAIN -> renderMain(inventory);
            case ADV_COMPLETED -> renderAdvancementList(inventory, challengeManager.getAdvancementManager().getCompletedViews(), page, true);
            case ADV_MISSING -> renderAdvancementList(inventory, challengeManager.getAdvancementManager().getMissingViews(), page, false);
            case ADV_ALL -> renderAdvancementList(inventory, challengeManager.getAdvancementManager().getAllViews(), page, null);
            case STATS -> renderStats(inventory);
            case CONFIG -> renderConfig(inventory);
        }
        renderNavigation(inventory, page);
        return inventory;
    }

    private String title(GuiScreenType type) {
        return switch (type) {
            case MAIN -> "Worldborder Challenge";
            case ADV_COMPLETED -> "Advancements - Erreicht";
            case ADV_MISSING -> "Advancements - Fehlt";
            case ADV_ALL -> "Advancements - Alle";
            case STATS -> "Worldborder Stats";
            case CONFIG -> "Worldborder Admin";
        };
    }

    private void renderMain(Inventory inventory) {
        AdvancementManager advancements = challengeManager.getAdvancementManager();
        inventory.setItem(10, createItem(Material.CLOCK, "&eAttempt", List.of("&7Current: &f#" + challengeManager.getAttemptManager().getAttemptCounter())));
        inventory.setItem(12, createItem(Material.BARRIER, "&eWorldborder", List.of(
                "&7Diameter: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCurrentSize()),
                "&7Radius: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCurrentRadius()),
                "&7Center: &f" + challengeManager.formatSize(challengeManager.getBorderManager().getCenterX())
                        + " / " + challengeManager.formatSize(challengeManager.getBorderManager().getCenterZ())
        )));
        inventory.setItem(14, createItem(Material.KNOWLEDGE_BOOK, "&eAdvancements", List.of(
                "&7Completed: &f" + advancements.getCompletedCount() + " / " + advancements.getTotalCount(),
                "&7Missing: &f" + advancements.getMissingCount(),
                "&7Progress: &f" + String.format(java.util.Locale.US, "%.2f%%", advancements.getProgressPercent())
        )));
        inventory.setItem(16, createItem(Material.COMPASS, "&eTimer", List.of(
                "&7Run: &f" + DurationFormatter.format(challengeManager.getTimerManager().getRunElapsedSeconds()),
                "&7Global: &f" + DurationFormatter.format(challengeManager.getTimerManager().getGlobalElapsedSeconds())
        )));
        AdvancementManager.AdvancementView last = advancements.getLastAdvancementView().orElse(null);
        inventory.setItem(31, createItem(last == null ? Material.PAPER : last.icon(), "&eLast Advancement",
                last == null ? List.of("&7No advancement completed yet") : List.of("&f" + last.displayName(), "&8" + last.key())));
    }

    private void renderAdvancementList(Inventory inventory, List<AdvancementManager.AdvancementView> views, int page, Boolean completedState) {
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, views.size());
        for (int i = start; i < end; i++) {
            AdvancementManager.AdvancementView view = views.get(i);
            boolean completed = completedState != null ? completedState : challengeManager.getAdvancementManager().isCompleted(view.key());
            List<String> lore = new ArrayList<>();
            lore.add(completed ? "&aErreicht" : "&cFehlt noch");
            lore.add("&7Namespace: &f" + view.namespace());
            lore.add("&8" + view.key());
            challengeManager.getAdvancementManager().getFirstCompleterName(view.key())
                    .ifPresent(name -> lore.add("&7First by: &f" + name));
            inventory.setItem(i - start, createItem(view.icon(), (completed ? "&a" : "&e") + view.displayName(), lore));
        }
        inventory.setItem(40, createItem(Material.BOOK, "&ePage " + page, List.of("&7Entries: &f" + views.size())));
    }

    private void renderStats(Inventory inventory) {
        PlayerStatsManager stats = challengeManager.getPlayerStatsManager();
        inventory.setItem(10, createItem(Material.EMERALD, "&aRun Advancements", List.of(
                "&7Total: &f" + stats.getCurrentRunAdvancementTotal(),
                "&7Top: &f" + challengeManager.getTopRunAdvancer().map(entry -> entry.playerName() + " (" + entry.value() + ")").orElse("No data")
        )));
        inventory.setItem(12, createItem(Material.DIAMOND, "&bLifetime Advancements", List.of(
                "&7Total: &f" + stats.getLifetimeAdvancementTotal(),
                "&7Top: &f" + challengeManager.getTopLifetimeAdvancer().map(entry -> entry.playerName() + " (" + entry.value() + ")").orElse("No data")
        )));
        inventory.setItem(14, createItem(Material.BONE, "&cDeaths", List.of(
                "&7Run: &f" + stats.getCurrentRunDeathsTotal(),
                "&7Lifetime: &f" + stats.getLifetimeDeathsTotal(),
                "&7Most deaths: &f" + challengeManager.getTopLifetimeDeaths().map(entry -> entry.playerName() + " (" + entry.value() + ")").orElse("No data")
        )));
        inventory.setItem(16, createItem(Material.PLAYER_HEAD, "&ePlayers", List.of("&7Tracked: &f" + stats.getTrackedPlayerCount())));
    }

    private void renderConfig(Inventory inventory) {
        inventory.setItem(10, createItem(Material.LIME_CONCRETE, "&aStart Challenge", List.of("&7Starts or restarts the challenge flow")));
        inventory.setItem(11, createItem(Material.YELLOW_DYE, "&ePause / Resume", List.of("&7Current state: &f" + challengeManager.getState())));
        inventory.setItem(12, createItem(Material.ENDER_PEARL, "&eForce Reset", List.of("&7Regenerate the run world")));
        inventory.setItem(13, createItem(Material.OAK_SIGN, "&eScoreboard", List.of("&7Current: &f" + (challengeManager.isScoreboardEnabled() ? "ON" : "OFF"))));
        inventory.setItem(14, createItem(Material.ENDER_EYE, "&eShow Seed", List.of("&7Current: &f" + (challengeManager.isWorldSeedVisible() ? "ON" : "OFF"))));
        inventory.setItem(15, createItem(Material.MINECART, "&eForce Join", List.of("&7Move waiting lobby players into the run")));
        inventory.setItem(16, createItem(Material.KNOWLEDGE_BOOK, "&eReload Advancements", List.of("&7Reloads advancement pool from server")));
    }

    private void renderNavigation(Inventory inventory, int page) {
        inventory.setItem(SLOT_NAV_MAIN, createItem(Material.MAP, "&bMain", List.of()));
        inventory.setItem(SLOT_NAV_COMPLETED, createItem(Material.LIME_DYE, "&aErreicht", List.of()));
        inventory.setItem(SLOT_NAV_MISSING, createItem(Material.RED_DYE, "&cFehlt", List.of()));
        inventory.setItem(SLOT_NAV_ALL, createItem(Material.BOOK, "&eAlle", List.of()));
        inventory.setItem(SLOT_NAV_STATS, createItem(Material.NAME_TAG, "&bStats", List.of()));
        inventory.setItem(SLOT_PREV, createItem(Material.ARROW, "&ePrev", List.of("&7Page: &f" + page)));
        inventory.setItem(SLOT_NEXT, createItem(Material.SPECTRAL_ARROW, "&eNext", List.of("&7Page: &f" + page)));
        inventory.setItem(SLOT_NAV_CONFIG, createItem(Material.WRITABLE_BOOK, "&cAdmin", List.of()));
    }

    private void fillBackground(Inventory inventory) {
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    private ItemStack createItem(Material material, String displayName, List<String> loreLines) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(colorize(displayName));
        if (!loreLines.isEmpty()) {
            meta.setLore(loreLines.stream().map(this::colorize).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
