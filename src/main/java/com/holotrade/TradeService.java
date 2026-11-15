package com.holotrade;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class TradeService {
    private final HoloTradePlugin plugin;

    public TradeService(HoloTradePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean fail(Player p, Component msg) {
        p.sendMessage(msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
        return false;
    }

    /**
     * @param fromHand true = paga con el item de la mano principal; false = busca en todo el inventario
     * @param useMoney true = paga con dinero (solo aplica si la tienda lo acepta)
     */
    public boolean trade(Player p, TradeShop s, boolean fromHand, boolean useMoney) {
        if (!s.isReady()) return fail(p, plugin.msg("not-configured"));
        if (s.stock == 0) return fail(p, plugin.msg("no-stock"));

        if (s.cooldown > 0) {
            Long last = s.lastUse.get(p.getUniqueId());
            if (last != null) {
                long rem = s.cooldown - (System.currentTimeMillis() - last) / 1000;
                if (rem > 0) return fail(p, plugin.msg("cooldown", "time", Items.time(rem)));
            }
        }

        boolean money = s.type != TradeType.XP && switch (s.payMode) {
            case ITEM -> false;
            case MONEY -> true;
            case BOTH -> useMoney;
        };

        int decay = plugin.getConfig().getInt("demand-decay-seconds", 60);
        double step = plugin.getConfig().getDouble("demand-money-step", 0.10);
        int cost = s.effectiveAmount(decay);
        double moneyCost = s.moneyCost(decay, step);

        // ---- comprobar pago ----
        if (s.type == TradeType.XP) {
            if (p.getLevel() < s.xpLevels) {
                return fail(p, plugin.msg("no-xp", "levels", String.valueOf(s.xpLevels)));
            }
        } else if (money) {
            if (!plugin.eco().available()) return fail(p, plugin.msg("no-economy"));
            if (!plugin.eco().has(p, moneyCost)) {
                return fail(p, plugin.msg("no-money", "amount", plugin.eco().format(moneyCost)));
            }
        } else {
            boolean ok;
            if (fromHand) {
                ItemStack h = p.getInventory().getItemInMainHand();
                ok = h.isSimilar(s.price) && h.getAmount() >= cost;
            } else {
                ok = Items.count(p.getInventory(), s.price) >= cost;
            }
            if (!ok) {
                return fail(p, plugin.msg("no-payment", "amount", String.valueOf(cost), "item", Items.name(s.price)));
            }
        }

        // ---- premio ----
        ItemStack reward = s.type == TradeType.MYSTERY
                ? s.pool.get(ThreadLocalRandom.current().nextInt(s.pool.size())).clone()
                : s.offer.clone();

        // ---- cobrar ----
        if (s.type == TradeType.XP) {
            p.setLevel(p.getLevel() - s.xpLevels);
        } else if (money) {
            if (!plugin.eco().withdraw(p, moneyCost)) return fail(p, plugin.msg("no-money", "amount", plugin.eco().format(moneyCost)));
            plugin.eco().deposit(Bukkit.getOfflinePlayer(s.owner), moneyCost);
        } else if (fromHand) {
            ItemStack h = p.getInventory().getItemInMainHand();
            int left = h.getAmount() - cost;
            if (left <= 0) p.getInventory().setItemInMainHand(null);
            else {
                h.setAmount(left);
                p.getInventory().setItemInMainHand(h);
            }
        } else {
            Items.remove(p.getInventory(), s.price, cost);
        }

        // ---- entregar (lo que no quepa cae al suelo) ----
        p.getInventory().addItem(reward).values()
                .forEach(i -> p.getWorld().dropItem(p.getLocation(), i));

        // ---- efectos del tradeo ----
        if (s.stock > 0) s.stock--;
        if (s.cooldown > 0) s.lastUse.put(p.getUniqueId(), System.currentTimeMillis());
        if (s.type == TradeType.DEMAND) {
            if (s.demand <= 0) s.lastDecay = System.currentTimeMillis();
            s.demand++;
        }
        plugin.shops().markDirty();
        plugin.shops().refresh(s);

        p.sendMessage(plugin.msg("success", "item", reward.getAmount() + "x " + Items.name(reward)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.x + 0.5, s.y + 1.4, s.z + 0.5, 12, 0.3, 0.3, 0.3, 0);
        return true;
    }
}
