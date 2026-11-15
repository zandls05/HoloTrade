package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Lista de ofertas de un NPC (para editar). */
public class NpcTradesMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final NpcShop shop;

    public NpcTradesMenu(HoloTradePlugin plugin, NpcShop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Ofertas del NPC"));
        render();
    }

    private void render() {
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());
        for (int i = 0; i < Math.min(45, shop.trades.size()); i++) {
            NpcTrade t = shop.trades.get(i);
            ItemStack base = t.result != null ? t.result.clone() : new ItemStack(Material.PAPER);
            inv.setItem(i, Items.withLore(base,
                    "",
                    "§8» Oferta #" + (i + 1) + (t.ready() ? "" : " §c(incompleta)"),
                    "§7Pago: §f" + t.mode.label,
                    "",
                    "§eClick: editar",
                    "§cShift + click der: eliminar"));
        }
        inv.setItem(45, Items.icon(Material.ARROW, "§7Volver"));
        if (shop.trades.size() < 45) {
            inv.setItem(49, Items.icon(Material.LIME_DYE, "§a§l+ Nueva oferta"));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot == 45) {
            new NpcEditorMenu(plugin, shop).open(p);
        } else if (slot == 49 && shop.trades.size() < 45) {
            NpcTrade t = new NpcTrade();
            shop.trades.add(t);
            plugin.npcs().markDirty();
            new NpcTradeEditor(plugin, shop, t).open(p);
        } else if (slot >= 0 && slot < 45 && slot < shop.trades.size()) {
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                shop.trades.remove(slot);
                plugin.npcs().markDirty();
                render();
            } else {
                new NpcTradeEditor(plugin, shop, shop.trades.get(slot)).open(p);
            }
        }
    }
}
