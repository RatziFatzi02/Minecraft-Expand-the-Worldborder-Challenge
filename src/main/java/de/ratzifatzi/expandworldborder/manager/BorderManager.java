package de.ratzifatzi.expandworldborder.manager;

import de.ratzifatzi.expandworldborder.config.PluginConfig;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public final class BorderManager {

    private final PluginConfig config;
    private final WorldManager worldManager;
    private double currentSize;
    private double centerX;
    private double centerZ;

    public BorderManager(PluginConfig config, WorldManager worldManager, double currentSize) {
        this.config = config;
        this.worldManager = worldManager;
        this.currentSize = clamp(currentSize);
        this.centerX = config.getBorderCenterX();
        this.centerZ = config.getBorderCenterZ();
    }

    public void resetToStartSize() {
        currentSize = config.getBorderStartSize();
        applyCurrentSize(0);
    }

    public void restoreCurrentSize() {
        applyCurrentSize(0);
    }

    public double growForAdvancement() {
        currentSize = clamp(currentSize + config.getBorderGrowthPerAdvancement());
        applyCurrentSize(config.getBorderAnimationSeconds());
        return currentSize;
    }

    public void centerOn(double x, double z) {
        centerX = x;
        centerZ = z;
        applyCurrentSize(0);
    }

    public void applyCurrentSize(int animationSeconds) {
        for (World world : worldManager.getLoadedRunWorlds()) {
            if (!config.isBorderEnabledFor(world.getEnvironment())) {
                continue;
            }
            WorldBorder border = world.getWorldBorder();
            border.setCenter(centerX, centerZ);
            if (animationSeconds <= 0) {
                border.setSize(currentSize);
            } else {
                border.setSize(currentSize, animationSeconds);
            }
        }
    }

    public double getCurrentSize() {
        return currentSize;
    }

    public double getCurrentRadius() {
        return currentSize / 2.0D;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    private double clamp(double value) {
        return Math.max(1.0D, Math.min(config.getBorderMaxSize(), value));
    }
}
