package com.holotrade;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tienda holograma sobre un bloque. */
public class TradeShop {
    public final String id;
    public final UUID owner;
    public final String world;
    public final int x, y, z;

    public TradeType type = TradeType.BARTER;
    public PayMode payMode = PayMode.ITEM;
    public ItemStack offer;          // lo que recibe el jugador
    public ItemStack price;          // pago en items
    public double moneyPrice = 0;    // pago en dinero
    public int xpLevels = 5;         // tipo XP
    public int stock = -1;           // -1 = infinito
    public int cooldown = 0;         // segundos por jugador
    public List<ItemStack> pool = new ArrayList<>(); // tipo MYSTERY

    // runtime
    public boolean configured;
    public int demand;
    public long lastDecay;
    public float spin;
    public final Map<UUID, Long> lastUse = new HashMap<>();
    public final List<UUID> displays = new ArrayList<>();

    public TradeShop(String id, UUID owner, String world, int x, int y, int z) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public TradeShop(String id, UUID owner, Block b) {
        this(id, owner, b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static String key(String w, int x, int y, int z) {
        return w + ";" + x + ";" + y + ";" + z;
    }

    public static String key(Block b) {
        return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public String key() {
        return key(world, x, y, z);
    }

    private boolean paymentOk() {
        return switch (payMode) {
            case ITEM -> price != null;
            case MONEY -> moneyPrice > 0;
            case BOTH -> price != null && moneyPrice > 0;
        };
    }

    public boolean isReady() {
        return switch (type) {
            case XP -> offer != null;
            case MYSTERY -> paymentOk() && !pool.isEmpty();
            default -> offer != null && paymentOk();
        };
    }

    public boolean acceptsItems() {
        return type != TradeType.XP && payMode != PayMode.MONEY;
    }

    public boolean acceptsMoney() {
        return type != TradeType.XP && payMode != PayMode.ITEM;
    }

    /** Aplica la bajada de demanda con el tiempo y devuelve la demanda actual. */
    public int demandLevel(int decaySeconds) {
        if (demand <= 0) {
            demand = 0;
            return 0;
        }
        long now = System.currentTimeMillis();
        long step = Math.max(1, decaySeconds) * 1000L;
        long steps = (now - lastDecay) / step;
        if (steps > 0) {
            demand = (int) Math.max(0, demand - steps);
            lastDecay += steps * step;
        }
        return demand;
    }

    public int effectiveAmount(int decaySeconds) {
        if (price == null) return 0;
        if (type == TradeType.DEMAND) {
            return Math.min(price.getMaxStackSize(), price.getAmount() + demandLevel(decaySeconds));
        }
        return price.getAmount();
    }

    public double moneyCost(int decaySeconds, double demandStep) {
        double c = moneyPrice;
        if (type == TradeType.DEMAND) c *= 1 + demandStep * demandLevel(decaySeconds);
        return Math.round(c * 100.0) / 100.0;
    }
}
