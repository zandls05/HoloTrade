package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MyShopsMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final List<TradeShop> list;

    public MyShopsMenu(HoloTradePlugin plugin, Player owner) {
        this.plugin = plugin;
        this.list = plugin.shops().byOwner(owner.getUniqueId());
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Mis tiendas holograma"));
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());

        for (int i = 0; i < Math.min(45, list.size()); i++) {
            TradeShop s = list.get(i);
            ItemStack base;
            if (s.type == TradeType.MYSTERY) base = new ItemStack(Material.ENDER_CHEST);
            else if (s.offer != null) base = s.offer.clone();
            else base = new ItemStack(Material.PAPER);
            base.setAmount(1);
            inv.setItem(i, Items.withLore(base,
                    "",
                    "§8» " + s.type.color + s.type.label + " §7(" + s.payMode.label + ")",
                    "§7Ubicacion: §f" + s.x + ", " + s.y + ", " + s.z + " §8(" + s.world + ")",
                    "§7Stock: §f" + (s.stock < 0 ? "Infinito" : String.valueOf(s.stock)),
                    "",
                    "§eClick para editar"));
        }
        inv.setItem(49, Items.icon(Material.ARROW, "§7Volver"));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot == 49) {
            new MainMenu(plugin).open(p);
        } else if (slot >= 0 && slot < 45 && slot < list.size()) {
            new EditorMenu(plugin, list.get(slot)).open(p);
        }
    }
}
