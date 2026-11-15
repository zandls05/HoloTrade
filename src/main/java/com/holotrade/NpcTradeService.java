package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class NpcTradeService {
    public enum Result { OK, NOT_READY, NO_STOCK, LIMIT, COOLDOWN, NO_ITEMS, NO_MONEY, NO_SPACE, NO_ECONOMY, NO_CHEST }

    private final HoloTradePlugin plugin;

    public NpcTradeService(HoloTradePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean usesMoney(NpcTrade t, boolean preferMoney) {
        return switch (t.mode) {
            case ITEM -> false;
            case MONEY -> true;
            case BOTH -> preferMoney;
        };
    }

    /** Descuento de fidelidad (en %) segun las compras previas del jugador en esta tienda. */
    public double discountPct(NpcShop s, UUID u) {
        double per = plugin.getConfig().getDouble("loyalty.percent-per-purchase", 0.5);
        double max = plugin.getConfig().getDouble("loyalty.max-percent", 15);
        return Math.min(max, per * s.purchases.getOrDefault(u, 0));
    }

    public double price(NpcShop s, NpcTrade t, UUID u) {
        double p = t.money * (1 - discountPct(s, u) / 100.0);
        return Math.round(p * 100.0) / 100.0;
    }

    /** Compra hasta {@code qty} veces seguidas; se detiene en el primer fallo. */
    public int buy(Player p, NpcShop s, NpcTrade t, int qty, boolean preferMoney) {
        int done = 0;
        double[] spent = {0};
        Result last = Result.OK;
        for (int i = 0; i < qty; i++) {
            last = buyOnce(p, s, t, preferMoney, spent);
            if (last != Result.OK) break;
            done++;
        }
        boolean money = usesMoney(t, preferMoney);

        if (done > 0) {
            String item = (t.result.getAmount() * done) + "x " + Items.name(t.result);
            String cost;
            if (money) {
                cost = plugin.eco().format(spent[0]);
            } else {
                cost = (t.cost1.getAmount() * done) + "x " + Items.name(t.cost1);
                if (t.cost2 != null) cost += " + " + (t.cost2.getAmount() * done) + "x " + Items.name(t.cost2);
            }
            p.sendMessage(plugin.msg("npc-bought", "item", item, "cost", cost));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);

            if (!s.admin && plugin.getConfig().getBoolean("npc.notify-owner", true)) {
                Player owner = Bukkit.getPlayer(s.owner);
                if (owner != null && !owner.equals(p)) {
                    owner.sendMessage(plugin.msg("npc-owner-notify", "player", p.getName(), "item", item,
                            "shop", Txt.legacy(Txt.amp(s.name))));
                }
            }
            plugin.npcs().markDirty();
        }
        if (last != Result.OK) {
            reportFailure(p, last, done);
        }
        return done;
    }

    private void reportFailure(Player p, Result r, int done) {
        String key = switch (r) {
            case NOT_READY -> "not-configured";
            case NO_STOCK -> "npc-no-stock";
            case LIMIT -> "npc-limit-player";
            case COOLDOWN -> "npc-cooldown";
            case NO_ITEMS -> "npc-no-items";
            case NO_MONEY -> "npc-no-money";
            case NO_SPACE -> "npc-no-space";
            case NO_ECONOMY -> "no-economy";
            case NO_CHEST -> "npc-no-chest";
            default -> "npc-no-stock";
        };
        if (done == 0) {
            p.sendMessage(plugin.msg(key, "time", "unos segundos"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
        } else {
            p.sendMessage(plugin.msg("npc-partial", "reason", plugin.raw(key, "time", "unos segundos")));
        }
    }

    private Result buyOnce(Player p, NpcShop s, NpcTrade t, boolean preferMoney, double[] spent) {
        t.restockTick();
        if (!t.ready()) return Result.NOT_READY;
        UUID uid = p.getUniqueId();
        boolean money = usesMoney(t, preferMoney);
        var eco = plugin.eco();
        if (money && !eco.available()) return Result.NO_ECONOMY;

        if (t.limit > 0 && t.bought.getOrDefault(uid, 0) >= t.limit) return Result.LIMIT;
        if (t.cooldown > 0) {
            Long l = t.lastUse.get(uid);
            if (l != null && System.currentTimeMillis() - l < t.cooldown * 1000L) return Result.COOLDOWN;
        }

        // ---- stock ----
        Inventory chestInv = null;
        Inventory temp = null;
        if (s.admin) {
            if (t.stock == 0) return Result.NO_STOCK;
        } else {
            Container c = plugin.npcs().chest(s);
            if (c == null) return Result.NO_CHEST;
            chestInv = c.getInventory();
            temp = Bukkit.createInventory(null, chestInv.getSize());
            ItemStack[] src = chestInv.getContents();
            ItemStack[] copy = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) copy[i] = src[i] == null ? null : src[i].clone();
            temp.setContents(copy);
            if (Items.count(temp, t.result) < t.result.getAmount()) return Result.NO_STOCK;
            Items.remove(temp, t.result, t.result.getAmount());
        }

        // ---- pago ----
        double price = 0;
        if (money) {
            price = price(s, t, uid);
            if (!eco.has(p, price)) return Result.NO_MONEY;
        } else {
            if (Items.count(p.getInventory(), t.cost1) < t.cost1.getAmount()) return Result.NO_ITEMS;
            if (t.cost2 != null && Items.count(p.getInventory(), t.cost2) < t.cost2.getAmount()) return Result.NO_ITEMS;
            if (!s.admin) {
                if (!temp.addItem(t.cost1.clone()).isEmpty()) return Result.NO_SPACE;
                if (t.cost2 != null && !temp.addItem(t.cost2.clone()).isEmpty()) return Result.NO_SPACE;
            }
        }

        // ---- ejecutar ----
        if (money) {
            if (!eco.withdraw(p, price)) return Result.NO_MONEY;
            if (!s.admin) eco.deposit(Bukkit.getOfflinePlayer(s.owner), price);
            spent[0] += price;
        } else {
            Items.remove(p.getInventory(), t.cost1, t.cost1.getAmount());
            if (t.cost2 != null) Items.remove(p.getInventory(), t.cost2, t.cost2.getAmount());
        }
        if (!s.admin) chestInv.setContents(temp.getContents());

        p.getInventory().addItem(t.result.clone()).values()
                .forEach(i -> p.getWorld().dropItem(p.getLocation(), i));

        if (s.admin) t.consume();
        t.bought.merge(uid, 1, Integer::sum);
        if (t.cooldown > 0) t.lastUse.put(uid, System.currentTimeMillis());
        s.purchases.merge(uid, 1, Integer::sum);
        return Result.OK;
    }
}
