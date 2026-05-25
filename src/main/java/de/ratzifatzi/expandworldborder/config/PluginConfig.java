package de.ratzifatzi.expandworldborder.config;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PluginConfig {

    private final JavaPlugin plugin;

    private boolean debugMode;
    private int autosaveIntervalSeconds;
    private int transitionToLobbySeconds;
    private int transitionLobbyWaitSeconds;
    private int transitionLobbyTitleLastSeconds;
    private int autoPauseDelaySeconds;
    private boolean joinControlEnabled;
    private boolean randomSeedEnabled;
    private long fixedSeed;
    private boolean showWorldSeedByDefault;
    private String lobbyWorldName;
    private String runWorldName;
    private SpawnPoint lobbySpawn;
    private RunSpawnPoint runSpawn;
    private double borderStartSize;
    private double borderGrowthPerAdvancement;
    private double borderMaxSize;
    private int borderAnimationSeconds;
    private double borderCenterX;
    private double borderCenterZ;
    private boolean borderOverworldEnabled;
    private boolean borderNetherEnabled;
    private boolean borderEndEnabled;
    private boolean includeHiddenAdvancements;
    private boolean includeTechnicalAdvancements;
    private boolean includeRecipeAdvancements;
    private Set<String> allowedNamespaces;
    private Set<String> blockedNamespaces;
    private boolean scoreboardEnabledByDefault;
    private boolean feedbackSoundEnabled;
    private Sound feedbackSound;
    private boolean feedbackActionbarEnabled;
    private boolean feedbackChatEnabled;
    private String prefix;
    private final Map<String, String> messages = new HashMap<>();

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        debugMode = cfg.getBoolean("debug-mode", false);
        autosaveIntervalSeconds = Math.max(1, cfg.getInt("autosave-interval-seconds", 10));
        transitionToLobbySeconds = Math.max(0, cfg.getInt("transitions.to-lobby-seconds", 5));
        transitionLobbyWaitSeconds = Math.max(0, cfg.getInt("transitions.lobby-wait-seconds", 10));
        transitionLobbyTitleLastSeconds = Math.max(0, cfg.getInt("transitions.lobby-title-last-seconds", 5));
        autoPauseDelaySeconds = Math.max(0, cfg.getInt("pause.auto-pause-delay-seconds", 5));
        joinControlEnabled = cfg.getBoolean("join-control.enabled", true);

        randomSeedEnabled = cfg.getBoolean("seed.randomize", true);
        fixedSeed = cfg.getLong("seed.fixed-seed", 0L);
        showWorldSeedByDefault = cfg.getBoolean("seed.show-world-seed", false);

        lobbyWorldName = cfg.getString("lobby.world-name", "challenge_lobby");
        runWorldName = cfg.getString("run.world-name", "challenge_run");
        lobbySpawn = new SpawnPoint(
                cfg.getDouble("lobby.spawn.x", 0.5D),
                cfg.getDouble("lobby.spawn.y", 80.0D),
                cfg.getDouble("lobby.spawn.z", 0.5D),
                (float) cfg.getDouble("lobby.spawn.yaw", 0.0D),
                (float) cfg.getDouble("lobby.spawn.pitch", 0.0D)
        );
        runSpawn = new RunSpawnPoint(
                cfg.getDouble("run.spawn.x", 0.5D),
                cfg.getDouble("run.spawn.z", 0.5D),
                (float) cfg.getDouble("run.spawn.yaw", 0.0D),
                (float) cfg.getDouble("run.spawn.pitch", 0.0D)
        );

        borderStartSize = clampBorderSize(cfg.getDouble("border.start-size", 128.0D));
        borderGrowthPerAdvancement = Math.max(0.0D, cfg.getDouble("border.growth-per-advancement", 128.0D));
        borderMaxSize = clampBorderSize(cfg.getDouble("border.max-size", 60000000.0D));
        if (borderMaxSize < borderStartSize) {
            borderMaxSize = borderStartSize;
        }
        borderAnimationSeconds = Math.max(0, cfg.getInt("border.animation-seconds", 3));
        borderCenterX = cfg.getDouble("border.center.x", 0.5D);
        borderCenterZ = cfg.getDouble("border.center.z", 0.5D);
        borderOverworldEnabled = cfg.getBoolean("border.apply-to.overworld", true);
        borderNetherEnabled = cfg.getBoolean("border.apply-to.nether", true);
        borderEndEnabled = cfg.getBoolean("border.apply-to.end", true);

        includeHiddenAdvancements = cfg.getBoolean("advancements.include-hidden", false);
        includeTechnicalAdvancements = cfg.getBoolean("advancements.include-technical", false);
        includeRecipeAdvancements = cfg.getBoolean("advancements.include-recipes", false);
        allowedNamespaces = parseNamespaceSet(cfg.getStringList("advancements.allowed-namespaces"));
        blockedNamespaces = parseNamespaceSet(cfg.getStringList("advancements.blocked-namespaces"));

        scoreboardEnabledByDefault = cfg.getBoolean("scoreboard.enabled", true);
        feedbackSoundEnabled = cfg.getBoolean("feedback.sound.enabled", true);
        feedbackSound = parseSound(cfg.getString("feedback.sound.type", "ENTITY_PLAYER_LEVELUP"));
        feedbackActionbarEnabled = cfg.getBoolean("feedback.actionbar.enabled", true);
        feedbackChatEnabled = cfg.getBoolean("feedback.chat.enabled", true);

        prefix = cfg.getString("messages.prefix", "&6[Worldborder]&r ");
        messages.clear();
        ConfigurationSection section = cfg.getConfigurationSection("messages");
        if (section != null) {
            loadMessages(section, "");
        }
    }

    private Set<String> parseNamespaceSet(java.util.List<String> rawValues) {
        Set<String> result = new HashSet<>();
        for (String value : rawValues) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    private void loadMessages(ConfigurationSection section, String prefixPath) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefixPath.isEmpty() ? key : prefixPath + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    loadMessages(child, fullKey);
                }
                continue;
            }
            messages.put(fullKey, section.getString(key, ""));
        }
    }

    private Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown sound in feedback.sound.type: " + raw);
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    private double clampBorderSize(double value) {
        return Math.max(1.0D, Math.min(60000000.0D, value));
    }

    public String prefixed(String text) {
        return colorize(prefix + text);
    }

    public String message(String key, String fallback, Map<String, String> placeholders) {
        return prefixed(rawMessage(key, fallback, placeholders));
    }

    public String rawMessage(String key, String fallback, Map<String, String> placeholders) {
        String text = messages.getOrDefault(key, fallback);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return colorize(text);
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getAutosaveIntervalSeconds() {
        return autosaveIntervalSeconds;
    }

    public int getTransitionToLobbySeconds() {
        return transitionToLobbySeconds;
    }

    public int getTransitionLobbyWaitSeconds() {
        return transitionLobbyWaitSeconds;
    }

    public int getTransitionLobbyTitleLastSeconds() {
        return transitionLobbyTitleLastSeconds;
    }

    public int getAutoPauseDelaySeconds() {
        return autoPauseDelaySeconds;
    }

    public boolean isJoinControlEnabled() {
        return joinControlEnabled;
    }

    public boolean isRandomSeedEnabled() {
        return randomSeedEnabled;
    }

    public long getFixedSeed() {
        return fixedSeed;
    }

    public boolean isShowWorldSeedByDefault() {
        return showWorldSeedByDefault;
    }

    public String getLobbyWorldName() {
        return lobbyWorldName;
    }

    public String getRunWorldName() {
        return runWorldName;
    }

    public SpawnPoint getLobbySpawn() {
        return lobbySpawn;
    }

    public RunSpawnPoint getRunSpawn() {
        return runSpawn;
    }

    public double getBorderStartSize() {
        return borderStartSize;
    }

    public double getBorderGrowthPerAdvancement() {
        return borderGrowthPerAdvancement;
    }

    public double getBorderMaxSize() {
        return borderMaxSize;
    }

    public int getBorderAnimationSeconds() {
        return borderAnimationSeconds;
    }

    public double getBorderCenterX() {
        return borderCenterX;
    }

    public double getBorderCenterZ() {
        return borderCenterZ;
    }

    public boolean isBorderEnabledFor(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> borderOverworldEnabled;
            case NETHER -> borderNetherEnabled;
            case THE_END -> borderEndEnabled;
            default -> false;
        };
    }

    public boolean isIncludeHiddenAdvancements() {
        return includeHiddenAdvancements;
    }

    public boolean isIncludeTechnicalAdvancements() {
        return includeTechnicalAdvancements;
    }

    public boolean isIncludeRecipeAdvancements() {
        return includeRecipeAdvancements;
    }

    public Set<String> getAllowedNamespaces() {
        return allowedNamespaces;
    }

    public Set<String> getBlockedNamespaces() {
        return blockedNamespaces;
    }

    public boolean isScoreboardEnabledByDefault() {
        return scoreboardEnabledByDefault;
    }

    public boolean isFeedbackSoundEnabled() {
        return feedbackSoundEnabled;
    }

    public Sound getFeedbackSound() {
        return feedbackSound;
    }

    public boolean isFeedbackActionbarEnabled() {
        return feedbackActionbarEnabled;
    }

    public boolean isFeedbackChatEnabled() {
        return feedbackChatEnabled;
    }

    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
        public Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public record RunSpawnPoint(double x, double z, float yaw, float pitch) {
    }
}
