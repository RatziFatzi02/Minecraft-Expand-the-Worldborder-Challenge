package de.ratzifatzi.expandworldborder.manager;

import de.ratzifatzi.expandworldborder.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class WorldManager {

    private static final String STASIS_WORLD_BASE_NAME = "challenge_run_world_stasis";
    private static final Set<Material> HAZARDOUS_BLOCKS = EnumSet.of(
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE,
            Material.COBWEB,
            Material.POWDER_SNOW,
            Material.POINTED_DRIPSTONE,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE
    );

    private final PluginConfig config;
    private long currentSeed;

    public WorldManager(PluginConfig config, long currentSeed) {
        this.config = config;
        this.currentSeed = currentSeed;
    }

    public World ensureLobbyWorld() {
        World world = Bukkit.getWorld(config.getLobbyWorldName());
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(config.getLobbyWorldName()));
        }
        if (world == null) {
            throw new IllegalStateException("Could not create/load lobby world: " + config.getLobbyWorldName());
        }
        applyServerDifficulty(world);
        return world;
    }

    public World ensureRunWorld() {
        return ensureRunWorldForEnvironment(World.Environment.NORMAL);
    }

    public World ensureRunNetherWorld() {
        return ensureRunWorldForEnvironment(World.Environment.NETHER);
    }

    public World ensureRunEndWorld() {
        return ensureRunWorldForEnvironment(World.Environment.THE_END);
    }

    public World resolveRunWorldForEnvironment(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> ensureRunWorld();
            case NETHER -> ensureRunNetherWorld();
            case THE_END -> ensureRunEndWorld();
            default -> ensureRunWorld();
        };
    }

    private World ensureRunWorldForEnvironment(World.Environment environment) {
        String worldName = getRunWorldName(environment);
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (environment == World.Environment.NORMAL) {
                currentSeed = world.getSeed();
            }
            applyServerDifficulty(world);
            return world;
        }

        WorldCreator creator = new WorldCreator(worldName).environment(environment);
        if (!worldFolderExists(worldName) && currentSeed != 0L) {
            creator.seed(currentSeed);
        }
        world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Could not create/load run world: " + worldName);
        }
        if (environment == World.Environment.NORMAL) {
            currentSeed = world.getSeed();
        }
        applyServerDifficulty(world);
        return world;
    }

    public boolean isRunWorldFamily(World world) {
        return world != null && isRunWorldFamily(world.getName());
    }

    public boolean isRunWorldFamily(String worldName) {
        if (worldName == null) {
            return false;
        }
        return getRunWorldNames().stream().anyMatch(name -> name.equalsIgnoreCase(worldName));
    }

    public List<World> getLoadedRunWorlds() {
        List<World> worlds = new ArrayList<>();
        for (String worldName : getRunWorldNames()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    public void ensureRunWorldFamily() {
        ensureRunWorld();
        ensureRunNetherWorld();
        ensureRunEndWorld();
    }

    public void teleportAllPlayersToLobby() {
        Location target = getLobbySpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportPlayerToLobby(player, target);
        }
    }

    public void teleportAllPlayersToRunWorld() {
        Location target = getRunSpawnLocation();
        teleportAllPlayersToRunWorld(target);
    }

    public void teleportAllPlayersToRunWorld(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportPlayerToRunWorld(player, target);
        }
    }

    public void teleportPlayerToLobby(Player player) {
        teleportPlayerToLobby(player, getLobbySpawnLocation());
    }

    public void teleportPlayerToRunWorld(Player player) {
        teleportPlayerToRunWorld(player, getRunSpawnLocation());
    }

    public Location getLobbySpawnLocation() {
        return config.getLobbySpawn().toLocation(ensureLobbyWorld());
    }

    public Location getRunSpawnLocation() {
        World world = ensureRunWorld();
        Location vanillaSpawn = world.getSpawnLocation();
        Location safeVanillaSpawn = findSafeNear(world, vanillaSpawn.getBlockX(), vanillaSpawn.getBlockZ(), config.getRunSpawn().yaw(), config.getRunSpawn().pitch(), 32);
        if (safeVanillaSpawn != null) {
            return safeVanillaSpawn;
        }
        return findSafeNear(world, (int) Math.floor(config.getRunSpawn().x()), (int) Math.floor(config.getRunSpawn().z()), config.getRunSpawn().yaw(), config.getRunSpawn().pitch(), 48);
    }

    private void teleportPlayerToLobby(Player player, Location target) {
        cleanupTransferState(player);
        player.teleport(target);
    }

    private void teleportPlayerToRunWorld(Player player, Location target) {
        cleanupTransferState(player);
        player.teleport(target);
    }

    private void cleanupTransferState(Player player) {
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        if (!player.isDead()) {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(maxHealth.getValue());
            }
        }
    }

    private Location findSafeNear(World world, int centerX, int centerZ, float yaw, float pitch, int maxRadius) {
        for (int radius = 0; radius <= maxRadius; radius++) {
            int minX = centerX - radius;
            int maxX = centerX + radius;
            int minZ = centerZ - radius;
            int maxZ = centerZ + radius;
            for (int x = minX; x <= maxX; x++) {
                Location top = findSafeInColumn(world, x, minZ, yaw, pitch);
                if (top != null) {
                    return top;
                }
                Location bottom = findSafeInColumn(world, x, maxZ, yaw, pitch);
                if (bottom != null) {
                    return bottom;
                }
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                Location left = findSafeInColumn(world, minX, z, yaw, pitch);
                if (left != null) {
                    return left;
                }
                Location right = findSafeInColumn(world, maxX, z, yaw, pitch);
                if (right != null) {
                    return right;
                }
            }
        }
        int y = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(centerX, centerZ, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        return new Location(world, centerX + 0.5D, y, centerZ + 0.5D, yaw, pitch);
    }

    private Location findSafeInColumn(World world, int x, int z, float yaw, float pitch) {
        int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int startY = Math.min(world.getMaxHeight() - 2, surfaceY + 1);
        int minY = Math.max(world.getMinHeight() + 1, startY - 12);
        for (int y = startY; y >= minY; y--) {
            if (isSafeStandingSpot(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D, yaw, pitch);
            }
        }
        return null;
    }

    private boolean isSafeStandingSpot(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = feet.getRelative(BlockFace.UP);
        Block floor = feet.getRelative(BlockFace.DOWN);
        return isSafeFloor(floor) && isSafeBodySpace(feet) && isSafeBodySpace(head);
    }

    private boolean isSafeFloor(Block block) {
        Material type = block.getType();
        if (!type.isSolid() || block.isLiquid() || Tag.LEAVES.isTagged(type)) {
            return false;
        }
        return !isHazardous(type);
    }

    private boolean isSafeBodySpace(Block block) {
        Material type = block.getType();
        return block.isPassable() && !block.isLiquid() && !isHazardous(type);
    }

    private boolean isHazardous(Material type) {
        return HAZARDOUS_BLOCKS.contains(type) || Tag.FIRE.isTagged(type) || type.name().contains("LAVA") || type.name().contains("WATER");
    }

    public long pickNextSeed() {
        return config.isRandomSeedEnabled() ? ThreadLocalRandom.current().nextLong() : config.getFixedSeed();
    }

    public void regenerateRunWorld(long seed) throws IOException {
        Location lobbySpawn = getLobbySpawnLocation();
        unloadWorldFamily(getRunWorldNames(), lobbySpawn);
        unloadWorldFamily(getStasisWorldNames(), null);
        deleteWorldFamilyFolders(getStasisWorldNames());
        moveRunWorldFamilyToStasis();
        deleteWorldFamilyFolders(getRunWorldNames());

        currentSeed = seed;
        World created = Bukkit.createWorld(new WorldCreator(config.getRunWorldName()).seed(seed));
        if (created == null) {
            throw new IOException("Run world could not be regenerated: " + config.getRunWorldName());
        }
        currentSeed = created.getSeed();
        applyServerDifficulty(created);
        ensureRunNetherWorld();
        ensureRunEndWorld();
    }

    public long getCurrentSeed() {
        return currentSeed;
    }

    private boolean worldFolderExists(String worldName) {
        return Files.isDirectory(resolveWorldFolder(worldName));
    }

    private Path resolveWorldFolder(String worldName) {
        Path worldContainer = Bukkit.getWorldContainer().toPath();
        Path legacyWorldFolder = worldContainer.resolve(worldName);
        Path modernDimensionFolder = resolveModernDimensionFolder(worldContainer, worldName);

        if (Files.exists(modernDimensionFolder) || Files.isDirectory(modernDimensionFolder.getParent())) {
            return modernDimensionFolder;
        }
        return legacyWorldFolder;
    }

    private Path resolveModernDimensionFolder(Path worldContainer, String worldName) {
        return worldContainer
                .resolve(readLevelNameFromServerProperties())
                .resolve("dimensions")
                .resolve("minecraft")
                .resolve(worldName);
    }

    private String getRunWorldName(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> config.getRunWorldName();
            case NETHER -> config.getRunWorldName() + "_nether";
            case THE_END -> config.getRunWorldName() + "_the_end";
            default -> config.getRunWorldName();
        };
    }

    private List<String> getRunWorldNames() {
        return List.of(config.getRunWorldName(), config.getRunWorldName() + "_nether", config.getRunWorldName() + "_the_end");
    }

    private List<String> getStasisWorldNames() {
        return List.of(STASIS_WORLD_BASE_NAME, STASIS_WORLD_BASE_NAME + "_nether", STASIS_WORLD_BASE_NAME + "_the_end");
    }

    private void unloadWorldFamily(List<String> worldNames, Location relocationTarget) throws IOException {
        for (String worldName : worldNames) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            if (relocationTarget != null) {
                for (Player player : List.copyOf(world.getPlayers())) {
                    teleportPlayerToLobby(player, relocationTarget);
                }
            }
            if (!Bukkit.unloadWorld(world, false)) {
                throw new IOException("World could not be unloaded: " + worldName);
            }
        }
    }

    private void moveRunWorldFamilyToStasis() throws IOException {
        List<String> runWorldNames = getRunWorldNames();
        List<String> stasisWorldNames = getStasisWorldNames();
        for (int i = 0; i < runWorldNames.size(); i++) {
            Path source = resolveWorldFolder(runWorldNames.get(i));
            if (!Files.isDirectory(source)) {
                continue;
            }
            moveWorldFolder(source, resolveWorldFolder(stasisWorldNames.get(i)));
        }
    }

    private void moveWorldFolder(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private void deleteWorldFamilyFolders(List<String> worldNames) throws IOException {
        for (String worldName : worldNames) {
            Path folder = resolveWorldFolder(worldName);
            if (Files.exists(folder)) {
                deleteWorldFolder(folder);
            }
        }
    }

    private void deleteWorldFolder(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException wrapped) {
            throw wrapped.getCause();
        }
    }

    public void applyServerDifficulty(World world) {
        Difficulty target = resolveServerDifficulty();
        if (world.getDifficulty() != target) {
            world.setDifficulty(target);
        }
    }

    private Difficulty resolveServerDifficulty() {
        Difficulty fromProperties = readDifficultyFromServerProperties();
        if (fromProperties != null) {
            return fromProperties;
        }
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? Difficulty.NORMAL : worlds.getFirst().getDifficulty();
    }

    private Difficulty readDifficultyFromServerProperties() {
        Path serverPropertiesPath = Bukkit.getWorldContainer().toPath().resolve("server.properties");
        if (!Files.isRegularFile(serverPropertiesPath)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
            properties.load(input);
            String raw = properties.getProperty("difficulty", "");
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "0", "peaceful" -> Difficulty.PEACEFUL;
                case "1", "easy" -> Difficulty.EASY;
                case "2", "normal" -> Difficulty.NORMAL;
                case "3", "hard" -> Difficulty.HARD;
                default -> null;
            };
        } catch (IOException exception) {
            return null;
        }
    }

    private String readLevelNameFromServerProperties() {
        Path serverPropertiesPath = Bukkit.getWorldContainer().toPath().resolve("server.properties");
        if (!Files.isRegularFile(serverPropertiesPath)) {
            return "world";
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(serverPropertiesPath)) {
            properties.load(input);
            String levelName = properties.getProperty("level-name", "world");
            return levelName == null || levelName.isBlank() ? "world" : levelName.trim();
        } catch (IOException exception) {
            return "world";
        }
    }
}
