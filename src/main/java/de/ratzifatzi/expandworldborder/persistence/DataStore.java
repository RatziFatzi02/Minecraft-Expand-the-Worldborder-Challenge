package de.ratzifatzi.expandworldborder.persistence;

import de.ratzifatzi.expandworldborder.challenge.ChallengeState;
import de.ratzifatzi.expandworldborder.manager.PlayerStatsManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DataStore {

    private final JavaPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save data.yml: " + exception.getMessage());
        }
    }

    public ChallengeState getState() {
        String value = data.getString("state", ChallengeState.IDLE.name());
        try {
            return ChallengeState.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid challenge state in data.yml: " + value);
            return ChallengeState.IDLE;
        }
    }

    public void setState(ChallengeState state) {
        data.set("state", state.name());
    }

    public long getGlobalElapsedSeconds() {
        return Math.max(0L, data.getLong("global-elapsed-seconds", 0L));
    }

    public void setGlobalElapsedSeconds(long seconds) {
        data.set("global-elapsed-seconds", Math.max(0L, seconds));
    }

    public long getRunElapsedSeconds() {
        return Math.max(0L, data.getLong("run-elapsed-seconds", 0L));
    }

    public void setRunElapsedSeconds(long seconds) {
        data.set("run-elapsed-seconds", Math.max(0L, seconds));
    }

    public int getAttemptCounter() {
        return Math.max(0, data.getInt("attempt-counter", 0));
    }

    public void setAttemptCounter(int attemptCounter) {
        data.set("attempt-counter", Math.max(0, attemptCounter));
    }

    public long getCurrentSeed() {
        return data.getLong("current-seed", 0L);
    }

    public void setCurrentSeed(long seed) {
        data.set("current-seed", seed);
    }

    public double getCurrentBorderSize(double defaultValue) {
        return Math.max(1.0D, data.getDouble("current-border-size", defaultValue));
    }

    public void setCurrentBorderSize(double size) {
        data.set("current-border-size", Math.max(1.0D, size));
    }

    public boolean getScoreboardEnabled(boolean defaultValue) {
        return data.getBoolean("scoreboard-enabled", defaultValue);
    }

    public void setScoreboardEnabled(boolean enabled) {
        data.set("scoreboard-enabled", enabled);
    }

    public boolean getShowWorldSeed(boolean defaultValue) {
        return data.getBoolean("show-world-seed", defaultValue);
    }

    public void setShowWorldSeed(boolean enabled) {
        data.set("show-world-seed", enabled);
    }

    public String getLastAdvancementKey() {
        return data.getString("last-advancement-key", null);
    }

    public void setLastAdvancementKey(String key) {
        data.set("last-advancement-key", key);
    }

    public Set<String> getCompletedAdvancements() {
        return readStringSet("completed-advancements");
    }

    public void setCompletedAdvancements(Set<String> keys) {
        writeStringSet("completed-advancements", keys);
    }

    public Map<String, UUID> getFirstCompletedBy() {
        Map<String, UUID> result = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection("first-completed-by");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            String rawUuid = section.getString(key, "");
            try {
                result.put(key, UUID.fromString(rawUuid));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid UUID in first-completed-by for " + key + ": " + rawUuid);
            }
        }
        return result;
    }

    public void setFirstCompletedBy(Map<String, UUID> values) {
        data.set("first-completed-by", null);
        for (Map.Entry<String, UUID> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            data.set("first-completed-by." + escapeKey(entry.getKey()), entry.getValue().toString());
        }
    }

    public Map<String, Long> getCompletedAt() {
        Map<String, Long> result = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection("completed-at");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, Math.max(0L, section.getLong(key, 0L)));
        }
        return result;
    }

    public void setCompletedAt(Map<String, Long> values) {
        data.set("completed-at", null);
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            data.set("completed-at." + escapeKey(entry.getKey()), Math.max(0L, entry.getValue()));
        }
    }

    public Map<String, String> getCompletedDisplayNames() {
        Map<String, String> result = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection("completed-display-names");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, section.getString(key, key));
        }
        return result;
    }

    public void setCompletedDisplayNames(Map<String, String> values) {
        data.set("completed-display-names", null);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            data.set("completed-display-names." + escapeKey(entry.getKey()), entry.getValue());
        }
    }

    public Map<UUID, PlayerStatsManager.PlayerStatsSnapshot> getPlayerStatsSnapshots() {
        Map<UUID, PlayerStatsManager.PlayerStatsSnapshot> result = new HashMap<>();
        ConfigurationSection section = data.getConfigurationSection("player-stats");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid UUID in player-stats: " + key);
                continue;
            }
            String path = "player-stats." + key;
            result.put(uuid, new PlayerStatsManager.PlayerStatsSnapshot(
                    data.getString(path + ".last-name", "Unknown"),
                    Math.max(0, data.getInt(path + ".run-advancements", 0)),
                    Math.max(0, data.getInt(path + ".lifetime-advancements", 0)),
                    Math.max(0, data.getInt(path + ".run-deaths", 0)),
                    Math.max(0, data.getInt(path + ".lifetime-deaths", 0)),
                    readStringIntMap(path + ".run-death-reasons"),
                    readStringIntMap(path + ".lifetime-death-reasons")
            ));
        }
        return result;
    }

    public void setPlayerStatsSnapshots(Map<UUID, PlayerStatsManager.PlayerStatsSnapshot> snapshots) {
        data.set("player-stats", null);
        for (Map.Entry<UUID, PlayerStatsManager.PlayerStatsSnapshot> entry : snapshots.entrySet()) {
            String path = "player-stats." + entry.getKey();
            PlayerStatsManager.PlayerStatsSnapshot snapshot = entry.getValue();
            data.set(path + ".last-name", snapshot.lastKnownName());
            data.set(path + ".run-advancements", Math.max(0, snapshot.runAdvancements()));
            data.set(path + ".lifetime-advancements", Math.max(0, snapshot.lifetimeAdvancements()));
            data.set(path + ".run-deaths", Math.max(0, snapshot.runDeaths()));
            data.set(path + ".lifetime-deaths", Math.max(0, snapshot.lifetimeDeaths()));
            writeStringIntMap(path + ".run-death-reasons", snapshot.runDeathReasons());
            writeStringIntMap(path + ".lifetime-death-reasons", snapshot.lifetimeDeathReasons());
        }
    }

    private Set<String> readStringSet(String path) {
        return data.getStringList(path).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void writeStringSet(String path, Set<String> values) {
        List<String> sorted = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .toList();
        data.set(path, sorted);
    }

    private Map<String, Integer> readStringIntMap(String path) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            int value = Math.max(0, section.getInt(key, 0));
            if (value > 0) {
                result.put(key, value);
            }
        }
        return result;
    }

    private void writeStringIntMap(String path, Map<String, Integer> values) {
        data.set(path, null);
        if (values == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            data.set(path + "." + escapeKey(entry.getKey()), entry.getValue());
        }
    }

    private String escapeKey(String key) {
        return key.replace(".", "{dot}");
    }
}
