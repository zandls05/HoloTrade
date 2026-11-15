package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NpcManager {
    public static final EntityType[] TYPES = {
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.IRON_GOLEM,
            EntityType.WITCH, EntityType.ALLAY, EntityType.SNOW_GOLEM
    };
    public static final String[] PROF_NAMES = {
            "NONE", "ARMORER", "BUTCHER", "CARTOGRAPHER", "CLERIC", "FARMER", "FISHERMAN", "FLETCHER",
            "LEATHERWORKER", "LIBRARIAN", "MASON", "NITWIT", "SHEPHERD", "TOOLSMITH", "WEAPONSMITH"
    };
    public static final Villager.Profession[] PROFS = {
            Villager.Profession.NONE, Villager.Profession.ARMORER, Villager.Profession.BUTCHER,
            Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC, Villager.Profession.FARMER,
            Villager.Profession.FISHERMAN, Villager.Profession.FLETCHER, Villager.Profession.LEATHERWORKER,
            Villager.Profession.LIBRARIAN, Villager.Profession.MASON, Villager.Profession.NITWIT,
            Villager.Profession.SHEPHERD, Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH
    };

    private final HoloTradePlugin plugin;
    private final Map<String, NpcShop> shops = new LinkedHashMap<>();
    private final File file;
    private boolean dirty;

    public NpcManager(HoloTradePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ---------- acceso ----------
    public NpcShop get(String id) {
        return id == null ? null : shops.get(id);
    }

    public NpcShop byEntity(Entity e) {
        if (e == null) return null;
        return get(e.getPersistentDataContainer().get(plugin.npcKey(), PersistentDataType.STRING));
    }

    public List<NpcShop> byOwner(UUID owner) {
        List<NpcShop> l = new ArrayList<>();
        for (NpcShop s : shops.values()) if (s.owner.equals(owner)) l.add(s);
        return l;
    }

    public void add(NpcShop s) {
        shops.put(s.id, s);
        dirty = true;
    }

    public void remove(NpcShop s) {
        despawn(s);
        shops.remove(s.id);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public static int typeIndex(String key) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].getKey().getKey().equals(key)) return i;
        return 0;
    }

    public static int profIndex(String name) {
        for (int i = 0; i < PROF_NAMES.length; i++) if (PROF_NAMES[i].equals(name)) return i;
        return 5;
    }

    // ---------- cofre ----------
    public Container chest(NpcShop s) {
        if (!s.hasChest) return null;
        World w = Bukkit.getWorld(s.chestWorld);
        if (w == null) return null;
        Block b = w.getBlockAt(s.cx, s.cy, s.cz);
        return b.getState() instanceof Container c ? c : null;
    }

    public int chestStock(NpcShop s, ItemStack proto) {
        Container c = chest(s);
        if (c == null) return 0;
        return Items.count(c.getInventory(), proto) / Math.max(1, proto.getAmount());
    }

    // ---------- entidades ----------
    public void ensureAll() {
        for (NpcShop s : shops.values()) ensure(s);
    }

    public void onEntitiesLoad(String world, int chunkX, int chunkZ) {
        for (NpcShop s : shops.values()) {
            if (s.world.equals(world) && s.chunkX() == chunkX && s.chunkZ() == chunkZ) {
                Bukkit.getScheduler().runTask(plugin, () -> ensure(s));
            }
        }
    }

    /** Se asegura de que exista exactamente un NPC para esta tienda (si su chunk esta cargado). */
    public void ensure(NpcShop s) {
        World w = Bukkit.getWorld(s.world);
        if (w == null || !w.isChunkLoaded(s.chunkX(), s.chunkZ())) return;
        Chunk chunk = w.getChunkAt(s.chunkX(), s.chunkZ());
        if (!chunk.isEntitiesLoaded()) return;

        Entity found = null;
        for (Entity en : chunk.getEntities()) {
            String tag = en.getPersistentDataContainer().get(plugin.npcKey(), PersistentDataType.STRING);
            if (s.id.equals(tag)) {
                if (found == null) found = en;
                else en.remove(); // duplicado
            }
        }
        if (found != null) {
            s.entityId = found.getUniqueId();
            return;
        }
        spawn(s);
    }

    public void spawn(NpcShop s) {
        World w = Bukkit.getWorld(s.world);
        if (w == null) return;
        Location loc = new Location(w, s.x, s.y, s.z, s.yaw, 0f);
        Entity e = w.spawnEntity(loc, TYPES[typeIndex(s.entityType)]);
        e.getPersistentDataContainer().set(plugin.npcKey(), PersistentDataType.STRING, s.id);
        e.customName(Txt.amp(s.name));
        e.setCustomNameVisible(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent(true);
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setCollidable(false);
            le.setRemoveWhenFarAway(false);
        }
        if (e instanceof Villager v) {
            v.setProfession(PROFS[profIndex(s.profession)]);
            v.setVillagerLevel(5);
        }
        s.entityId = e.getUniqueId();
    }

    public void despawn(NpcShop s) {
        if (s.entityId != null) {
            Entity e = Bukkit.getEntity(s.entityId);
            if (e != null) e.remove();
        }
        World w = Bukkit.getWorld(s.world);
        if (w != null && w.isChunkLoaded(s.chunkX(), s.chunkZ())) {
            Chunk chunk = w.getChunkAt(s.chunkX(), s.chunkZ());
            if (chunk.isEntitiesLoaded()) {
                for (Entity en : chunk.getEntities()) {
                    if (s.id.equals(en.getPersistentDataContainer().get(plugin.npcKey(), PersistentDataType.STRING))) en.remove();
                }
            }
        }
        s.entityId = null;
    }

    public void respawn(NpcShop s) {
        despawn(s);
        World w = Bukkit.getWorld(s.world);
        if (w != null && w.isChunkLoaded(s.chunkX(), s.chunkZ())) spawn(s);
        dirty = true;
    }

    public void despawnAll() {
        // no se borran: son persistentes. Solo se limpia la referencia.
        for (NpcShop s : shops.values()) s.entityId = null;
    }

    // ---------- persistencia ----------
    public void load() {
        shops.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            try {
                NpcShop s = new NpcShop(id, UUID.fromString(c.getString("owner")), c.getBoolean("admin"),
                        c.getString("world"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"),
                        (float) c.getDouble("yaw"));
                s.entityType = c.getString("type", "villager");
                s.profession = c.getString("profession", "FARMER");
                s.name = c.getString("name", "&eTienda");
                if (c.contains("chest.world")) {
                    s.hasChest = true;
                    s.chestWorld = c.getString("chest.world");
                    s.cx = c.getInt("chest.x");
                    s.cy = c.getInt("chest.y");
                    s.cz = c.getInt("chest.z");
                }
                String ent = c.getString("entity");
                if (ent != null) s.entityId = UUID.fromString(ent);

                ConfigurationSection pc = c.getConfigurationSection("purchases");
                if (pc != null) for (String k : pc.getKeys(false)) s.purchases.put(UUID.fromString(k), pc.getInt(k));

                ConfigurationSection tc = c.getConfigurationSection("trades");
                if (tc != null) {
                    for (String k : tc.getKeys(false)) {
                        ConfigurationSection t = tc.getConfigurationSection(k);
                        if (t == null) continue;
                        NpcTrade nt = new NpcTrade();
                        try {
                            nt.mode = PayMode.valueOf(t.getString("mode", "ITEM"));
                        } catch (IllegalArgumentException ignored) {
                        }
                        nt.result = t.getItemStack("result");
                        nt.cost1 = t.getItemStack("cost1");
                        nt.cost2 = t.getItemStack("cost2");
                        nt.money = t.getDouble("money");
                        nt.stock = t.getInt("stock", -1);
                        nt.stockMax = t.getInt("stockMax", -1);
                        nt.restockSec = t.getInt("restock", 0);
                        nt.limit = t.getInt("limit", 0);
                        nt.cooldown = t.getInt("cooldown", 0);
                        ConfigurationSection bc = t.getConfigurationSection("bought");
                        if (bc != null) for (String u : bc.getKeys(false)) nt.bought.put(UUID.fromString(u), bc.getInt(u));
                        s.trades.add(nt);
                    }
                }
                shops.put(id, s);
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo cargar el NPC " + id + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (NpcShop s : shops.values()) {
            String p = "npcs." + s.id + ".";
            cfg.set(p + "owner", s.owner.toString());
            cfg.set(p + "admin", s.admin);
            cfg.set(p + "world", s.world);
            cfg.set(p + "x", s.x);
            cfg.set(p + "y", s.y);
            cfg.set(p + "z", s.z);
            cfg.set(p + "yaw", (double) s.yaw);
            cfg.set(p + "type", s.entityType);
            cfg.set(p + "profession", s.profession);
            cfg.set(p + "name", s.name);
            if (s.hasChest) {
                cfg.set(p + "chest.world", s.chestWorld);
                cfg.set(p + "chest.x", s.cx);
                cfg.set(p + "chest.y", s.cy);
                cfg.set(p + "chest.z", s.cz);
            }
            if (s.entityId != null) cfg.set(p + "entity", s.entityId.toString());
            for (Map.Entry<UUID, Integer> en : s.purchases.entrySet()) {
                cfg.set(p + "purchases." + en.getKey(), en.getValue());
            }
            int i = 0;
            for (NpcTrade t : s.trades) {
                String tp = p + "trades." + (i++) + ".";
                cfg.set(tp + "mode", t.mode.name());
                cfg.set(tp + "result", t.result);
                cfg.set(tp + "cost1", t.cost1);
                cfg.set(tp + "cost2", t.cost2);
                cfg.set(tp + "money", t.money);
                cfg.set(tp + "stock", t.stock);
                cfg.set(tp + "stockMax", t.stockMax);
                cfg.set(tp + "restock", t.restockSec);
                cfg.set(tp + "limit", t.limit);
                cfg.set(tp + "cooldown", t.cooldown);
                for (Map.Entry<UUID, Integer> en : t.bought.entrySet()) {
                    cfg.set(tp + "bought." + en.getKey(), en.getValue());
                }
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar npcs.yml: " + ex.getMessage());
        }
    }
}
