package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu de compra de un NPC, estilo Shopkeepers pero con mas informacion.
 * Cada fila es una oferta:  [pago 1] [pago 2] [pago extra] [estado] [RESULTADO] [stock] [limite] [cooldown] [ahorro]
 */
public class NpcShopMenu extends Menu {
    private static final int PER_PAGE = 4;

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
        this.inv = Bukkit.createInventory(this, 54, Txt.t("§5§l✦ §d" + shop.name.replace('&', '§') + " §5§l✦"));
        render();
    }

    private boolean anyBoth() {
        for (NpcTrade t : visible) if (t.mode == PayMode.BOTH) return true;
        return false;
    }

    private int pages() {
        return Math.max(1, (visible.size() + PER_PAGE - 1) / PER_PAGE);
    }

    // =====================================================================
    private void render() {
        inv.clear();
        for (int i = 0; i < 9; i++) inv.setItem(i, Items.pane(Material.PURPLE_STAINED_GLASS_PANE));
        for (int i = 9; i < 45; i++) inv.setItem(i, Items.pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.pane(Material.PURPLE_STAINED_GLASS_PANE));

        renderHeader();

        if (visible.isEmpty()) {
            inv.setItem(22, Items.icon(Material.BARRIER, "§c§lSin ofertas",
                    "§7Esta tienda aun no tiene nada a la venta."));
        }
        for (int r = 0; r < PER_PAGE; r++) {
            int idx = page * PER_PAGE + r;
            if (idx >= visible.size()) break;
            renderRow(9 + r * 9, visible.get(idx));
        }

        if (page > 0) inv.setItem(45, Items.icon(Material.SPECTRAL_ARROW, "§e« Pagina anterior", "§7Pagina §f" + page + "§7/§f" + pages()));
        inv.setItem(49, Items.icon(Material.PAPER, "§dPagina §f" + (page + 1) + "§d/§f" + pages(),
                "§7Ofertas: §f" + visible.size()));
        if ((page + 1) * PER_PAGE < visible.size())
            inv.setItem(53, Items.icon(Material.SPECTRAL_ARROW, "§ePagina siguiente »", "§7Pagina §f" + (page + 2) + "§7/§f" + pages()));
    }

    private void renderHeader() {
        UUID u = viewer.getUniqueId();
        double disc = plugin.npcTrades().discountPct(shop, u);
        double max = plugin.getConfig().getDouble("loyalty.max-percent", 15);
        int bought = shop.purchases.getOrDefault(u, 0);

        List<String> info = new ArrayList<>();
        info.add("");
        info.add("§5▸ §dTipo: " + (shop.admin ? "§bTienda del servidor" : "§aTienda de jugador"));
        if (!shop.admin) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(shop.owner);
            info.add("§5▸ §dDueño: §f" + (o.getName() == null ? "?" : o.getName()));
        }
        info.add("§5▸ §dOfertas: §f" + visible.size());
        info.add("§5▸ §dTus compras aqui: §f" + bought);
        if (plugin.eco().available()) info.add("§5▸ §dTu dinero: §e" + plugin.eco().format(plugin.eco().balance(viewer)));
        info.add("");
        info.add("§8Izq: x1  §7|  §8Der: x8  §7|  §8Shift: maximo");
        inv.setItem(4, Items.icon(shop.admin ? Material.NETHER_STAR : Material.EMERALD,
                "§d§l" + shop.name.replace('&', '§'), info.toArray(new String[0])));

        // fidelidad
        inv.setItem(2, Items.icon(Material.AMETHYST_SHARD, "§d§lFidelidad",
                "",
                "§7Descuento actual: " + (disc > 0 ? "§a" : "§8") + String.format("%.1f", disc) + "%",
                "§7Maximo: §f" + String.format("%.0f", max) + "%",
                "§7Progreso: " + bar(disc, max, 10),
                "",
                "§8Cada compra aqui aumenta tu descuento",
                "§8en pagos con dinero."));

        // forma de pago
        if (anyBoth()) {
            inv.setItem(6, preferMoney
                    ? Items.icon(Material.GOLD_INGOT, "§e§lPagando con: DINERO",
                    "", "§7Se aplica a las ofertas §f'items o dinero'§7.", "", "§eClick §7para pagar con §aitems")
                    : Items.icon(Material.EMERALD, "§a§lPagando con: ITEMS",
                    "", "§7Se aplica a las ofertas §f'items o dinero'§7.", "", "§eClick §7para pagar con §6dinero"));
        } else {
            inv.setItem(6, Items.icon(Material.BOOK, "§d§lComo comprar",
                    "", "§aIzq §7= comprar x1", "§aDer §7= comprar x8", "§aShift §7= maximo posible"));
        }
    }

    private static String bar(double v, double max, int len) {
        int on = max <= 0 ? 0 : (int) Math.round(Math.min(1, v / max) * len);
        return "§a" + "▌".repeat(on) + "§8" + "▌".repeat(len - on);
    }

    // =====================================================================
    private void renderRow(int base, NpcTrade t) {
        UUID u = viewer.getUniqueId();
        boolean money = plugin.npcTrades().usesMoney(t, preferMoney);
        String reason = blocker(t, money);
        boolean ok = reason == null;

        // --- pagos ---
        if (money) {
            double price = plugin.npcTrades().price(shop, t, u);
            double disc = plugin.npcTrades().discountPct(shop, u);
            boolean enough = plugin.eco().available() && plugin.eco().has(viewer, price);
            List<String> l = new ArrayList<>();
            l.add("");
            l.add("§7Precio: " + (enough ? "§a" : "§c") + plugin.eco().format(price));
            if (disc > 0) {
                l.add("§7Precio base: §8§m" + plugin.eco().format(t.money));
                l.add("§7Fidelidad: §a-" + String.format("%.1f", disc) + "%  §8(ahorras " + plugin.eco().format(t.money - price) + ")");
            }
            l.add("§7Tu dinero: §e" + plugin.eco().format(plugin.eco().balance(viewer)));
            l.add("");
            l.add(enough ? "§a✔ Tienes suficiente" : "§c✖ Te falta " + plugin.eco().format(Math.max(0, price - plugin.eco().balance(viewer))));
            inv.setItem(base, Items.icon(Material.GOLD_INGOT, "§e§lPAGAS: §f" + plugin.eco().format(price), l.toArray(new String[0])));
        } else {
            inv.setItem(base, costIcon(t.cost1, "§c§lPAGO 1"));
            inv.setItem(base + 1, t.cost2 != null ? costIcon(t.cost2, "§c§lPAGO 2")
                    : Items.icon(Material.GRAY_STAINED_GLASS_PANE, "§8Sin segundo pago"));
        }
        inv.setItem(base + 2, t.mode == PayMode.BOTH
                ? Items.icon(Material.COMPARATOR, "§6Pago flexible",
                "", "§7Esta oferta acepta §aitems §7o §edinero§7.",
                "§7Ahora: " + (money ? "§edinero" : "§aitems"), "§8Cambialo con el boton de arriba.")
                : Items.icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        // --- estado / flecha ---
        inv.setItem(base + 3, ok
                ? Items.icon(Material.LIME_STAINED_GLASS_PANE, "§a§l➜ DISPONIBLE", "", "§7Haz click para comprar.")
                : Items.icon(Material.RED_STAINED_GLASS_PANE, "§c§l✖ NO DISPONIBLE", "", reason));

        // --- resultado ---
        inv.setItem(base + 4, resultIcon(t, money, ok));

        // --- info ---
        if (shop.admin) {
            t.restockTick();
            int st = t.stock;
            inv.setItem(base + 5, Items.icon(Material.CHEST,
                    "§6Stock: " + (st < 0 ? "§b∞" : (st == 0 ? "§c0" : "§f" + st)),
                    "", st < 0 ? "§7Stock infinito." : "§7Unidades restantes.",
                    t.restockSec > 0 ? "§7Repone: §a1 cada " + Items.time(t.restockSec) : "§8Sin reposicion automatica"));
        } else {
            int st = plugin.npcs().chestStock(shop, t.result);
            inv.setItem(base + 5, Items.icon(Material.BARREL,
                    "§6Stock: " + (st > 0 ? "§f" + st : "§c0"),
                    "", "§7Unidades disponibles en el", "§7cofre de la tienda."));
        }

        if (t.limit > 0) {
            int b = t.bought.getOrDefault(u, 0);
            inv.setItem(base + 6, Items.icon(b >= t.limit ? Material.BARRIER : Material.PLAYER_HEAD,
                    "§bTu limite: " + (b >= t.limit ? "§c" : "§f") + b + "§7/§f" + t.limit,
                    "", "§7Compras maximas por jugador.", "§7Quedan: §f" + Math.max(0, t.limit - b)));
        } else {
            inv.setItem(base + 6, Items.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§8Sin limite de compra"));
        }

        if (t.cooldown > 0) {
            Long l = t.lastUse.get(u);
            long rem = l == null ? 0 : t.cooldown - (System.currentTimeMillis() - l) / 1000;
            inv.setItem(base + 7, Items.icon(Material.CLOCK,
                    "§dCooldown: " + (rem > 0 ? "§c" + Items.time(rem) : "§alisto"),
                    "", "§7Espera entre compras: §f" + Items.time(t.cooldown)));
        } else {
            inv.setItem(base + 7, Items.icon(Material.PINK_STAINED_GLASS_PANE, "§8Sin cooldown"));
        }

        inv.setItem(base + 8, Items.icon(Material.PURPLE_STAINED_GLASS_PANE, " "));
    }

    private ItemStack costIcon(ItemStack cost, String title) {
        int have = Items.count(viewer.getInventory(), cost);
        boolean enough = have >= cost.getAmount();
        return Items.withLore(cost.clone(),
                "", "§8» " + title,
                "§7Necesitas: §f" + cost.getAmount(),
                "§7Tienes: " + (enough ? "§a" : "§c") + have,
                "", enough ? "§a✔ Tienes suficiente" : "§c✖ Te faltan " + (cost.getAmount() - have));
    }

    private ItemStack resultIcon(NpcTrade t, boolean money, boolean ok) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§8» §a§lRECIBES");
        lore.add("§7Cantidad: §f" + t.result.getAmount());
        lore.add("");
        lore.add("§8» §6§lCOSTE");
        if (money) {
            lore.add("  §e" + plugin.eco().format(plugin.npcTrades().price(shop, t, viewer.getUniqueId())));
        } else {
            lore.add("  §f" + t.cost1.getAmount() + "x " + Items.name(t.cost1));
            if (t.cost2 != null) lore.add("  §f" + t.cost2.getAmount() + "x " + Items.name(t.cost2));
        }
        lore.add("");
        lore.add(ok ? "§aIzq §7x1  §8|  §aDer §7x8  §8|  §aShift §7maximo" : "§c✖ No puedes comprar ahora");
        ItemStack it = Items.withLore(t.result.clone(), lore.toArray(new String[0]));
        return it;
    }

    /** Motivo por el que el jugador no puede comprar ahora (null = puede). */
    private String blocker(NpcTrade t, boolean money) {
        UUID u = viewer.getUniqueId();
        if (shop.admin) {
            t.restockTick();
            if (t.stock == 0) return "§cSin stock.";
        } else if (plugin.npcs().chestStock(shop, t.result) <= 0) {
            return "§cSin stock en el cofre.";
        }
        if (t.limit > 0 && t.bought.getOrDefault(u, 0) >= t.limit) return "§cLlegaste a tu limite de compras.";
        if (t.cooldown > 0) {
            Long l = t.lastUse.get(u);
            long rem = l == null ? 0 : t.cooldown - (System.currentTimeMillis() - l) / 1000;
            if (rem > 0) return "§cEspera " + Items.time(rem) + ".";
        }
        if (money) {
            if (!plugin.eco().available()) return "§cEconomia no disponible.";
            if (!plugin.eco().has(viewer, plugin.npcTrades().price(shop, t, u))) return "§cNo tienes suficiente dinero.";
        } else {
            if (Items.count(viewer.getInventory(), t.cost1) < t.cost1.getAmount()) return "§cTe faltan items de pago.";
            if (t.cost2 != null && Items.count(viewer.getInventory(), t.cost2) < t.cost2.getAmount())
                return "§cTe faltan items de pago.";
        }
        return null;
    }

    // =====================================================================
    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getRawSlot();
        if (slot == 45 && page > 0) {
            page--;
            render();
        } else if (slot == 53 && (page + 1) * PER_PAGE < visible.size()) {
            page++;
            render();
        } else if (slot == 6 && anyBoth()) {
            preferMoney = !preferMoney;
            render();
        } else if (slot >= 9 && slot < 45) {
            int row = slot / 9 - 1;
            int col = slot % 9;
            if (col > 4) return;
            int idx = page * PER_PAGE + row;
            if (idx >= visible.size()) return;
            int max = plugin.getConfig().getInt("max-bulk", 64);
            int qty = e.isShiftClick() ? max : (e.isRightClick() ? 8 : 1);
            plugin.npcTrades().buy(viewer, shop, visible.get(idx), qty, preferMoney);
            render();
        }
    }
}
