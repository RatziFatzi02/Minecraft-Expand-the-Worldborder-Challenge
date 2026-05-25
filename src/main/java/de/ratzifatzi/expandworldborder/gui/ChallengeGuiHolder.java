package de.ratzifatzi.expandworldborder.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ChallengeGuiHolder implements InventoryHolder {

    private final GuiScreenType screenType;
    private final int page;
    private Inventory inventory;

    public ChallengeGuiHolder(GuiScreenType screenType, int page) {
        this.screenType = screenType;
        this.page = Math.max(1, page);
    }

    public GuiScreenType screenType() {
        return screenType;
    }

    public int page() {
        return page;
    }

    public void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
