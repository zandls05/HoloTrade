package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/** Editor de una tienda holograma. */
public class EditorMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final TradeShop shop;
    boolean switching; // true cuando se abre el submenu del pozo

    public EditorMenu(HoloTradePlugin plugin, TradeShop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§8Editor de tradeo"));
        render();
    }

    private void render() {
        for (int i = 0; i < 54; i++) inv.setItem(i, Items.pane());

        inv.setItem(4, Items.icon(Material.COMPASS, "§6Tienda §7#" + shop.id,
                "§7Bloque: §f" + shop.x + ", " + shop.y + ", " + shop.z,
                "",
                "§8Pon un item en el cursor y haz click",
                "§8en un slot para copiarlo (no se gasta).",
                "§8Con el cursor vacio: izq +1 / der -1 cantidad,",
                "§8Shift+der quita el item."));

        // item que vendes
        if (shop.type == TradeType.MYSTERY) {
            inv.setItem(20, Items.icon(Material.ENDER_CHEST, "§dPremio misterioso",
                    "§7Se elige al azar del pozo de premios.",
                    "§7Edita el pozo con el boton de abajo."));
        } else {
            inv.setItem(20, slot(shop.offer, "§a§lVENDES (lo que recibe el jugador)"));
        }

        // tipo
        inv.setItem(22, Items.icon(shop.type.icon, shop.type.color + "§lTipo: " + shop.type.label,
                "§7" + shop.type.description, "",
                "§eClick izq: siguiente tipo", "§eClick der: tipo anterior"));

        // precio en items / xp
        if (shop.type == TradeType.XP) {
            inv.setItem(24, Items.icon(Material.EXPERIENCE_BOTTLE, "§b§lPIDES: §f" + shop.xpLevels + " niveles",
                    "§7Izq +1 / Der -1", "§7Shift: +-5"));
        } else if (shop.payMode == PayMode.MONEY) {
            inv.setItem(24, Items.icon(Material.GRAY_DYE, "§7Pago en items desactivado",
                    "§7Esta tienda solo cobra dinero."));
        } else {
            String extra = shop.type == TradeType.DEMAND
                    ? "§7(precio base: sube con cada compra)" : "§7Lo que paga el jugador";
            inv.setItem(24, slot(shop.price, "§c§lPIDES (items)", extra));
        }

        // forma de pago (items / dinero / ambos) y precio en dinero
        if (shop.type != TradeType.XP) {
            inv.setItem(31, Items.icon(Material.NAME_TAG, "§6Forma de pago: §f" + shop.payMode.label,
                    "§7Items, dinero (Vault) o que el cliente elija.", "",
                    "§eClick: cambiar",
                    "§8Dinero requiere el permiso holotrade.money"));
            if (shop.payMode != PayMode.ITEM) {
                inv.setItem(33, Items.icon(Material.GOLD_INGOT,
                        "§e§lPRECIO EN DINERO: §f" + plugin.eco().format(shop.moneyPrice),
                        "§7Izq +1 / Der -1", "§7Shift: +-10", "§7Q: +100 / Ctrl+Q: -100"));
            }
        }

        inv.setItem(38, Items.icon(Material.CHEST, "§6Stock: §f" + (shop.stock < 0 ? "Infinito" : String.valueOf(shop.stock)),
                "§7Izq +1 / Der -1", "§7Shift: +-10", "§7Bajalo a -1 para stock infinito"));
        inv.setItem(40, Items.icon(Material.CLOCK, "§6Cooldown: §f" + (shop.cooldown <= 0 ? "Ninguno" : Items.time(shop.cooldown)),
                "§7Tiempo entre tradeos por jugador.", "§7Izq +10s / Der -10s", "§7Shift: +-60s"));
        inv.setItem(42, Items.icon(Material.ENDER_CHEST, "§dPozo de premios §7(" + shop.pool.size() + ")",
                "§7Solo se usa en el tipo " + TradeType.MYSTERY.color + "Misterio§7.", "", "§eClick para editar"));

        inv.setItem(45, Items.icon(Material.ARROW, "§7Volver al menu"));
        inv.setItem(49, Items.icon(Material.EMERALD_BLOCK, "§a§lGuardar y cerrar"));
        inv.setItem(53, Items.icon(Material.TNT, "§c§lEliminar tienda", "§7Shift + click para confirmar"));
    }

    private ItemStack slot(ItemStack item, String title, String... extra) {
        if (item == null) {
            return Items.icon(Material.BARRIER, title, "§7Vacio. Click con un item en el cursor.");
        }
        String[] lore = new String[3 + extra.length];
        lore[0] = "";
        lore[1] = "§8» " + title;
        lore[2] = "§7Cantidad: §f" + item.getAmount();
        System.arraycopy(extra, 0, lore, 3, extra.length);
        return Items.withLore(item, lore);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        ClickType c = e.getClick();
        ItemStack held = Items.held(e, p);

        switch (e.getRawSlot()) {
            case 20 -> {
                if (shop.type != TradeType.MYSTERY) shop.offer = Items.edit(shop.offer, held, c);
            }
            case 24 -> {
                if (shop.type == TradeType.XP) shop.xpLevels = Math.max(1, shop.xpLevels + Items.step(c, 1, 5));
                else if (shop.payMode != PayMode.MONEY) shop.price = Items.edit(shop.price, held, c);
            }
            case 22 -> shop.type = c.isRightClick() ? shop.type.prev() : shop.type.next();
            case 31 -> {
                if (shop.type == TradeType.XP) return;
                PayMode next = shop.payMode.next();
                if (next != PayMode.ITEM && !p.hasPermission("holotrade.money")) {
                    p.sendMessage(plugin.msg("money-denied"));
                    return;
                }
                if (next != PayMode.ITEM && !plugin.eco().available()) {
                    p.sendMessage(plugin.msg("no-economy"));
                    return;
                }
                shop.payMode = next;
            }
            case 33 -> {
                if (shop.type != TradeType.XP && shop.payMode != PayMode.ITEM) {
                    shop.moneyPrice = Math.max(0, shop.moneyPrice + Items.moneyStep(c));
                }
            }
            case 38 -> shop.stock = Math.max(-1, shop.stock + Items.step(c, 1, 10));
            case 40 -> shop.cooldown = Math.max(0, shop.cooldown + Items.step(c, 10, 60));
            case 42 -> {
                switching = true;
                new PoolMenu(plugin, shop).open(p);
                return;
            }
            case 45 -> {
                new MainMenu(plugin).open(p);
                return;
            }
            case 49 -> {
                p.closeInventory();
                return;
            }
            case 53 -> {
                if (c.isShiftClick()) {
                    plugin.shops().remove(shop);
                    p.closeInventory();
                    p.sendMessage(plugin.msg("deleted"));
                }
                return;
            }
            default -> {
                return;
            }
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        render();
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        if (switching) {
            switching = false;
            return;
        }
        if (!plugin.shops().finishEdit(shop) && e.getPlayer() instanceof Player p) {
            p.sendMessage(plugin.msg("editor-cancelled"));
        }
    }
}
