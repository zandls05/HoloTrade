package com.holotrade;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Una oferta dentro de una tienda NPC. */
public class NpcTrade {
    public PayMode mode = PayMode.ITEM;
    public ItemStack result;       // lo que recibe el comprador
    public ItemStack cost1, cost2; // pago en items (cost2 opcional)
    public double money;           // pago en dinero

    public int stock = -1;         // solo tiendas admin. -1 = infinito
    public int stockMax = -1;      // tope para el auto-reabastecimiento
    public int restockSec = 0;     // segundos por unidad repuesta (0 = no repone)
    public long lastRestock;

    public int limit = 0;          // compras maximas por jugador (0 = sin limite)
    public int cooldown = 0;       // segundos entre compras por jugador

    public final Map<UUID, Integer> bought = new HashMap<>();
    public final Map<UUID, Long> lastUse = new HashMap<>();

    public boolean ready() {
        if (result == null) return false;
        return switch (mode) {
            case ITEM -> cost1 != null;
            case MONEY -> money > 0;
            case BOTH -> cost1 != null && money > 0;
        };
    }

    public boolean acceptsItems() {
        return mode != PayMode.MONEY;
    }

    public boolean acceptsMoney() {
        return mode != PayMode.ITEM;
    }

    public void restockTick() {
        if (restockSec <= 0 || stockMax < 0 || stock < 0) return;
        long now = System.currentTimeMillis();
        if (stock >= stockMax) {
            lastRestock = now;
            return;
        }
        if (lastRestock == 0) lastRestock = now;
        long step = restockSec * 1000L;
        long steps = (now - lastRestock) / step;
        if (steps > 0) {
            stock = (int) Math.min(stockMax, stock + steps);
            lastRestock += steps * step;
            if (stock >= stockMax) lastRestock = now;
        }
    }

    public void consume() {
        if (stock < 0) return;
        if (stockMax >= 0 && stock >= stockMax) lastRestock = System.currentTimeMillis();
        stock--;
    }
}
