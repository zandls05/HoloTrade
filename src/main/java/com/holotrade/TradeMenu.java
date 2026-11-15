package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menu de tradeo de una tienda holograma. */
public class TradeMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final TradeShop shop;

    public TradeMenu(HoloTradePlugin plugin, TradeShop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inv = Bukkit.createInventory(this, 27, Txt.t("§8Tradeo " + shop.type.color + shop.type.label));
        render();
    }

    private void render() {
        for (int i = 0; i < 27; i++) inv.setItem(i, Items.pane());
        int decay = plugin.getConfig().getInt("demand-decay-seconds", 60);
        double step = plugin.getConfig().getDouble("demand-money-step", 0.10);

        // lo que recibes
        if (shop.type == TradeType.MYSTERY) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Recibes un premio al azar de:");
            for (int i = 0; i < Math.min(8, shop.pool.size()); i++) {
                ItemStack it = shop.pool.get(i);
                lore.add("§8- §f" + it.getAmount() + "x " + Items.name(it));
            }
            if (shop.pool.size() > 8) lore.add("§8... y " + (shop.pool.size() - 8) + " mas");
            inv.setItem(11, Items.icon(Material.ENDER_CHEST, "§d§lPremio misterioso", lore.toArray(new String[0])));
        } else if (shop.offer != null) {
            inv.setItem(11, Items.withLore(shop.offer, "", "§8» §aRecibes"));
        }

        // lo que pagas
        String moneyTxt = plugin.eco().format(shop.moneyCost(decay, step));
        String itemsTxt = "-";
        if (shop.type == TradeType.XP) {
            inv.setItem(15, Items.icon(Material.EXPERIENCE_BOTTLE, "§b§l" + shop.xpLevels + " niveles de XP", "§8» §cPagas"));
            itemsTxt = shop.xpLevels + " niveles";
        } else if (shop.acceptsItems() && shop.price != null) {
            int amount = shop.effectiveAmount(decay);
            ItemStack shown = shop.price.clone();
            shown.setAmount(Math.max(1, amount));
            inv.setItem(15, shop.acceptsMoney()
                    ? Items.withLore(shown, "", "§8» §cPagas §f" + amount + "x", "§7o §e" + moneyTxt)
                    : Items.withLore(shown, "", "§8» §cPagas §f" + amount + "x"));
            itemsTxt = amount + "x " + Items.name(shop.price);
        } else if (shop.acceptsMoney()) {
            inv.setItem(15, Items.icon(Material.GOLD_INGOT, "§e§l" + moneyTxt, "§8» §cPagas"));
        }

        // botones
        if (!shop.isReady()) {
            inv.setItem(13, Items.icon(Material.RED_CONCRETE, "§cNo disponible"));
        } else if (shop.acceptsItems() && shop.acceptsMoney()) {
            inv.setItem(12, Items.icon(Material.LIME_CONCRETE, "§a§lPAGAR CON ITEMS", "§7Pagas: §f" + itemsTxt, "", "§eClick para tradear"));
            inv.setItem(14, Items.icon(Material.YELLOW_CONCRETE, "§e§lPAGAR CON DINERO", "§7Pagas: §f" + moneyTxt, "", "§eClick para tradear"));
        } else if (shop.acceptsMoney()) {
            inv.setItem(13, Items.icon(Material.YELLOW_CONCRETE, "§e§lPAGAR CON DINERO", "§7Pagas: §f" + moneyTxt, "", "§eClick para tradear"));
        } else {
            inv.setItem(13, Items.icon(Material.LIME_CONCRETE, "§a§lTRADEAR", "§7Pagas: §f" + itemsTxt, "", "§eClick para tradear"));
        }

        List<String> info = new ArrayList<>();
        info.add("§7Tipo: " + shop.type.color + shop.type.label);
        info.add("§7Stock: §f" + (shop.stock < 0 ? "Infinito" : String.valueOf(shop.stock)));
        if (shop.cooldown > 0) info.add("§7Cooldown: §f" + Items.time(shop.cooldown));
        if (shop.type == TradeType.DEMAND) info.add("§7Demanda actual: §f" + shop.demandLevel(decay));
        info.add("");
        info.add("§8Tip: sosten el item de pago y haz click");
        info.add("§8derecho al bloque para tradear rapido.");
        inv.setItem(22, Items.icon(Material.OAK_SIGN, "§6Informacion", info.toArray(new String[0])));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (!shop.isReady()) return;
        if (slot == 13) {
            // boton unico: dinero si la tienda solo acepta dinero, si no items
            plugin.trades().trade(p, shop, false, !shop.acceptsItems());
        } else if (slot == 12) {
            plugin.trades().trade(p, shop, false, false);
        } else if (slot == 14) {
            plugin.trades().trade(p, shop, false, true);
        } else {
            return;
        }
        render();
    }
}
