package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menu de compra de un NPC: lista paginada, pago en items o dinero, compra en bloque. */
public class NpcShopMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final NpcShop shop;
    private final Player viewer;
    private final List<NpcTrade> visible = new ArrayList<>();
    private int page = 0;
    private boolean preferMoney = false;

    public NpcShopMenu(HoloTradePlugin plugin, NpcShop shop, Player viewer) {
        this.plugin = plugin;
        this.shop = shop;
        this.viewer = viewer;
        for (NpcTrade t : shop.trades) if (t.ready()) visible.add(t);
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8" + shop.name.replace('&', '§')));
        render();
    }

    private boolean anyBoth() {
        for (NpcTrade t : visible) if (t.mode == PayMode.BOTH) return true;
        return false;
    }

    private void render() {
        inv.clear();
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.pane());

        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            int idx = start + i;
            if (idx >= visible.size()) break;
            inv.setItem(i, tradeIcon(visible.get(idx)));
        }

        if (page > 0) inv.setItem(45, Items.icon(Material.ARROW, "§ePagina anterior"));
        if (start + 45 < visible.size()) inv.setItem(53, Items.icon(Material.ARROW, "§ePagina siguiente"));

        UUID u = viewer.getUniqueId();
        double disc = plugin.npcTrades().discountPct(shop, u);
        List<String> info = new ArrayList<>();
        info.add("§7Ofertas: §f" + visible.size());
        info.add("§7Tus compras aqui: §f" + shop.purchases.getOrDefault(u, 0));
        info.add("§7Descuento fidelidad (dinero): §a" + String.format("%.1f", disc) + "%");
        if (plugin.eco().available()) info.add("§7Tu dinero: §e" + plugin.eco().format(plugin.eco().balance(viewer)));
        info.add("");
        info.add("§8Izq: x1 | Der: x8 | Shift: maximo");
        inv.setItem(49, Items.icon(shop.admin ? Material.NETHER_STAR : Material.EMERALD,
                "§6" + shop.name.replace('&', '§'), info.toArray(new String[0])));

        if (anyBoth()) {
            inv.setItem(48, preferMoney
                    ? Items.icon(Material.GOLD_INGOT, "§eAhora pagas con: §lDINERO", "§7Aplica a las ofertas 'items o dinero'.", "§eClick para cambiar a items")
                    : Items.icon(Material.EMERALD, "§aAhora pagas con: §lITEMS", "§7Aplica a las ofertas 'items o dinero'.", "§eClick para cambiar a dinero"));
        }
    }

    private ItemStack tradeIcon(NpcTrade t) {
        UUID u = viewer.getUniqueId();
        boolean both = t.mode == PayMode.BOTH;
        boolean money = plugin.npcTrades().usesMoney(t, preferMoney);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§8» §6Precio");

        if (t.acceptsItems()) {
            lore.add((both ? (money ? "§8   " : "§a▶ ") : "") + itemsLine(t));
        }
        if (t.acceptsMoney()) {
            double price = plugin.npcTrades().price(shop, t, u);
            boolean ok = !plugin.eco().available() || plugin.eco().has(viewer, price);
            String line = (ok ? "§e" : "§c") + plugin.eco().format(price);
            double disc = plugin.npcTrades().discountPct(shop, u);
            if (disc > 0) line += " §a(-" + String.format("%.1f", disc) + "%)";
            lore.add((both ? (money ? "§a▶ " : "§8   ") : "") + line);
        }

        // stock
        if (shop.admin) {
            t.restockTick();
            lore.add("§7Stock: §f" + (t.stock < 0 ? "Infinito" : String.valueOf(t.stock)));
        } else {
            int st = plugin.npcs().chestStock(shop, t.result);
            lore.add("§7Stock: " + (st > 0 ? "§f" : "§c") + st);
        }
        if (t.limit > 0) lore.add("§7Tu limite: §f" + t.bought.getOrDefault(u, 0) + "/" + t.limit);
        if (t.cooldown > 0) {
            Long l = t.lastUse.get(u);
            long rem = l == null ? 0 : t.cooldown - (System.currentTimeMillis() - l) / 1000;
            lore.add("§7Cooldown: §f" + (rem > 0 ? Items.time(rem) : "listo"));
        }
        lore.add("");
        lore.add("§eIzq: comprar x1 §8| §eDer: x8 §8| §eShift: maximo");
        return Items.withLore(t.result.clone(), lore.toArray(new String[0]));
    }

    private String itemsLine(NpcTrade t) {
        StringBuilder sb = new StringBuilder();
        sb.append(has(t.cost1) ? "§a" : "§c").append(t.cost1.getAmount()).append("x ").append(Items.name(t.cost1));
        if (t.cost2 != null) {
            sb.append(" §7+ ").append(has(t.cost2) ? "§a" : "§c").append(t.cost2.getAmount()).append("x ").append(Items.name(t.cost2));
        }
        return sb.toString();
    }

    private boolean has(ItemStack c) {
        return Items.count(viewer.getInventory(), c) >= c.getAmount();
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 45 && page > 0) {
            page--;
            render();
        } else if (slot == 53 && (page + 1) * 45 < visible.size()) {
            page++;
            render();
        } else if (slot == 48 && anyBoth()) {
            preferMoney = !preferMoney;
            render();
        } else if (slot >= 0 && slot < 45) {
            int idx = page * 45 + slot;
            if (idx >= visible.size()) return;
            int max = plugin.getConfig().getInt("max-bulk", 64);
            int qty = e.isShiftClick() ? max : (e.isRightClick() ? 8 : 1);
            plugin.npcTrades().buy(viewer, shop, visible.get(idx), qty, preferMoney);
            render();
        }
    }
}
