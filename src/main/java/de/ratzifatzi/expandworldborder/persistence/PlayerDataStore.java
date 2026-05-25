package de.ratzifatzi.expandworldborder.persistence;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerDataStore {

    public record PausedPlayerSnapshotData(
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

    private final JavaPlugin plugin;
    private final File playerDataFolder;

    public PlayerDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void load() {
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create playerdata folder: " + playerDataFolder.getAbsolutePath());
        }
    }

    public Map<UUID, PausedPlayerSnapshotData> getPausedPlayerSnapshots() {
        Map<UUID, PausedPlayerSnapshotData> result = new HashMap<>();
        for (UUID playerId : listKnownPlayerIds()) {
            PausedPlayerSnapshotData snapshot = getPausedPlayerSnapshot(playerId);
            if (snapshot != null) {
                result.put(playerId, snapshot);
            }
        }
        return result;
    }

    public void setPausedPlayerSnapshots(Map<UUID, PausedPlayerSnapshotData> snapshots) {
        Set<UUID> known = listKnownPlayerIds();
        known.addAll(snapshots.keySet());
        for (UUID playerId : known) {
            setPausedPlayerSnapshot(playerId, snapshots.get(playerId));
        }
    }

    public Set<UUID> getActiveRunParticipants() {
        Set<UUID> result = new HashSet<>();
        for (UUID playerId : listKnownPlayerIds()) {
            if (loadConfig(playerId).getBoolean("current-run.participant", false)) {
                result.add(playerId);
            }
        }
        return result;
    }

    public void setActiveRunParticipants(Set<UUID> participants) {
        Set<UUID> known = listKnownPlayerIds();
        known.addAll(participants);
        for (UUID playerId : known) {
            FileConfiguration config = loadConfig(playerId);
            config.set("current-run.participant", participants.contains(playerId));
            saveConfig(playerId, config);
        }
    }

    public void setLastKnownName(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
        FileConfiguration config = loadConfig(playerId);
        config.set("last-known-name", playerName);
        saveConfig(playerId, config);
    }

    private PausedPlayerSnapshotData getPausedPlayerSnapshot(UUID playerId) {
        FileConfiguration config = loadConfig(playerId);
        if (!config.isConfigurationSection("pause-snapshot")) {
            return null;
        }
        String path = "pause-snapshot";
        return new PausedPlayerSnapshotData(
                config.getString(path + ".world-name", null),
                config.getDouble(path + ".location.x", 0.5D),
                config.getDouble(path + ".location.y", 80.0D),
                config.getDouble(path + ".location.z", 0.5D),
                (float) config.getDouble(path + ".location.yaw", 0.0D),
                (float) config.getDouble(path + ".location.pitch", 0.0D),
                readItemStackArray(config, path + ".inventory.contents"),
                readItemStackArray(config, path + ".inventory.armor"),
                readItemStackArray(config, path + ".inventory.extra"),
                config.getItemStack(path + ".inventory.offhand"),
                (float) config.getDouble(path + ".exp", 0.0D),
                Math.max(0, config.getInt(path + ".level", 0)),
                Math.max(0, config.getInt(path + ".total-experience", 0))
        );
    }

    private void setPausedPlayerSnapshot(UUID playerId, PausedPlayerSnapshotData snapshot) {
        FileConfiguration config = loadConfig(playerId);
        if (snapshot == null) {
            config.set("pause-snapshot", null);
            saveConfig(playerId, config);
            return;
        }
        String path = "pause-snapshot";
        config.set(path + ".world-name", snapshot.worldName());
        config.set(path + ".location.x", snapshot.x());
        config.set(path + ".location.y", snapshot.y());
        config.set(path + ".location.z", snapshot.z());
        config.set(path + ".location.yaw", snapshot.yaw());
        config.set(path + ".location.pitch", snapshot.pitch());
        config.set(path + ".inventory.contents", snapshot.contents());
        config.set(path + ".inventory.armor", snapshot.armor());
        config.set(path + ".inventory.extra", snapshot.extra());
        config.set(path + ".inventory.offhand", snapshot.offhand());
        config.set(path + ".exp", snapshot.exp());
        config.set(path + ".level", snapshot.level());
        config.set(path + ".total-experience", snapshot.totalExperience());
        saveConfig(playerId, config);
    }

    private Set<UUID> listKnownPlayerIds() {
        Set<UUID> result = new HashSet<>();
        File[] files = playerDataFolder.listFiles((ignored, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            String raw = file.getName().substring(0, file.getName().length() - ".yml".length());
            try {
                result.add(UUID.fromString(raw));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid playerdata file name: " + file.getName());
            }
        }
        return result;
    }

    private File playerFile(UUID playerId) {
        return new File(playerDataFolder, playerId + ".yml");
    }

    private FileConfiguration loadConfig(UUID playerId) {
        return YamlConfiguration.loadConfiguration(playerFile(playerId));
    }

    private void saveConfig(UUID playerId, FileConfiguration config) {
        try {
            config.save(playerFile(playerId));
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save playerdata for " + playerId + ": " + exception.getMessage());
        }
    }

    private ItemStack[] readItemStackArray(FileConfiguration config, String path) {
        List<?> raw = config.getList(path);
        if (raw == null || raw.isEmpty()) {
            return new ItemStack[0];
        }
        ItemStack[] result = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i) instanceof ItemStack itemStack) {
                result[i] = itemStack;
            }
        }
        return result;
    }
}
