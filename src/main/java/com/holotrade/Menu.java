package com.holotrade;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class Menu implements InventoryHolder {
    protected Inventory inv;

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** Solo se llama para clicks en el inventario superior (el del menu). */
    public abstract void onClick(InventoryClickEvent e);

    public void onClose(InventoryCloseEvent e) {
    }
}
