package de.ratzifatzi.expandworldborder.manager;

import de.ratzifatzi.expandworldborder.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AdvancementManager {

    private final PluginConfig config;
    private final Map<String, AdvancementView> allAdvancements = new LinkedHashMap<>();
    private final Set<String> completedAdvancements = new LinkedHashSet<>();
    private final Map<String, UUID> firstCompletedBy = new HashMap<>();
    private final Map<String, Long> completedAt = new HashMap<>();
    private final Map<String, String> completedDisplayNames = new HashMap<>();
    private String lastAdvancementKey;

    public AdvancementManager(PluginConfig config) {
        this.config = config;
        reloadPool();
    }

    public void reloadPool() {
        allAdvancements.clear();
        var iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            Optional<AdvancementView> view = toViewIfTrackable(advancement);
            view.ifPresent(value -> allAdvancements.put(value.key(), value));
        }
        sanitizeStoredState();
    }

    public void restoreFromData(
            Set<String> completed,
            Map<String, UUID> firstBy,
            Map<String, Long> completedAtInput,
            Map<String, String> displayNames,
            String lastKey
    ) {
        completedAdvancements.clear();
        completedAdvancements.addAll(completed);
        firstCompletedBy.clear();
        firstCompletedBy.putAll(firstBy);
        completedAt.clear();
        completedAt.putAll(completedAtInput);
        completedDisplayNames.clear();
        completedDisplayNames.putAll(displayNames);
        lastAdvancementKey = lastKey;
        sanitizeStoredState();
    }

    public void startNewRun() {
        completedAdvancements.clear();
        firstCompletedBy.clear();
        completedAt.clear();
        completedDisplayNames.clear();
        lastAdvancementKey = null;
        reloadPool();
    }

    public Optional<CompletionResult> tryComplete(Advancement advancement, Player player) {
        AdvancementView view = toViewIfTrackable(advancement).orElse(null);
        if (view == null) {
            return Optional.empty();
        }
        allAdvancements.putIfAbsent(view.key(), view);
        if (!completedAdvancements.add(view.key())) {
            return Optional.empty();
        }
        UUID playerId = player == null ? null : player.getUniqueId();
        if (playerId != null) {
            firstCompletedBy.putIfAbsent(view.key(), playerId);
        }
        completedAt.put(view.key(), Instant.now().toEpochMilli());
        completedDisplayNames.put(view.key(), view.displayName());
        lastAdvancementKey = view.key();
        return Optional.of(new CompletionResult(view, playerId));
    }

    public int getCompletedCount() {
        return completedAdvancements.size();
    }

    public int getTotalCount() {
        return allAdvancements.size();
    }

    public int getMissingCount() {
        return Math.max(0, getTotalCount() - getCompletedCount());
    }

    public double getProgressPercent() {
        if (allAdvancements.isEmpty()) {
            return 0.0D;
        }
        return (completedAdvancements.size() * 100.0D) / allAdvancements.size();
    }

    public List<AdvancementView> getCompletedViews() {
        return allAdvancements.values().stream()
                .filter(view -> completedAdvancements.contains(view.key()))
                .sorted(Comparator.comparing(AdvancementView::sortName))
                .toList();
    }

    public List<AdvancementView> getMissingViews() {
        return allAdvancements.values().stream()
                .filter(view -> !completedAdvancements.contains(view.key()))
                .sorted(Comparator.comparing(AdvancementView::sortName))
                .toList();
    }

    public List<AdvancementView> getAllViews() {
        return allAdvancements.values().stream()
                .sorted(Comparator.comparing(AdvancementView::sortName))
                .toList();
    }

    public Set<String> getCompletedAdvancementKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(completedAdvancements));
    }

    public Map<String, UUID> getFirstCompletedByMap() {
        return Collections.unmodifiableMap(new HashMap<>(firstCompletedBy));
    }

    public Map<String, Long> getCompletedAtMap() {
        return Collections.unmodifiableMap(new HashMap<>(completedAt));
    }

    public Map<String, String> getCompletedDisplayNames() {
        return Collections.unmodifiableMap(new HashMap<>(completedDisplayNames));
    }

    public String getLastAdvancementKey() {
        return lastAdvancementKey;
    }

    public Optional<AdvancementView> getLastAdvancementView() {
        if (lastAdvancementKey == null) {
            return Optional.empty();
        }
        AdvancementView view = allAdvancements.get(lastAdvancementKey);
        if (view != null) {
            return Optional.of(view);
        }
        String displayName = completedDisplayNames.getOrDefault(lastAdvancementKey, prettifyKey(lastAdvancementKey));
        return Optional.of(new AdvancementView(lastAdvancementKey, displayName, "unknown", Material.KNOWLEDGE_BOOK, false));
    }

    public Optional<String> getFirstCompleterName(String key) {
        UUID uuid = firstCompletedBy.get(key);
        if (uuid == null) {
            return Optional.empty();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return Optional.of(name == null || name.isBlank() ? "Unknown" : name);
    }

    public boolean isCompleted(String key) {
        return completedAdvancements.contains(key);
    }

    private Optional<AdvancementView> toViewIfTrackable(Advancement advancement) {
        NamespacedKey key = advancement.getKey();
        String namespace = key.getNamespace().toLowerCase(Locale.ROOT);
        String path = key.getKey().toLowerCase(Locale.ROOT);
        if (!config.getAllowedNamespaces().isEmpty() && !config.getAllowedNamespaces().contains(namespace)) {
            return Optional.empty();
        }
        if (config.getBlockedNamespaces().contains(namespace)) {
            return Optional.empty();
        }
        if (!config.isIncludeRecipeAdvancements() && (path.startsWith("recipes/") || path.contains("/recipes/"))) {
            return Optional.empty();
        }
        if (!config.isIncludeTechnicalAdvancements() && isTechnicalPath(path)) {
            return Optional.empty();
        }

        Object display = getDisplayObject(advancement);
        if (display == null && !config.isIncludeTechnicalAdvancements()) {
            return Optional.empty();
        }
        boolean hidden = display != null && readBoolean(display, "isHidden", "hidden").orElse(false);
        if (hidden && !config.isIncludeHiddenAdvancements()) {
            return Optional.empty();
        }

        String displayName = display == null
                ? prettifyKey(key.asString())
                : readComponentPlainText(display, "title", "getTitle").orElse(prettifyKey(key.asString()));
        Material icon = display == null
                ? Material.KNOWLEDGE_BOOK
                : readIconMaterial(display).orElse(Material.KNOWLEDGE_BOOK);
        return Optional.of(new AdvancementView(key.asString(), displayName, namespace, icon, hidden));
    }

    private boolean isTechnicalPath(String path) {
        return path.startsWith("technical/")
                || path.contains("/technical/");
    }

    private Object getDisplayObject(Advancement advancement) {
        try {
            Method method = advancement.getClass().getMethod("getDisplay");
            return method.invoke(advancement);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Optional<Boolean> readBoolean(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Boolean bool) {
                    return Optional.of(bool);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<String> readComponentPlainText(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof Component component) {
                    return Optional.of(PlainTextComponentSerializer.plainText().serialize(component));
                }
                if (value != null) {
                    return Optional.of(value.toString());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<Material> readIconMaterial(Object target) {
        for (String methodName : List.of("icon", "getIcon")) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value instanceof ItemStack stack) {
                    return Optional.of(stack.getType());
                }
                if (value instanceof Material material) {
                    return Optional.of(material);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private void sanitizeStoredState() {
        completedAdvancements.removeIf(key -> key == null || key.isBlank());
        firstCompletedBy.keySet().removeIf(key -> !completedAdvancements.contains(key));
        completedAt.keySet().removeIf(key -> !completedAdvancements.contains(key));
        completedDisplayNames.keySet().removeIf(key -> !completedAdvancements.contains(key));
    }

    private String prettifyKey(String key) {
        String raw = key;
        int slash = raw.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < raw.length()) {
            raw = raw.substring(slash + 1);
        }
        int colon = raw.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < raw.length()) {
            raw = raw.substring(colon + 1);
        }
        raw = raw.replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : raw.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? key : builder.toString();
    }

    public record CompletionResult(AdvancementView view, UUID playerId) {
    }

    public record AdvancementView(String key, String displayName, String namespace, Material icon, boolean hidden) {
        public String sortName() {
            return namespace + ":" + displayName.toLowerCase(Locale.ROOT) + ":" + key;
        }
    }
}
