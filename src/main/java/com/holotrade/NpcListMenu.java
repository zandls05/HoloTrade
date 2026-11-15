package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class NpcListMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final List<NpcShop> list;

    public NpcListMenu(HoloTradePlugin plugin, Player owner) {
        this.plugin = plugin;
        this.list = plugin.npcs().byOwner(owner.getUniqueId());
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Mis NPC"));
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());
        for (int i = 0; i < Math.min(45, list.size()); i++) {
            NpcShop s = list.get(i);
            inv.setItem(i, Items.icon(Material.VILLAGER_SPAWN_EGG, "§6" + s.name.replace('&', '§'),
                    "§7Tipo: §f" + (s.admin ? "ADMIN" : "Jugador"),
                    "§7Ofertas: §f" + s.trades.size(),
                    "§7Ubicacion: §f" + (int) s.x + ", " + (int) s.y + ", " + (int) s.z + " §8(" + s.world + ")",
                    "", "§eClick: editar",
                    owner.hasPermission("holotrade.admin") ? "§eShift+click: teletransportarte" : ""));
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
            NpcShop s = list.get(slot);
            if (e.isShiftClick() && p.hasPermission("holotrade.admin")) {
                World w = Bukkit.getWorld(s.world);
                if (w != null) p.teleport(new Location(w, s.x, s.y, s.z));
            } else {
                new NpcEditorMenu(plugin, s).open(p);
            }
        }
    }
}
