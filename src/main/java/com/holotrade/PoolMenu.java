package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class PoolMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final TradeShop shop;
    private boolean back;

    public PoolMenu(HoloTradePlugin plugin, TradeShop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Pozo de premios"));
        render();
    }

    private void render() {
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());
        for (int i = 0; i < 45; i++) {
            if (i < shop.pool.size()) {
                inv.setItem(i, Items.withLore(shop.pool.get(i), "", "§8Shift + click der: quitar del pozo"));
            } else {
                inv.setItem(i, Items.icon(Material.BLACK_STAINED_GLASS_PANE, "§7Vacio",
                        "§8Pon un item en el cursor y haz click."));
            }
        }
        inv.setItem(49, Items.icon(Material.ARROW, "§aVolver al editor",
                "§7Premios: §f" + shop.pool.size() + "§7/45",
                "§7Todos tienen la misma probabilidad."));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        if (slot == 49) {
            back = true;
            new EditorMenu(plugin, shop).open(p);
            return;
        }
        if (slot < 0 || slot >= 45) return;

        ItemStack cur = Items.held(e, p);
        if (cur != null) {
            if (slot < shop.pool.size()) shop.pool.set(slot, cur.clone());
            else shop.pool.add(cur.clone());
        } else if (e.getClick() == ClickType.SHIFT_RIGHT && slot < shop.pool.size()) {
            shop.pool.remove(slot);
        }
        render();
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (!back) plugin.shops().finishEdit(shop);
    }
}
