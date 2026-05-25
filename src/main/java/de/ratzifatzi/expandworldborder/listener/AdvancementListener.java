package de.ratzifatzi.expandworldborder.listener;

import de.ratzifatzi.expandworldborder.manager.ChallengeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public final class AdvancementListener implements Listener {

    private final ChallengeManager challengeManager;

    public AdvancementListener(ChallengeManager challengeManager) {
        this.challengeManager = challengeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        challengeManager.handleAdvancement(event.getPlayer(), event.getAdvancement());
    }
}
