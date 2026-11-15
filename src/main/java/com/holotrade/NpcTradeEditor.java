package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Editor de una oferta: resultado, 2 costes en items, precio en dinero, forma de pago, stock, limites. */
public class NpcTradeEditor extends Menu {
    private final HoloTradePlugin plugin;
    private final NpcShop shop;
    private final NpcTrade t;

    public NpcTradeEditor(HoloTradePlugin plugin, NpcShop shop, NpcTrade t) {
        this.plugin = plugin;
        this.shop = shop;
        this.t = t;
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Editar oferta"));
        render();
    }

    private ItemStack slot(ItemStack item, String title, String hint) {
        if (item == null) return Items.icon(Material.BARRIER, title, "§7Vacio. " + hint);
        return Items.withLore(item, "", "§8» " + title, "§7Cantidad: §f" + item.getAmount());
    }

    private void render() {
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());

        inv.setItem(4, Items.icon(Material.BOOK, "§6Como editar",
                "§7Pon un item en el cursor y haz click",
                "§7en un slot para copiarlo (no se gasta).",
                "§7Cursor vacio: izq +1 / der -1 cantidad,",
                "§7Shift+der quita el item."));

        inv.setItem(10, slot(t.result, "§a§lRECIBE el cliente", "Click con item en el cursor."));
        inv.setItem(12, t.acceptsItems()
                ? slot(t.cost1, "§c§lPAGO 1 (items)", "Click con item en el cursor.")
                : Items.icon(Material.GRAY_DYE, "§7Pago en items desactivado"));
        inv.setItem(14, t.acceptsItems()
                ? slot(t.cost2, "§c§lPAGO 2 (opcional)", "Segundo item de pago.")
                : Items.icon(Material.GRAY_DYE, "§7Pago en items desactivado"));
        inv.setItem(16, t.acceptsMoney()
                ? Items.icon(Material.GOLD_INGOT, "§e§lPRECIO EN DINERO: §f" + plugin.eco().format(t.money),
                "§7Izq +1 / Der -1", "§7Shift: +-10", "§7Q: +100 / Ctrl+Q: -100")
                : Items.icon(Material.GRAY_DYE, "§7Pago en dinero desactivado"));

        inv.setItem(28, Items.icon(Material.NAME_TAG, "§6Forma de pago: §f" + t.mode.label,
                "§7Items, dinero (Vault) o que el cliente elija.", "",
                "§eClick: cambiar", "§8Dinero requiere el permiso holotrade.money"));

        if (shop.admin) {
            inv.setItem(30, Items.icon(Material.CHEST, "§6Stock: §f" + (t.stock < 0 ? "Infinito" : String.valueOf(t.stock)),
                    "§7Izq +1 / Der -1 | Shift +-10", "§7-1 = infinito"));
            inv.setItem(31, Items.icon(Material.HOPPER,
                    "§6Reposicion: §f" + (t.restockSec <= 0 ? "Ninguna" : "1 cada " + Items.time(t.restockSec)),
                    "§7Repone stock solo hasta el tope configurado.", "§7Izq +10s / Der -10s | Shift +-60s"));
        } else {
            inv.setItem(30, Items.icon(Material.BARREL, "§6Stock: del cofre", "§7En tiendas de jugador el stock es el cofre vinculado."));
        }
        inv.setItem(32, Items.icon(Material.PLAYER_HEAD, "§6Limite por jugador: §f" + (t.limit <= 0 ? "Sin limite" : String.valueOf(t.limit)),
                "§7Compras maximas por cliente.", "§7Izq +1 / Der -1 | Shift +-10"));
        inv.setItem(34, Items.icon(Material.CLOCK, "§6Cooldown: §f" + (t.cooldown <= 0 ? "Ninguno" : Items.time(t.cooldown)),
                "§7Espera entre compras por jugador.", "§7Izq +10s / Der -10s | Shift +-60s"));

        inv.setItem(45, Items.icon(Material.ARROW, "§7Volver a ofertas"));
        inv.setItem(49, Items.icon(t.ready() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                t.ready() ? "§a§lOferta lista" : "§c§lOferta incompleta",
                t.ready() ? "§7Visible para los clientes." : "§7Falta resultado o pago: no se muestra a clientes."));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        ClickType c = e.getClick();
        ItemStack held = Items.held(e, p);

        switch (e.getRawSlot()) {
            case 10 -> t.result = Items.edit(t.result, held, c);
            case 12 -> {
                if (t.acceptsItems()) t.cost1 = Items.edit(t.cost1, held, c);
            }
            case 14 -> {
                if (t.acceptsItems()) t.cost2 = Items.edit(t.cost2, held, c);
            }
            case 16 -> {
                if (t.acceptsMoney()) t.money = Math.max(0, t.money + Items.moneyStep(c));
            }
            case 28 -> {
                PayMode next = t.mode.next();
                if (next != PayMode.ITEM && !p.hasPermission("holotrade.money")) {
                    p.sendMessage(plugin.msg("money-denied"));
                    return;
                }
                if (next != PayMode.ITEM && !plugin.eco().available()) {
                    p.sendMessage(plugin.msg("no-economy"));
                    return;
                }
                t.mode = next;
            }
            case 30 -> {
                if (!shop.admin) return;
                t.stock = Math.max(-1, t.stock + Items.step(c, 1, 10));
                t.stockMax = t.stock;
            }
            case 31 -> {
                if (!shop.admin) return;
                t.restockSec = Math.max(0, t.restockSec + Items.step(c, 10, 60));
            }
            case 32 -> t.limit = Math.max(0, t.limit + Items.step(c, 1, 10));
            case 34 -> t.cooldown = Math.max(0, t.cooldown + Items.step(c, 10, 60));
            case 45 -> {
                plugin.npcs().markDirty();
                new NpcTradesMenu(plugin, shop).open(p);
                return;
            }
            default -> {
                return;
            }
        }
        plugin.npcs().markDirty();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        render();
    }
}
