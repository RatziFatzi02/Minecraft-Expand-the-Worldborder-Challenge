package de.ratzifatzi.expandworldborder.listener;

import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathListener implements Listener {

    private final ChallengeManager challengeManager;

    public DeathListener(ChallengeManager challengeManager) {
        this.challengeManager = challengeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        challengeManager.handlePlayerDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        challengeManager.handleRespawn(event);
    }
}
