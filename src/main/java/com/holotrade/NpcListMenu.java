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
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane(i >= 45 ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < Math.min(45, list.size()); i++) {
            NpcShop s = list.get(i);
            inv.setItem(i, Items.icon(Material.VILLAGER_SPAWN_EGG, "§6" + s.name.replace('&', '§'),
                    "",
                    "§5▸ §dTipo: " + (s.admin ? "§bAdmin" : "§aJugador"),
                    "§5▸ §dOfertas: §a" + s.trades.stream().filter(NpcTrade::ready).count() + "§7/§f" + s.trades.size(),
                    "§5▸ §dCompras totales: §f" + s.purchases.values().stream().mapToInt(Integer::intValue).sum(),
                    "§5▸ §dCofre: " + (s.admin ? "§8no usa" : (s.hasChest ? "§avinculado" : "§csin vincular")),
                    "§5▸ §dUbicacion: §f" + (int) s.x + ", " + (int) s.y + ", " + (int) s.z + " §8(" + s.world + ")",
                    "", "§eClick: §7editar",
                    owner.hasPermission("holotrade.admin") ? "§eShift+click: §7teletransportarte" : ""));
        }
        inv.setItem(49, Items.icon(Material.ARROW, "§7« Volver"));
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
