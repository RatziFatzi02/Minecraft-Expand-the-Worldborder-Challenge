package de.ratzifatzi.expandworldborder.manager;

import de.ratzifatzi.expandworldborder.ExpandWorldborderChallengePlugin;
import de.ratzifatzi.expandworldborder.challenge.ChallengeState;
import de.ratzifatzi.expandworldborder.config.PluginConfig;
import de.ratzifatzi.expandworldborder.gui.GUIManager;
import de.ratzifatzi.expandworldborder.persistence.PlayerDataStore;
import de.ratzifatzi.expandworldborder.util.DurationFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ChallengeManager {

    private static final String SCOREBOARD_OBJECTIVE = "ewbc";
    private static final String[] SCOREBOARD_ENTRIES = {
            ChatColor.BLACK.toString(),
            ChatColor.DARK_BLUE.toString(),
            ChatColor.DARK_GREEN.toString(),
            ChatColor.DARK_AQUA.toString(),
            ChatColor.DARK_RED.toString(),
            ChatColor.DARK_PURPLE.toString(),
            ChatColor.GOLD.toString(),
            ChatColor.GRAY.toString(),
            ChatColor.BLUE.toString()
    };

    private enum ResetPhase {
        NONE,
        TO_LOBBY_COUNTDOWN,
        LOBBY_WAIT_COUNTDOWN
    }

    private record PausedPlayerSnapshot(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack[] extra,
            ItemStack offhand,
            float exp,
            int level,
            int totalExperience
    ) {
    }

    private final ExpandWorldborderChallengePlugin plugin;
    private final PluginConfig config;
    private final TimerManager timerManager;
    private final AttemptManager attemptManager;
    private final AdvancementManager advancementManager;
    private final PlayerStatsManager playerStatsManager;
    private final WorldManager worldManager;
    private final BorderManager borderManager;
    private final PlayerDataStore playerDataStore;
    private final Map<UUID, PausedPlayerSnapshot> pausedPlayerSnapshots = new HashMap<>();
    private final Set<UUID> activeRunParticipants = new HashSet<>();

    private ChallengeState state;
    private boolean scoreboardEnabled;
    private boolean showWorldSeed;
    private GUIManager guiManager;
    private BukkitTask toLobbyCountdownTask;
    private BukkitTask lobbyWaitCountdownTask;
    private BukkitTask scoreboardTask;
    private BukkitTask autoPauseTask;
    private BukkitTask pausedActionbarTask;
    private ResetPhase resetPhase = ResetPhase.NONE;
    private int resetPhaseSecondsRemaining;

    public ChallengeManager(
            ExpandWorldborderChallengePlugin plugin,
            PluginConfig config,
            TimerManager timerManager,
            AttemptManager attemptManager,
            AdvancementManager advancementManager,
            PlayerStatsManager playerStatsManager,
            WorldManager worldManager,
            BorderManager borderManager,
            PlayerDataStore playerDataStore,
            ChallengeState initialState,
            boolean scoreboardEnabled,
            boolean showWorldSeed
    ) {
        this.plugin = plugin;
        this.config = config;
        this.timerManager = timerManager;
        this.attemptManager = attemptManager;
        this.advancementManager = advancementManager;
        this.playerStatsManager = playerStatsManager;
        this.worldManager = worldManager;
        this.borderManager = borderManager;
        this.playerDataStore = playerDataStore;
        this.state = initialState;
        this.scoreboardEnabled = scoreboardEnabled;
        this.showWorldSeed = showWorldSeed;
    }

    public void setGuiManager(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    public void bootstrapFromStoredState() {
        loadPausedSnapshotsFromData();
        activeRunParticipants.clear();
        activeRunParticipants.addAll(playerDataStore.getActiveRunParticipants());
        worldManager.ensureLobbyWorld();

        if (state == ChallengeState.RESETTING) {
            plugin.getLogger().warning("Stored state was RESETTING. Falling back to IDLE for safe startup.");
            state = ChallengeState.IDLE;
        }

        if (state == ChallengeState.RUNNING) {
            worldManager.ensureRunWorldFamily();
            borderManager.restoreCurrentSize();
            timerManager.resumeForRunningState();
        } else if (state == ChallengeState.PAUSED) {
            timerManager.pauseForPauseState();
            startPausedActionbarTask();
        } else {
            pausedPlayerSnapshots.clear();
            activeRunParticipants.clear();
            timerManager.stopRunTimer();
            timerManager.stopGlobalTimer();
        }
        applyScoreboardMode();
        plugin.persistRuntimeData();
    }

    public void shutdown() {
        cancelResetTasks();
        cancelAutoPauseTask();
        stopPausedActionbarTask();
        stopScoreboardTask();
        clearScoreboardsForAll();
    }

    public void startChallenge(CommandSender sender) {
        if (state == ChallengeState.RUNNING) {
            sender.sendMessage(config.prefixed("&eChallenge laeuft bereits."));
            return;
        }
        if (state == ChallengeState.RESETTING) {
            sender.sendMessage(config.prefixed("&eChallenge wird bereits zurueckgesetzt."));
            return;
        }
        beginResetSequence(config.prefixed("&eChallenge wird gestartet..."));
    }

    public void manualReset(CommandSender sender) {
        if (state == ChallengeState.RESETTING) {
            sender.sendMessage(config.prefixed("&eReset laeuft bereits."));
            return;
        }
        beginResetSequence(config.prefixed("&eManueller Reset ausgeloest."));
    }

    public void pauseChallenge(CommandSender sender) {
        if (state != ChallengeState.RUNNING) {
            sender.sendMessage(config.prefixed("&ePause ist nur im RUNNING-Status moeglich."));
            return;
        }
        pauseChallengeInternal(config.message("challenge-paused", "&eChallenge wurde pausiert.", Map.of()));
    }

    public void resumeChallenge(CommandSender sender) {
        if (state != ChallengeState.PAUSED) {
            sender.sendMessage(config.prefixed("&eResume ist nur im PAUSED-Status moeglich."));
            return;
        }
        cancelAutoPauseTask();
        stopPausedActionbarTask();
        worldManager.ensureRunWorldFamily();
        borderManager.restoreCurrentSize();
        state = ChallengeState.RUNNING;
        timerManager.resumeForRunningState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldEnterRun = !config.isJoinControlEnabled() || activeRunParticipants.contains(player.getUniqueId());
            if (!shouldEnterRun) {
                resetPlayerForNewRun(player);
                worldManager.teleportPlayerToLobby(player);
                continue;
            }
            if (!restorePlayerAfterPause(player)) {
                worldManager.teleportPlayerToRunWorld(player);
            }
            activeRunParticipants.add(player.getUniqueId());
        }
        Bukkit.broadcastMessage(config.message("challenge-resumed", "&aChallenge wurde fortgesetzt.", Map.of()));
        plugin.persistRuntimeData();
        refreshViews();
    }

    public void forceJoinLobbyPlayers(CommandSender sender) {
        if (state != ChallengeState.RUNNING) {
            sender.sendMessage(config.prefixed("&eForcejoin ist nur im RUNNING-Status moeglich."));
            return;
        }
        int joined = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (activeRunParticipants.contains(player.getUniqueId())) {
                continue;
            }
            activeRunParticipants.add(player.getUniqueId());
            playerDataStore.setLastKnownName(player.getUniqueId(), player.getName());
            resetPlayerForNewRun(player);
            worldManager.teleportPlayerToRunWorld(player);
            applyScoreboardFor(player);
            joined++;
        }
        if (joined == 0) {
            sender.sendMessage(config.message("join-control-forcejoin-empty", "&eKeine wartenden Lobby-Spieler gefunden.", Map.of()));
            return;
        }
        Bukkit.broadcastMessage(config.message("join-control-forcejoin", "&a%count% Spieler wurden in den aktiven Run aufgenommen.", Map.of("count", String.valueOf(joined))));
        plugin.persistRuntimeData();
        refreshViews();
    }

    public void stopChallenge(CommandSender sender) {
        cancelResetTasks();
        cancelAutoPauseTask();
        stopPausedActionbarTask();
        state = ChallengeState.IDLE;
        timerManager.stopRunTimer();
        timerManager.stopGlobalTimer();
        pausedPlayerSnapshots.clear();
        activeRunParticipants.clear();
        worldManager.teleportAllPlayersToLobby();
        Bukkit.broadcastMessage(config.message("run-stopped", "&cChallenge gestoppt.", Map.of()));
        plugin.persistRuntimeData();
        refreshViews();
    }

    public void handleAdvancement(Player player, org.bukkit.advancement.Advancement advancement) {
        if (state != ChallengeState.RUNNING || !canPlayerAffectRun(player)) {
            return;
        }
        Optional<AdvancementManager.CompletionResult> completed = advancementManager.tryComplete(advancement, player);
        if (completed.isEmpty()) {
            return;
        }
        AdvancementManager.AdvancementView view = completed.get().view();
        playerStatsManager.recordAdvancement(player);
        double border = borderManager.growForAdvancement();
        if (config.isFeedbackSoundEnabled()) {
            player.playSound(player.getLocation(), config.getFeedbackSound(), 1.0F, 1.0F);
        }
        if (config.isFeedbackActionbarEnabled()) {
            player.sendActionBar(Component.text("Border: " + formatSize(border) + " | " + view.displayName()));
        }
        if (config.isFeedbackChatEnabled()) {
            Bukkit.broadcastMessage(config.message(
                    "advancement-completed",
                    "&a%player% hat %advancement% erreicht. Border: &f%border%",
                    Map.of("player", player.getName(), "advancement", view.displayName(), "border", formatSize(border))
            ));
        }
        plugin.persistRuntimeData();
        refreshViews();
    }

    public void handlePlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (state != ChallengeState.RUNNING || !canPlayerAffectRun(player)) {
            return;
        }
        playerStatsManager.recordDeath(player, resolveDeathReason(player));
        beginResetSequence(config.message("run-lost", "&c%player% ist gestorben. Run verloren!", Map.of("player", player.getName())));
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        if (state != ChallengeState.RUNNING) {
            event.setRespawnLocation(worldManager.getLobbySpawnLocation());
        }
    }

    public void handlePlayerJoin(Player player) {
        cancelAutoPauseTask();
        playerStatsManager.ensurePlayer(player);
        playerDataStore.setLastKnownName(player.getUniqueId(), player.getName());
        if (state == ChallengeState.RUNNING) {
            if (config.isJoinControlEnabled() && !activeRunParticipants.contains(player.getUniqueId())) {
                resetPlayerForNewRun(player);
                worldManager.teleportPlayerToLobby(player);
                player.sendMessage(config.message("join-control-lobby-wait", "&eEin Run laeuft bereits. Du wartest in der Lobby bis zum naechsten Run.", Map.of()));
            } else {
                activeRunParticipants.add(player.getUniqueId());
                worldManager.teleportPlayerToRunWorld(player);
            }
        } else if (state == ChallengeState.PAUSED) {
            worldManager.teleportPlayerToLobby(player);
        } else {
            worldManager.teleportPlayerToLobby(player);
        }
        applyScoreboardFor(player);
        showCurrentResetTitleFor(player);
        plugin.persistRuntimeData();
    }

    public void handlePlayerDisconnect(Player player) {
        if (state != ChallengeState.RUNNING) {
            return;
        }
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            cancelAutoPauseTask();
            autoPauseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == ChallengeState.RUNNING && Bukkit.getOnlinePlayers().isEmpty()) {
                    pauseChallengeInternal(config.message("pause-auto-triggered", "&eChallenge wurde automatisch pausiert.", Map.of()));
                }
            }, Math.max(0, config.getAutoPauseDelaySeconds()) * 20L);
        }
    }

    public boolean canPlayerAffectRun(Player player) {
        return player != null
                && worldManager.isRunWorldFamily(player.getWorld())
                && (!config.isJoinControlEnabled() || activeRunParticipants.contains(player.getUniqueId()));
    }

    public void toggleScoreboard(CommandSender sender) {
        scoreboardEnabled = !scoreboardEnabled;
        sender.sendMessage(config.prefixed("&eScoreboard ist jetzt: &f" + (scoreboardEnabled ? "ON" : "OFF")));
        applyScoreboardMode();
        plugin.persistRuntimeData();
    }

    public void toggleWorldSeedVisibility(CommandSender sender) {
        showWorldSeed = !showWorldSeed;
        sender.sendMessage(config.prefixed("&eSeed-Anzeige ist jetzt: &f" + (showWorldSeed ? "ON" : "OFF")));
        plugin.persistRuntimeData();
        refreshViews();
    }

    public void reloadAdvancementPool(CommandSender sender) {
        advancementManager.reloadPool();
        sender.sendMessage(config.prefixed("&aAdvancement-Pool neu geladen: &f" + advancementManager.getTotalCount()));
        plugin.persistRuntimeData();
        refreshViews();
    }

    private void beginResetSequence(String message) {
        cancelResetTasks();
        stopPausedActionbarTask();
        state = ChallengeState.RESETTING;
        resetPhase = ResetPhase.TO_LOBBY_COUNTDOWN;
        resetPhaseSecondsRemaining = Math.max(0, config.getTransitionToLobbySeconds());
        timerManager.stopRunTimer();
        Bukkit.broadcastMessage(message);
        if (resetPhaseSecondsRemaining <= 0) {
            movePlayersToLobbyAndWait();
            return;
        }
        showToLobbyTitleToAll(resetPhaseSecondsRemaining);
        toLobbyCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            resetPhaseSecondsRemaining--;
            if (resetPhaseSecondsRemaining <= 0) {
                cancelToLobbyTask();
                movePlayersToLobbyAndWait();
                return;
            }
            showToLobbyTitleToAll(resetPhaseSecondsRemaining);
        }, 20L, 20L);
        plugin.persistRuntimeData();
        refreshViews();
    }

    private void movePlayersToLobbyAndWait() {
        worldManager.teleportAllPlayersToLobby();
        resetPhase = ResetPhase.LOBBY_WAIT_COUNTDOWN;
        resetPhaseSecondsRemaining = Math.max(0, config.getTransitionLobbyWaitSeconds());
        if (resetPhaseSecondsRemaining <= 0) {
            createAndStartNextRun();
            return;
        }
        lobbyWaitCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            resetPhaseSecondsRemaining--;
            if (resetPhaseSecondsRemaining <= 0) {
                cancelLobbyWaitTask();
                createAndStartNextRun();
                return;
            }
            if (resetPhaseSecondsRemaining <= config.getTransitionLobbyTitleLastSeconds()) {
                showToRunTitleToAll(resetPhaseSecondsRemaining);
            }
        }, 20L, 20L);
    }

    private void createAndStartNextRun() {
        try {
            Bukkit.broadcastMessage(config.message("resetting", "&eReset laeuft... neue Welt wird erstellt.", Map.of()));
            long seed = worldManager.pickNextSeed();
            worldManager.regenerateRunWorld(seed);
            Location runSpawn = worldManager.getRunSpawnLocation();
            borderManager.resetToStartSize();
            borderManager.centerOn(runSpawn.getX(), runSpawn.getZ());
            advancementManager.startNewRun();
            playerStatsManager.resetRunStats();
            pausedPlayerSnapshots.clear();
            activeRunParticipants.clear();
            for (Player player : Bukkit.getOnlinePlayers()) {
                resetPlayerForNewRun(player);
                activeRunParticipants.add(player.getUniqueId());
                playerDataStore.setLastKnownName(player.getUniqueId(), player.getName());
            }
            attemptManager.nextAttempt();
            state = ChallengeState.RUNNING;
            resetPhase = ResetPhase.NONE;
            resetPhaseSecondsRemaining = 0;
            timerManager.startNewRunTimer();
            worldManager.teleportAllPlayersToRunWorld(runSpawn);
            String key = showWorldSeed ? "run-started-with-seed" : "run-started";
            String fallback = showWorldSeed
                    ? "&aNeuer Run gestartet! Versuch #%attempt% | Seed: %seed% | Border: %border%"
                    : "&aNeuer Run gestartet! Versuch #%attempt% | Border: %border%";
            Bukkit.broadcastMessage(config.message(key, fallback, Map.of(
                    "attempt", String.valueOf(attemptManager.getAttemptCounter()),
                    "seed", String.valueOf(worldManager.getCurrentSeed()),
                    "border", formatSize(borderManager.getCurrentSize())
            )));
            plugin.persistRuntimeData();
            refreshViews();
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("Run reset failed: " + exception.getMessage());
            state = ChallengeState.IDLE;
            resetPhase = ResetPhase.NONE;
            plugin.persistRuntimeData();
        }
    }

    private void pauseChallengeInternal(String message) {
        state = ChallengeState.PAUSED;
        timerManager.pauseForPauseState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (worldManager.isRunWorldFamily(player.getWorld())) {
                capturePlayerForPause(player);
            }
            resetPlayerForNewRun(player);
            worldManager.teleportPlayerToLobby(player);
        }
        startPausedActionbarTask();
        Bukkit.broadcastMessage(message);
        plugin.persistRuntimeData();
        refreshViews();
    }

    private void capturePlayerForPause(Player player) {
        Location location = player.getLocation();
        pausedPlayerSnapshots.put(player.getUniqueId(), new PausedPlayerSnapshot(
                location.getWorld() == null ? null : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                copyItemArray(player.getInventory().getContents()),
                copyItemArray(player.getInventory().getArmorContents()),
                copyItemArray(player.getInventory().getExtraContents()),
                player.getInventory().getItemInOffHand().clone(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience()
        ));
    }

    private boolean restorePlayerAfterPause(Player player) {
        PausedPlayerSnapshot snapshot = pausedPlayerSnapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        Location target = resolvePausedLocation(snapshot);
        if (target == null) {
            target = worldManager.getRunSpawnLocation();
        }
        player.teleport(target);
        player.getInventory().setContents(copyItemArray(snapshot.contents()));
        player.getInventory().setArmorContents(copyItemArray(snapshot.armor()));
        player.getInventory().setExtraContents(normalizeExtraContents(player, snapshot.extra()));
        player.getInventory().setItemInOffHand(snapshot.offhand() == null ? null : snapshot.offhand().clone());
        player.setExp(snapshot.exp());
        player.setLevel(snapshot.level());
        player.setTotalExperience(snapshot.totalExperience());
        return true;
    }

    private Location resolvePausedLocation(PausedPlayerSnapshot snapshot) {
        if (snapshot.worldName() == null || snapshot.worldName().isBlank() || !worldManager.isRunWorldFamily(snapshot.worldName())) {
            return null;
        }
        World world = Bukkit.getWorld(snapshot.worldName());
        if (world == null) {
            world = worldManager.resolveRunWorldForEnvironment(snapshot.worldName().endsWith("_nether")
                    ? World.Environment.NETHER
                    : snapshot.worldName().endsWith("_the_end") ? World.Environment.THE_END : World.Environment.NORMAL);
        }
        return new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
    }

    private void loadPausedSnapshotsFromData() {
        pausedPlayerSnapshots.clear();
        for (Map.Entry<UUID, PlayerDataStore.PausedPlayerSnapshotData> entry : playerDataStore.getPausedPlayerSnapshots().entrySet()) {
            PlayerDataStore.PausedPlayerSnapshotData snapshot = entry.getValue();
            pausedPlayerSnapshots.put(entry.getKey(), new PausedPlayerSnapshot(
                    snapshot.worldName(),
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.yaw(),
                    snapshot.pitch(),
                    copyItemArray(snapshot.contents()),
                    copyItemArray(snapshot.armor()),
                    copyItemArray(snapshot.extra()),
                    snapshot.offhand() == null ? null : snapshot.offhand().clone(),
                    snapshot.exp(),
                    snapshot.level(),
                    snapshot.totalExperience()
            ));
        }
    }

    public Map<UUID, PlayerDataStore.PausedPlayerSnapshotData> getPausedPlayerSnapshotsForStorage() {
        Map<UUID, PlayerDataStore.PausedPlayerSnapshotData> result = new HashMap<>();
        for (Map.Entry<UUID, PausedPlayerSnapshot> entry : pausedPlayerSnapshots.entrySet()) {
            PausedPlayerSnapshot snapshot = entry.getValue();
            result.put(entry.getKey(), new PlayerDataStore.PausedPlayerSnapshotData(
                    snapshot.worldName(),
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.yaw(),
                    snapshot.pitch(),
                    copyItemArray(snapshot.contents()),
                    copyItemArray(snapshot.armor()),
                    copyItemArray(snapshot.extra()),
                    snapshot.offhand() == null ? null : snapshot.offhand().clone(),
                    snapshot.exp(),
                    snapshot.level(),
                    snapshot.totalExperience()
            ));
        }
        return result;
    }

    public Set<UUID> getActiveRunParticipantsForStorage() {
        return Set.copyOf(activeRunParticipants);
    }

    private void resetPlayerForNewRun(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.getInventory().setItemInOffHand(null);
        player.setExp(0.0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    private String resolveDeathReason(Player player) {
        EntityDamageEvent damageEvent = player.getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            return damager.getType().name();
        }
        if (damageEvent != null && damageEvent.getCause() != null) {
            return damageEvent.getCause().name();
        }
        return "UNKNOWN";
    }

    private void applyScoreboardMode() {
        if (scoreboardEnabled) {
            startScoreboardTaskIfNeeded();
            updateScoreboardsForAll();
        } else {
            stopScoreboardTask();
            clearScoreboardsForAll();
        }
    }

    private void startScoreboardTaskIfNeeded() {
        if (scoreboardTask != null) {
            return;
        }
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboardsForAll, 20L, 20L);
    }

    private void updateScoreboardsForAll() {
        if (!scoreboardEnabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyScoreboardFor(player);
        }
    }

    private void applyScoreboardFor(Player player) {
        if (!scoreboardEnabled) {
            clearScoreboard(player);
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(SCOREBOARD_OBJECTIVE, Criteria.DUMMY, ChatColor.GOLD + "Worldborder");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = buildScoreboardLines();
        int score = lines.size();
        for (int i = 0; i < lines.size() && i < SCOREBOARD_ENTRIES.length; i++) {
            Team team = board.registerNewTeam("line_" + i);
            String entry = SCOREBOARD_ENTRIES[i];
            team.addEntry(entry);
            team.setPrefix(trimScoreboardLine(lines.get(i)));
            objective.getScore(entry).setScore(score - i);
        }
        player.setScoreboard(board);
    }

    private List<String> buildScoreboardLines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.YELLOW + "State: " + ChatColor.WHITE + state.name());
        lines.add(ChatColor.YELLOW + "Attempt: " + ChatColor.WHITE + "#" + attemptManager.getAttemptCounter());
        lines.add(ChatColor.YELLOW + "Border: " + ChatColor.WHITE + formatSize(borderManager.getCurrentSize()));
        lines.add(ChatColor.YELLOW + "Radius: " + ChatColor.WHITE + formatSize(borderManager.getCurrentRadius()));
        lines.add(ChatColor.YELLOW + "Center: " + ChatColor.WHITE + formatSize(borderManager.getCenterX()) + "/" + formatSize(borderManager.getCenterZ()));
        lines.add(ChatColor.YELLOW + "Adv: " + ChatColor.WHITE + advancementManager.getCompletedCount() + "/" + advancementManager.getTotalCount());
        lines.add(ChatColor.YELLOW + "Missing: " + ChatColor.WHITE + advancementManager.getMissingCount());
        lines.add(ChatColor.YELLOW + "Run: " + ChatColor.WHITE + DurationFormatter.format(timerManager.getRunElapsedSeconds()));
        lines.add(ChatColor.YELLOW + "Global: " + ChatColor.WHITE + DurationFormatter.format(timerManager.getGlobalElapsedSeconds()));
        if (showWorldSeed) {
            lines.add(ChatColor.YELLOW + "Seed: " + ChatColor.WHITE + worldManager.getCurrentSeed());
        }
        return lines;
    }

    private String trimScoreboardLine(String line) {
        return line.length() <= 64 ? line : line.substring(0, 64);
    }

    private void clearScoreboardsForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearScoreboard(player);
        }
    }

    private void clearScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void refreshViews() {
        if (guiManager != null) {
            guiManager.refreshAllOpenGuis();
        }
        if (scoreboardEnabled) {
            updateScoreboardsForAll();
        }
    }

    private void startPausedActionbarTask() {
        if (pausedActionbarTask != null) {
            return;
        }
        pausedActionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != ChallengeState.PAUSED) {
                return;
            }
            Component message = Component.text(ChatColor.YELLOW + "Challenge ist pausiert. " + ChatColor.GRAY + "/borderchallenge resume");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(message);
            }
        }, 20L, 40L);
    }

    private void stopPausedActionbarTask() {
        if (pausedActionbarTask != null) {
            pausedActionbarTask.cancel();
            pausedActionbarTask = null;
        }
    }

    private void cancelResetTasks() {
        cancelToLobbyTask();
        cancelLobbyWaitTask();
        resetPhase = ResetPhase.NONE;
        resetPhaseSecondsRemaining = 0;
    }

    private void cancelToLobbyTask() {
        if (toLobbyCountdownTask != null) {
            toLobbyCountdownTask.cancel();
            toLobbyCountdownTask = null;
        }
    }

    private void cancelLobbyWaitTask() {
        if (lobbyWaitCountdownTask != null) {
            lobbyWaitCountdownTask.cancel();
            lobbyWaitCountdownTask = null;
        }
    }

    private void cancelAutoPauseTask() {
        if (autoPauseTask != null) {
            autoPauseTask.cancel();
            autoPauseTask = null;
        }
    }

    private void stopScoreboardTask() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
    }

    private void showCurrentResetTitleFor(Player player) {
        if (state != ChallengeState.RESETTING || resetPhaseSecondsRemaining <= 0) {
            return;
        }
        if (resetPhase == ResetPhase.TO_LOBBY_COUNTDOWN) {
            showToLobbyTitle(player, resetPhaseSecondsRemaining);
        } else if (resetPhase == ResetPhase.LOBBY_WAIT_COUNTDOWN) {
            showToRunTitle(player, resetPhaseSecondsRemaining);
        }
    }

    private void showToLobbyTitleToAll(int seconds) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showToLobbyTitle(player, seconds);
        }
    }

    private void showToRunTitleToAll(int seconds) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showToRunTitle(player, seconds);
        }
    }

    private void showToLobbyTitle(Player player, int seconds) {
        player.sendTitle(
                config.rawMessage("title.to-lobby.main", "&cLobby in %seconds%s", Map.of("seconds", String.valueOf(seconds))),
                config.rawMessage("title.to-lobby.sub", "&7Bereite dich auf den Reset vor", Map.of("seconds", String.valueOf(seconds))),
                0,
                20,
                0
        );
    }

    private void showToRunTitle(Player player, int seconds) {
        player.sendTitle(
                config.rawMessage("title.to-run.main", "&aNeuer Run in %seconds%s", Map.of("seconds", String.valueOf(seconds))),
                config.rawMessage("title.to-run.sub", "&7Die Border wurde zurueckgesetzt", Map.of("seconds", String.valueOf(seconds))),
                0,
                20,
                0
        );
    }

    private static ItemStack[] copyItemArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private static ItemStack[] normalizeExtraContents(Player player, ItemStack[] source) {
        int extraLength = player.getInventory().getExtraContents().length;
        ItemStack[] normalized = new ItemStack[extraLength];
        if (source == null) {
            return normalized;
        }
        int copyLength = Math.min(extraLength, source.length);
        for (int i = 0; i < copyLength; i++) {
            normalized[i] = source[i] == null ? null : source[i].clone();
        }
        return normalized;
    }

    public String formatSize(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    public ChallengeState getState() {
        return state;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public boolean isWorldSeedVisible() {
        return showWorldSeed;
    }

    public AdvancementManager getAdvancementManager() {
        return advancementManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public AttemptManager getAttemptManager() {
        return attemptManager;
    }

    public PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public Optional<PlayerStatsManager.LeaderboardEntry> getTopRunAdvancer() {
        return playerStatsManager.getTopRunAdvancer();
    }

    public Optional<PlayerStatsManager.LeaderboardEntry> getTopLifetimeAdvancer() {
        return playerStatsManager.getTopLifetimeAdvancer();
    }

    public Optional<PlayerStatsManager.LeaderboardEntry> getTopLifetimeDeaths() {
        return playerStatsManager.getTopLifetimeDeaths();
    }
}
