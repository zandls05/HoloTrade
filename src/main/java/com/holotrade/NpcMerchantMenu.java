package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu de comercio estilo aldeano / Shopkeepers (interfaz vanilla de "Comercios").
 * Cada fila = pago 1 + pago 2 -> resultado. Las compras las procesa HoloTrade (stock, cofre, limites,
 * cooldown, dinero, fidelidad), no el sistema vanilla.
 *
 * Funciones extra: pago en dinero (Vault), ofertas "items o dinero" como dos filas, descuento por
 * fidelidad, stock/limite/cooldown/motivo en el tooltip, fila de informacion, Shift (agachado) = comprar maximo.
 */
public class NpcMerchantMenu {
    /** Sesiones abiertas: jugador -> menu. */
    private static final Map<UUID, NpcMerchantMenu> OPEN = new HashMap<>();

    public static NpcMerchantMenu of(org.bukkit.entity.HumanEntity p) {
        return OPEN.get(p.getUniqueId());
    }

    private record Row(NpcTrade trade, boolean money) {}

    private final HoloTradePlugin plugin;
    private final NpcShop shop;
    private final Player viewer;
    private final Merchant merchant;
    private final List<Row> rows = new ArrayList<>(); // index 0 = fila de informacion (trade == null)
    private boolean reopening;

    public NpcMerchantMenu(HoloTradePlugin plugin, NpcShop shop, Player viewer) {
        this.plugin = plugin;
        this.shop = shop;
        this.viewer = viewer;
        this.merchant = Bukkit.createMerchant(Txt.t("§5§l✦ §d" + shop.name.replace('&', '§') + " §5§l✦"));
        build();
    }

    public void open() {
        OPEN.put(viewer.getUniqueId(), this);
        viewer.openMerchant(merchant, true);
    }

    /** Llamado al cerrar el inventario. */
    public static void closed(Player p) {
        NpcMerchantMenu m = OPEN.get(p.getUniqueId());
        if (m != null && !m.reopening) OPEN.remove(p.getUniqueId());
    }

    // =====================================================================
    private void build() {
        rows.clear();
        List<MerchantRecipe> recipes = new ArrayList<>();

        // fila 0: informacion
        rows.add(new Row(null, false));
        recipes.add(infoRecipe());

        for (NpcTrade t : shop.trades) {
            if (!t.ready()) continue;
            if (t.acceptsItems()) addRow(recipes, t, false);
            if (t.acceptsMoney() && plugin.eco().available()) addRow(recipes, t, true);
        }
        merchant.setRecipes(recipes);
    }

    private MerchantRecipe recipe(ItemStack result, boolean blocked) {
        MerchantRecipe r = new MerchantRecipe(result, blocked ? 1 : 0, 1, false, 0, 0f, 0, 0, true);
        return r;
    }

    private MerchantRecipe infoRecipe() {
        UUID u = viewer.getUniqueId();
        double disc = plugin.npcTrades().discountPct(shop, u);
        double max = plugin.getConfig().getDouble("loyalty.max-percent", 15);
        List<String> l = new ArrayList<>();
        l.add("");
        l.add("§5▸ §dTipo: " + (shop.admin ? "§bTienda del servidor" : "§aTienda de jugador"));
        if (!shop.admin) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(shop.owner);
            l.add("§5▸ §dDueño: §f" + (o.getName() == null ? "?" : o.getName()));
        }
        l.add("§5▸ §dTus compras aqui: §f" + shop.purchases.getOrDefault(u, 0));
        l.add("§5▸ §dFidelidad: " + (disc > 0 ? "§a" : "§7") + String.format("%.1f", disc) + "% §8(max " + String.format("%.0f", max) + "%)");
        if (plugin.eco().available()) l.add("§5▸ §dTu dinero: §e" + plugin.eco().format(plugin.eco().balance(viewer)));
        l.add("");
        l.add("§aClick en una fila §7= comprar x1");
        l.add("§aAgachado + click §7= comprar el maximo");
        ItemStack res = Items.icon(shop.admin ? Material.NETHER_STAR : Material.EMERALD, "§d§l" + shop.name.replace('&', '§'), l.toArray(new String[0]));
        MerchantRecipe r = recipe(res, true);
        r.addIngredient(Items.icon(Material.BOOK, "§d§lInformacion"));
        return r;
    }

    private void addRow(List<MerchantRecipe> recipes, NpcTrade t, boolean money) {
        String reason = blocker(t, money);
        MerchantRecipe r = recipe(resultItem(t, money, reason), reason != null);
        if (money) {
            double price = plugin.npcTrades().price(shop, t, viewer.getUniqueId());
            r.addIngredient(moneyItem(t, price));
        } else {
            r.addIngredient(costItem(t.cost1, "§c§lPAGO 1"));
            if (t.cost2 != null) r.addIngredient(costItem(t.cost2, "§c§lPAGO 2"));
        }
        rows.add(new Row(t, money));
        recipes.add(r);
    }

    private ItemStack moneyItem(NpcTrade t, double price) {
        UUID u = viewer.getUniqueId();
        double disc = plugin.npcTrades().discountPct(shop, u);
        double bal = plugin.eco().balance(viewer);
        boolean enough = bal >= price;
        List<String> l = new ArrayList<>();
        l.add("");
        l.add("§8» §e§lPAGO EN DINERO");
        l.add("§7Precio: " + (enough ? "§a" : "§c") + plugin.eco().format(price));
        if (disc > 0) {
            l.add("§7Precio base: §8§m" + plugin.eco().format(t.money));
            l.add("§7Fidelidad: §a-" + String.format("%.1f", disc) + "% §8(ahorras " + plugin.eco().format(t.money - price) + ")");
        }
        l.add("§7Tu dinero: §e" + plugin.eco().format(bal));
        l.add("");
        l.add(enough ? "§a✔ Tienes suficiente" : "§c✖ Te falta " + plugin.eco().format(price - bal));
        return Items.icon(Material.GOLD_INGOT, "§e§l" + plugin.eco().format(price), l.toArray(new String[0]));
    }

    private ItemStack costItem(ItemStack cost, String title) {
        int have = Items.count(viewer.getInventory(), cost);
        boolean enough = have >= cost.getAmount();
        return Items.withLore(cost.clone(),
                "", "§8» " + title,
                "§7Necesitas: §f" + cost.getAmount(),
                "§7Tienes: " + (enough ? "§a" : "§c") + have,
                "", enough ? "§a✔ Tienes suficiente" : "§c✖ Te faltan " + (cost.getAmount() - have));
    }

    private ItemStack resultItem(NpcTrade t, boolean money, String reason) {
        UUID u = viewer.getUniqueId();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§8» §a§lRECIBES §f" + t.result.getAmount() + "x");
        lore.add("");
        lore.add("§8» §6§lCOSTE");
        if (money) {
            lore.add("  §e" + plugin.eco().format(plugin.npcTrades().price(shop, t, u)));
        } else {
            lore.add("  §f" + t.cost1.getAmount() + "x " + Items.name(t.cost1));
            if (t.cost2 != null) lore.add("  §f" + t.cost2.getAmount() + "x " + Items.name(t.cost2));
        }
        lore.add("");
        if (shop.admin) {
            t.restockTick();
            lore.add("§7Stock: " + (t.stock < 0 ? "§b∞" : (t.stock == 0 ? "§c0" : "§f" + t.stock)));
            if (t.restockSec > 0) lore.add("§7Repone: §a1 cada " + Items.time(t.restockSec));
        } else {
            int st = plugin.npcs().chestStock(shop, t.result);
            lore.add("§7Stock: " + (st > 0 ? "§f" + st : "§c0"));
        }
        if (t.limit > 0) {
            int b = t.bought.getOrDefault(u, 0);
            lore.add("§7Tu limite: " + (b >= t.limit ? "§c" : "§f") + b + "§7/§f" + t.limit);
        }
        if (t.cooldown > 0) {
            Long l = t.lastUse.get(u);
            long rem = l == null ? 0 : t.cooldown - (System.currentTimeMillis() - l) / 1000;
            lore.add("§7Cooldown: " + (rem > 0 ? "§c" + Items.time(rem) : "§alisto") + " §8(" + Items.time(t.cooldown) + ")");
        }
        lore.add("");
        lore.add(reason == null ? "§a✔ Disponible" : "§c✖ " + reason);
        lore.add(reason == null ? "§eClick §7x1  §8|  §eAgachado + click §7maximo" : "§8Click para ver el motivo");
        return Items.withLore(t.result.clone(), lore.toArray(new String[0]));
    }

    /** Motivo por el que no se puede comprar ahora (null = se puede). Sin codigos de color. */
    private String blocker(NpcTrade t, boolean money) {
        UUID u = viewer.getUniqueId();
        if (shop.admin) {
            t.restockTick();
            if (t.stock == 0) return "Sin stock";
        } else if (plugin.npcs().chestStock(shop, t.result) <= 0) {
            return "Sin stock en el cofre";
        }
        if (t.limit > 0 && t.bought.getOrDefault(u, 0) >= t.limit) return "Llegaste a tu limite de compras";
        if (t.cooldown > 0) {
            Long l = t.lastUse.get(u);
            long rem = l == null ? 0 : t.cooldown - (System.currentTimeMillis() - l) / 1000;
            if (rem > 0) return "Espera " + Items.time(rem);
        }
        if (money) {
            if (!plugin.eco().available()) return "Economia no disponible";
            if (!plugin.eco().has(viewer, plugin.npcTrades().price(shop, t, u))) return "No tienes suficiente dinero";
        } else {
            if (Items.count(viewer.getInventory(), t.cost1) < t.cost1.getAmount()) return "Te faltan items de pago";
            if (t.cost2 != null && Items.count(viewer.getInventory(), t.cost2) < t.cost2.getAmount())
                return "Te faltan items de pago";
        }
        return null;
    }

    // =====================================================================
    /** El jugador pulso una fila. */
    public void select(int index) {
        if (index <= 0 || index >= rows.size()) return;
        Row row = rows.get(index);
        if (row.trade() == null) return;
        int max = plugin.getConfig().getInt("max-bulk", 64);
        int qty = viewer.isSneaking() ? max : 1;
        plugin.npcTrades().buy(viewer, shop, row.trade(), qty, row.money());

        // refrescar tooltips/estado: reabrir en el siguiente tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (OPEN.get(viewer.getUniqueId()) != this || !viewer.isOnline()) return;
            build();
            reopening = true;
            try {
                viewer.openMerchant(merchant, true);
            } finally {
                reopening = false;
            }
        });
    }
}
