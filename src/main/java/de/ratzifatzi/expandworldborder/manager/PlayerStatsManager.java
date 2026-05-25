package de.ratzifatzi.expandworldborder.manager;

import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerStatsManager {

    private final Map<UUID, PlayerStats> statsByPlayer = new HashMap<>();

    public PlayerStatsManager(Map<UUID, PlayerStatsSnapshot> storedStats) {
        for (Map.Entry<UUID, PlayerStatsSnapshot> entry : storedStats.entrySet()) {
            PlayerStatsSnapshot snapshot = entry.getValue();
            PlayerStats stats = new PlayerStats(
                    sanitizeName(snapshot.lastKnownName()),
                    Math.max(0, snapshot.runAdvancements()),
                    Math.max(0, snapshot.lifetimeAdvancements()),
                    Math.max(0, snapshot.runDeaths()),
                    Math.max(0, snapshot.lifetimeDeaths()),
                    copyStringIntMap(snapshot.runDeathReasons()),
                    copyStringIntMap(snapshot.lifetimeDeathReasons())
            );
            statsByPlayer.put(entry.getKey(), stats);
        }
    }

    public void ensurePlayer(Player player) {
        getOrCreate(player.getUniqueId(), player.getName());
    }

    public void recordAdvancement(Player player) {
        PlayerStats stats = getOrCreate(player.getUniqueId(), player.getName());
        stats.runAdvancements += 1;
        stats.lifetimeAdvancements += 1;
    }

    public void recordDeath(Player player, String reason) {
        PlayerStats stats = getOrCreate(player.getUniqueId(), player.getName());
        stats.runDeaths += 1;
        stats.lifetimeDeaths += 1;
        String safeReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        addCount(stats.runDeathReasons, safeReason, 1);
        addCount(stats.lifetimeDeathReasons, safeReason, 1);
    }

    public void resetRunStats() {
        for (PlayerStats stats : statsByPlayer.values()) {
            stats.runAdvancements = 0;
            stats.runDeaths = 0;
            stats.runDeathReasons.clear();
        }
    }

    public Optional<LeaderboardEntry> getTopRunAdvancer() {
        return findTop(stats -> stats.runAdvancements);
    }

    public Optional<LeaderboardEntry> getTopLifetimeAdvancer() {
        return findTop(stats -> stats.lifetimeAdvancements);
    }

    public Optional<LeaderboardEntry> getTopLifetimeDeaths() {
        return findTop(stats -> stats.lifetimeDeaths);
    }

    public List<PlayerStatView> getTopRunAdvancementViews(int limit) {
        return buildViews(limit, Comparator.comparingInt(PlayerStatView::runAdvancements).reversed()
                .thenComparing(PlayerStatView::playerName));
    }

    public List<PlayerStatView> getTopLifetimeAdvancementViews(int limit) {
        return buildViews(limit, Comparator.comparingInt(PlayerStatView::lifetimeAdvancements).reversed()
                .thenComparing(PlayerStatView::playerName));
    }

    public List<PlayerStatView> getTopLifetimeDeathViews(int limit) {
        return buildViews(limit, Comparator.comparingInt(PlayerStatView::lifetimeDeaths).reversed()
                .thenComparing(PlayerStatView::playerName));
    }

    public int getCurrentRunAdvancementTotal() {
        return statsByPlayer.values().stream().mapToInt(stats -> stats.runAdvancements).sum();
    }

    public int getLifetimeAdvancementTotal() {
        return statsByPlayer.values().stream().mapToInt(stats -> stats.lifetimeAdvancements).sum();
    }

    public int getCurrentRunDeathsTotal() {
        return statsByPlayer.values().stream().mapToInt(stats -> stats.runDeaths).sum();
    }

    public int getLifetimeDeathsTotal() {
        return statsByPlayer.values().stream().mapToInt(stats -> stats.lifetimeDeaths).sum();
    }

    public int getTrackedPlayerCount() {
        return statsByPlayer.size();
    }

    public Map<UUID, PlayerStatsSnapshot> snapshotStats() {
        Map<UUID, PlayerStatsSnapshot> result = new HashMap<>();
        for (Map.Entry<UUID, PlayerStats> entry : statsByPlayer.entrySet()) {
            PlayerStats stats = entry.getValue();
            result.put(entry.getKey(), new PlayerStatsSnapshot(
                    stats.lastKnownName,
                    stats.runAdvancements,
                    stats.lifetimeAdvancements,
                    stats.runDeaths,
                    stats.lifetimeDeaths,
                    copyStringIntMap(stats.runDeathReasons),
                    copyStringIntMap(stats.lifetimeDeathReasons)
            ));
        }
        return result;
    }

    public Map<String, Integer> getLifetimeDeathReasons() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (PlayerStats stats : statsByPlayer.values()) {
            merge(result, stats.lifetimeDeathReasons);
        }
        return result;
    }

    private List<PlayerStatView> buildViews(int limit, Comparator<PlayerStatView> comparator) {
        if (limit <= 0) {
            return List.of();
        }
        return statsByPlayer.values().stream()
                .map(stats -> new PlayerStatView(
                        stats.lastKnownName,
                        stats.runAdvancements,
                        stats.lifetimeAdvancements,
                        stats.runDeaths,
                        stats.lifetimeDeaths,
                        topReason(stats.lifetimeDeathReasons)
                ))
                .sorted(comparator)
                .limit(limit)
                .toList();
    }

    private Optional<LeaderboardEntry> findTop(StatExtractor extractor) {
        return statsByPlayer.values().stream()
                .max(Comparator.comparingInt(extractor::extract))
                .filter(stats -> extractor.extract(stats) > 0)
                .map(stats -> new LeaderboardEntry(stats.lastKnownName, extractor.extract(stats)));
    }

    private PlayerStats getOrCreate(UUID uuid, String playerName) {
        PlayerStats existing = statsByPlayer.get(uuid);
        if (existing != null) {
            existing.lastKnownName = sanitizeName(playerName);
            return existing;
        }
        PlayerStats created = new PlayerStats(sanitizeName(playerName), 0, 0, 0, 0, new LinkedHashMap<>(), new LinkedHashMap<>());
        statsByPlayer.put(uuid, created);
        return created;
    }

    private void addCount(Map<String, Integer> target, String key, int amount) {
        target.put(key, target.getOrDefault(key, 0) + Math.max(0, amount));
    }

    private void merge(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            addCount(target, entry.getKey(), entry.getValue());
        }
    }

    private String topReason(Map<String, Integer> reasons) {
        return reasons.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No data");
    }

    private Map<String, Integer> copyStringIntMap(Map<String, Integer> source) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private String sanitizeName(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    @FunctionalInterface
    private interface StatExtractor {
        int extract(PlayerStats stats);
    }

    private static final class PlayerStats {
        private String lastKnownName;
        private int runAdvancements;
        private int lifetimeAdvancements;
        private int runDeaths;
        private int lifetimeDeaths;
        private final Map<String, Integer> runDeathReasons;
        private final Map<String, Integer> lifetimeDeathReasons;

        private PlayerStats(String lastKnownName, int runAdvancements, int lifetimeAdvancements, int runDeaths, int lifetimeDeaths,
                            Map<String, Integer> runDeathReasons, Map<String, Integer> lifetimeDeathReasons) {
            this.lastKnownName = lastKnownName;
            this.runAdvancements = runAdvancements;
            this.lifetimeAdvancements = lifetimeAdvancements;
            this.runDeaths = runDeaths;
            this.lifetimeDeaths = lifetimeDeaths;
            this.runDeathReasons = runDeathReasons;
            this.lifetimeDeathReasons = lifetimeDeathReasons;
        }
    }

    public record LeaderboardEntry(String playerName, int value) {
    }

    public record PlayerStatsSnapshot(
            String lastKnownName,
            int runAdvancements,
            int lifetimeAdvancements,
            int runDeaths,
            int lifetimeDeaths,
            Map<String, Integer> runDeathReasons,
            Map<String, Integer> lifetimeDeathReasons
    ) {
    }

    public record PlayerStatView(
            String playerName,
            int runAdvancements,
            int lifetimeAdvancements,
            int runDeaths,
            int lifetimeDeaths,
            String topLifetimeDeathReason
    ) {
    }
}
