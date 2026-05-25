package de.ratzifatzi.expandworldborder;

import de.ratzifatzi.expandworldborder.challenge.ChallengeState;
import de.ratzifatzi.expandworldborder.command.BorderChallengeCommand;
import de.ratzifatzi.expandworldborder.config.PluginConfig;
import de.ratzifatzi.expandworldborder.gui.GUIManager;
import de.ratzifatzi.expandworldborder.listener.AdvancementListener;
import de.ratzifatzi.expandworldborder.listener.DeathListener;
import de.ratzifatzi.expandworldborder.listener.PlayerConnectionListener;
import de.ratzifatzi.expandworldborder.listener.PortalListener;
import de.ratzifatzi.expandworldborder.manager.AdvancementManager;
import de.ratzifatzi.expandworldborder.manager.AttemptManager;
import de.ratzifatzi.expandworldborder.manager.BorderManager;
import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import de.ratzifatzi.expandworldborder.manager.PlayerStatsManager;
import de.ratzifatzi.expandworldborder.manager.TimerManager;
import de.ratzifatzi.expandworldborder.manager.WorldManager;
import de.ratzifatzi.expandworldborder.persistence.DataStore;
import de.ratzifatzi.expandworldborder.persistence.PlayerDataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExpandWorldborderChallengePlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private DataStore dataStore;
    private PlayerDataStore playerDataStore;
    private TimerManager timerManager;
    private AttemptManager attemptManager;
    private AdvancementManager advancementManager;
    private PlayerStatsManager playerStatsManager;
    private WorldManager worldManager;
    private BorderManager borderManager;
    private ChallengeManager challengeManager;
    private GUIManager guiManager;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        pluginConfig = new PluginConfig(this);
        pluginConfig.reload();

        dataStore = new DataStore(this);
        dataStore.load();

        playerDataStore = new PlayerDataStore(this);
        playerDataStore.load();

        timerManager = new TimerManager(dataStore.getGlobalElapsedSeconds(), dataStore.getRunElapsedSeconds());
        attemptManager = new AttemptManager(dataStore.getAttemptCounter());
        advancementManager = new AdvancementManager(pluginConfig);
        advancementManager.restoreFromData(
                dataStore.getCompletedAdvancements(),
                dataStore.getFirstCompletedBy(),
                dataStore.getCompletedAt(),
                dataStore.getCompletedDisplayNames(),
                dataStore.getLastAdvancementKey()
        );
        playerStatsManager = new PlayerStatsManager(dataStore.getPlayerStatsSnapshots());
        worldManager = new WorldManager(pluginConfig, dataStore.getCurrentSeed());
        borderManager = new BorderManager(pluginConfig, worldManager, dataStore.getCurrentBorderSize(pluginConfig.getBorderStartSize()));

        ChallengeState stateFromFile = dataStore.getState();
        challengeManager = new ChallengeManager(
                this,
                pluginConfig,
                timerManager,
                attemptManager,
                advancementManager,
                playerStatsManager,
                worldManager,
                borderManager,
                playerDataStore,
                stateFromFile,
                dataStore.getScoreboardEnabled(pluginConfig.isScoreboardEnabledByDefault()),
                dataStore.getShowWorldSeed(pluginConfig.isShowWorldSeedByDefault())
        );
        guiManager = new GUIManager(this, pluginConfig, challengeManager);
        challengeManager.setGuiManager(guiManager);
        challengeManager.bootstrapFromStoredState();

        if (!registerCommand()) {
            return;
        }
        registerListeners();
        startAutosaveTask();

        getLogger().info("ExpandWorldborderChallenge enabled.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (challengeManager != null) {
            challengeManager.shutdown();
        }
        persistRuntimeData();
    }

    private boolean registerCommand() {
        PluginCommand command = getCommand("borderchallenge");
        if (command == null) {
            getLogger().severe("Command /borderchallenge is missing in plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        BorderChallengeCommand executor = new BorderChallengeCommand(pluginConfig, challengeManager, guiManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        return true;
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new AdvancementListener(challengeManager), this);
        getServer().getPluginManager().registerEvents(new DeathListener(challengeManager), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(challengeManager), this);
        getServer().getPluginManager().registerEvents(new PortalListener(challengeManager, worldManager), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
    }

    private void startAutosaveTask() {
        long intervalTicks = Math.max(1, pluginConfig.getAutosaveIntervalSeconds()) * 20L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, this::persistRuntimeData, intervalTicks, intervalTicks);
    }

    public void persistRuntimeData() {
        if (dataStore == null
                || timerManager == null
                || attemptManager == null
                || advancementManager == null
                || playerStatsManager == null
                || worldManager == null
                || borderManager == null
                || challengeManager == null
                || playerDataStore == null) {
            return;
        }
        dataStore.setState(challengeManager.getState());
        dataStore.setGlobalElapsedSeconds(timerManager.getGlobalElapsedSeconds());
        dataStore.setRunElapsedSeconds(timerManager.getRunElapsedSeconds());
        dataStore.setAttemptCounter(attemptManager.getAttemptCounter());
        dataStore.setCurrentSeed(worldManager.getCurrentSeed());
        dataStore.setCurrentBorderSize(borderManager.getCurrentSize());
        dataStore.setScoreboardEnabled(challengeManager.isScoreboardEnabled());
        dataStore.setShowWorldSeed(challengeManager.isWorldSeedVisible());
        dataStore.setCompletedAdvancements(advancementManager.getCompletedAdvancementKeys());
        dataStore.setFirstCompletedBy(advancementManager.getFirstCompletedByMap());
        dataStore.setCompletedAt(advancementManager.getCompletedAtMap());
        dataStore.setCompletedDisplayNames(advancementManager.getCompletedDisplayNames());
        dataStore.setLastAdvancementKey(advancementManager.getLastAdvancementKey());
        dataStore.setPlayerStatsSnapshots(playerStatsManager.snapshotStats());
        dataStore.save();

        playerDataStore.setPausedPlayerSnapshots(challengeManager.getPausedPlayerSnapshotsForStorage());
        playerDataStore.setActiveRunParticipants(challengeManager.getActiveRunParticipantsForStorage());
    }
}
