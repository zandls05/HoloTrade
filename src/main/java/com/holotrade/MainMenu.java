package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class MainMenu extends Menu {
    private final HoloTradePlugin plugin;

    public MainMenu(HoloTradePlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 36, Txt.t("§8HoloTrade §7- Menu"));
        for (int i = 0; i < 36; i++) inv.setItem(i, Items.pane());

        // --- tiendas holograma ---
        inv.setItem(10, Items.icon(Material.EMERALD, "§a§lCrear tienda holograma",
                "§7Mira un bloque y haz click: el item",
                "§7flota encima y se tradea con click derecho."));
        inv.setItem(11, Items.icon(Material.CHEST, "§e§lMis tiendas holograma"));

        // --- NPC ---
        inv.setItem(15, Items.icon(Material.VILLAGER_SPAWN_EGG, "§a§lCrear tienda NPC",
                "§7Un aldeano (u otro NPC) con varias ofertas,",
                "§7pago en items o dinero y compra en bloque.", "",
                "§7Shift+click: crear NPC §dADMIN",
                "§8(stock infinito, requiere holotrade.admin)"));
        inv.setItem(16, Items.icon(Material.LECTERN, "§e§lMis NPC"));

        List<String> lore = new ArrayList<>();
        lore.add("§7Tipos de tradeo holograma:");
        lore.add("");
        for (TradeType t : TradeType.values()) {
            lore.add(t.color + t.label + "§8: §7" + t.description);
        }
        lore.add("");
        lore.add("§7Pago: items, dinero (Vault) o ambos.");
        inv.setItem(13, Items.icon(Material.BOOK, "§6§lGuia de tradeos", lore.toArray(new String[0])));

        inv.setItem(31, Items.icon(Material.BARRIER, "§cCerrar"));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        switch (e.getRawSlot()) {
            case 10 -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.createShop(p));
            }
            case 11 -> new MyShopsMenu(plugin, p).open(p);
            case 15 -> {
                boolean admin = e.isShiftClick();
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> plugin.createNpc(p, admin));
            }
            case 16 -> new NpcListMenu(plugin, p).open(p);
            case 31 -> p.closeInventory();
            default -> {
            }
        }
    }
}
