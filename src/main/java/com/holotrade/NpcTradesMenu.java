package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        for (int i = 0; i < 45; i++) inv.setItem(i, Items.pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.pane(Material.PURPLE_STAINED_GLASS_PANE));
        for (int i = 0; i < Math.min(45, shop.trades.size()); i++) {
            NpcTrade t = shop.trades.get(i);
            ItemStack base = t.result != null ? t.result.clone() : new ItemStack(Material.PAPER);
            List<String> l = new ArrayList<>();
            l.add("");
            l.add("§8» §dOferta #" + (i + 1) + (t.ready() ? " §a✔ lista" : " §c✖ incompleta"));
            l.add("§7Recibe: §f" + (t.result == null ? "§c-" : t.result.getAmount() + "x " + Items.name(t.result)));
            l.add("§7Pago: §6" + t.mode.label);
            if (t.acceptsItems() && t.cost1 != null) {
                l.add("  §7Items: §f" + t.cost1.getAmount() + "x " + Items.name(t.cost1)
                        + (t.cost2 != null ? " §7+ §f" + t.cost2.getAmount() + "x " + Items.name(t.cost2) : ""));
            }
            if (t.acceptsMoney() && t.money > 0) l.add("  §7Dinero: §e" + plugin.eco().format(t.money));
            if (shop.admin) {
                l.add("§7Stock: §f" + (t.stock < 0 ? "∞" : String.valueOf(t.stock))
                        + (t.restockSec > 0 ? " §8(repone 1 cada " + Items.time(t.restockSec) + ")" : ""));
            }
            l.add("§7Limite/jugador: §f" + (t.limit <= 0 ? "ninguno" : String.valueOf(t.limit)));
            l.add("§7Cooldown: §f" + (t.cooldown <= 0 ? "ninguno" : Items.time(t.cooldown)));
            l.add("§7Compras totales: §f" + t.bought.values().stream().mapToInt(Integer::intValue).sum());
            l.add("");
            l.add("§eClick: §7editar");
            l.add("§cShift + click der: §7eliminar");
            inv.setItem(i, Items.withLore(base, l.toArray(new String[0])));
        }
        inv.setItem(45, Items.icon(Material.ARROW, "§7« Volver"));
        inv.setItem(4 + 45, Items.icon(Material.BOOK, "§d§lOfertas de la tienda",
                "", "§7Total: §f" + shop.trades.size() + "§7/§f45",
                "§7Visibles para clientes: §a" + shop.trades.stream().filter(NpcTrade::ready).count(),
                "§7Incompletas: §c" + shop.trades.stream().filter(t -> !t.ready()).count()));
        if (shop.trades.size() < 45) {
            inv.setItem(53, Items.icon(Material.LIME_DYE, "§a§l+ Nueva oferta", "§7Crea una oferta y ajusta", "§7precio, stock y limites."));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot == 45) {
            new NpcEditorMenu(plugin, shop).open(p);
        } else if (slot == 53 && shop.trades.size() < 45) {
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
