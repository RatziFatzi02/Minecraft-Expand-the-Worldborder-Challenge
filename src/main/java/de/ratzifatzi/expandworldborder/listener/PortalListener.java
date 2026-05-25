package de.ratzifatzi.expandworldborder.listener;

import de.ratzifatzi.expandworldborder.challenge.ChallengeState;
import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import de.ratzifatzi.expandworldborder.manager.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PortalListener implements Listener {

    private final ChallengeManager challengeManager;
    private final WorldManager worldManager;

    public PortalListener(ChallengeManager challengeManager, WorldManager worldManager) {
        this.challengeManager = challengeManager;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (challengeManager.getState() != ChallengeState.RUNNING) {
            return;
        }
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null || !worldManager.isRunWorldFamily(fromWorld)) {
            return;
        }
        World.Environment targetEnvironment = resolveTargetEnvironment(fromWorld.getEnvironment(), event.getCause());
        if (targetEnvironment == null) {
            return;
        }
        World targetWorld = worldManager.resolveRunWorldForEnvironment(targetEnvironment);
        event.setCanCreatePortal(true);
        event.setTo(remapPortalTarget(event.getFrom(), fromWorld.getEnvironment(), targetWorld, targetEnvironment));
    }

    private World.Environment resolveTargetEnvironment(World.Environment fromEnvironment, PlayerTeleportEvent.TeleportCause cause) {
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromEnvironment == World.Environment.NORMAL) {
                return World.Environment.NETHER;
            }
            if (fromEnvironment == World.Environment.NETHER) {
                return World.Environment.NORMAL;
            }
        }
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL && fromEnvironment == World.Environment.THE_END) {
            return World.Environment.NORMAL;
        }
        return null;
    }

    private Location remapPortalTarget(Location from, World.Environment fromEnvironment, World targetWorld, World.Environment targetEnvironment) {
        double x = from.getX();
        double y = from.getY();
        double z = from.getZ();
        if (fromEnvironment == World.Environment.NORMAL && targetEnvironment == World.Environment.NETHER) {
            x /= 8.0D;
            z /= 8.0D;
        } else if (fromEnvironment == World.Environment.NETHER && targetEnvironment == World.Environment.NORMAL) {
            x *= 8.0D;
            z *= 8.0D;
        }
        double clampedY = Math.max(targetWorld.getMinHeight() + 1, Math.min(targetWorld.getMaxHeight() - 2, y));
        return new Location(targetWorld, x, clampedY, z, from.getYaw(), from.getPitch());
    }
}
