package com.holotrade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tienda atendida por un NPC (estilo Shopkeepers). */
public class NpcShop {
    public final String id;
    public final UUID owner;
    public boolean admin;                 // true = stock infinito/sin cofre, pagos no van a nadie
    public String world;
    public double x, y, z;
    public float yaw;

    public String entityType = "villager";
    public String profession = "FARMER";
    public String name = "&eTienda";

    public boolean hasChest;
    public String chestWorld;
    public int cx, cy, cz;

    public UUID entityId;
    public final List<NpcTrade> trades = new ArrayList<>();
    public final Map<UUID, Integer> purchases = new HashMap<>();

    public NpcShop(String id, UUID owner, boolean admin, String world, double x, double y, double z, float yaw) {
        this.id = id;
        this.owner = owner;
        this.admin = admin;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public int chunkX() {
        return ((int) Math.floor(x)) >> 4;
    }

    public int chunkZ() {
        return ((int) Math.floor(z)) >> 4;
    }
}
