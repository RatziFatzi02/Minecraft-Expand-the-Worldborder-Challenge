package de.ratzifatzi.expandworldborder.listener;

import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final ChallengeManager challengeManager;

    public PlayerConnectionListener(ChallengeManager challengeManager) {
        this.challengeManager = challengeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        challengeManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        challengeManager.handlePlayerDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        challengeManager.handlePlayerDisconnect(event.getPlayer());
    }
}
