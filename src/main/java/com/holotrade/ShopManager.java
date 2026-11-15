package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopManager {
    public static final String TAG = "holotrade_display";
    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final Vector3f SCALE = new Vector3f(0.6f, 0.6f, 0.6f);

    private final HoloTradePlugin plugin;
    private final Map<String, TradeShop> shops = new LinkedHashMap<>();
    private final File file;
    private boolean dirty;

    public ShopManager(HoloTradePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    // ---------- acceso ----------
    public TradeShop get(Block b) {
        return shops.get(TradeShop.key(b));
    }

    public List<TradeShop> all() {
        return new ArrayList<>(shops.values());
    }

    public List<TradeShop> byOwner(UUID owner) {
        List<TradeShop> list = new ArrayList<>();
        for (TradeShop s : shops.values()) if (s.owner.equals(owner)) list.add(s);
        return list;
    }

    public void add(TradeShop s) {
        shops.put(s.key(), s);
        dirty = true;
    }

    public void remove(TradeShop s) {
        removeDisplays(s);
        shops.remove(s.key());
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Se llama al cerrar el editor. Devuelve false si la tienda se elimino por no estar configurada. */
    public boolean finishEdit(TradeShop s) {
        if (s.isReady()) {
            s.configured = true;
        } else if (!s.configured) {
            remove(s);
            return false;
        }
        dirty = true;
        refresh(s);
        return true;
    }

    // ---------- persistencia ----------
    public void load() {
        shops.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            try {
                TradeShop s = new TradeShop(id, UUID.fromString(c.getString("owner")),
                        c.getString("world"), c.getInt("x"), c.getInt("y"), c.getInt("z"));
                try {
                    s.type = TradeType.valueOf(c.getString("type", "BARTER"));
                    s.payMode = PayMode.valueOf(c.getString("paymode", "ITEM"));
                } catch (IllegalArgumentException ignored) {
                }
                s.customText = c.getString("holo-text");
                s.holoHeight = c.getDouble("holo-height", -1);
                s.holoScale = (float) c.getDouble("holo-scale", 1.0);
                s.offer = c.getItemStack("offer");
                s.price = c.getItemStack("price");
                s.moneyPrice = c.getDouble("money", 0);
                s.xpLevels = c.getInt("xp", 5);
                s.stock = c.getInt("stock", -1);
                s.cooldown = c.getInt("cooldown", 0);
                List<?> raw = c.getList("pool");
                if (raw != null) for (Object o : raw) if (o instanceof ItemStack i) s.pool.add(i);
                s.configured = s.isReady();
                shops.put(s.key(), s);
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo cargar la tienda " + id + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (TradeShop s : shops.values()) {
            String p = "shops." + s.id + ".";
            cfg.set(p + "owner", s.owner.toString());
            cfg.set(p + "world", s.world);
            cfg.set(p + "x", s.x);
            cfg.set(p + "y", s.y);
            cfg.set(p + "z", s.z);
            cfg.set(p + "type", s.type.name());
            cfg.set(p + "paymode", s.payMode.name());
            cfg.set(p + "offer", s.offer);
            cfg.set(p + "price", s.price);
            cfg.set(p + "money", s.moneyPrice);
            cfg.set(p + "xp", s.xpLevels);
            cfg.set(p + "stock", s.stock);
            cfg.set(p + "cooldown", s.cooldown);
            cfg.set(p + "pool", s.pool);
            cfg.set(p + "holo-text", s.customText);
            cfg.set(p + "holo-height", s.holoHeight);
            cfg.set(p + "holo-scale", s.holoScale);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar shops.yml: " + ex.getMessage());
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    // ---------- hologramas ----------
    public void removeDisplays(TradeShop s) {
        for (UUID id : s.displays) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        s.displays.clear();
        // barrido por si quedaron hologramas huerfanos (recarga con PlugManX, UUID perdido, etc.)
        World w = Bukkit.getWorld(s.world);
        if (w != null && w.isChunkLoaded(s.x >> 4, s.z >> 4)) {
            Location c = new Location(w, s.x + 0.5, s.y + 4, s.z + 0.5);
            for (Entity e : w.getNearbyEntities(c, 1.0, 7.0, 1.0)) {
                if (e instanceof Display && e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
    }

    /** Elimina TODOS los displays de HoloTrade de los chunks cargados. */
    public void sweepOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Display.class)) {
                if (e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
    }

    public void removeAllDisplays() {
        for (TradeShop s : shops.values()) removeDisplays(s);
        sweepOrphans();
    }

    public void refreshAll() {
        sweepOrphans();
        for (TradeShop s : shops.values()) refresh(s);
    }

    public void onChunkLoad(int cx, int cz, String world) {
        for (TradeShop s : shops.values()) {
            if (s.world.equals(world) && (s.x >> 4) == cx && (s.z >> 4) == cz) {
                Bukkit.getScheduler().runTask(plugin, () -> refresh(s));
            }
        }
    }

    public void refresh(TradeShop s) {
        removeDisplays(s);
        if (!s.isReady()) return;
        World w = Bukkit.getWorld(s.world);
        if (w == null || !w.isChunkLoaded(s.x >> 4, s.z >> 4)) return;

        final ItemStack shown = s.type == TradeType.MYSTERY
                ? new ItemStack(Material.ENDER_CHEST) : s.offer.clone();
        shown.setAmount(1);

        double baseY = s.y + (s.holoHeight >= 0 ? s.holoHeight : plugin.getConfig().getDouble("display.height", 1.35));
        final Vector3f scale = new Vector3f(SCALE).mul(s.holoScale);
        Location base = new Location(w, s.x + 0.5, baseY, s.z + 0.5);

        ItemDisplay item = w.spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), scale, new Quaternionf()));
        });

        final String text = s.customText != null && !s.customText.isBlank() ? s.customText.replace('&', '§') : buildText(s);
        TextDisplay label = w.spawn(base.clone().add(0, 0.6, 0), TextDisplay.class, d -> {
            d.text(Txt.t(text));
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
        });

        s.displays.add(item.getUniqueId());
        s.displays.add(label.getUniqueId());
    }

    private String buildText(TradeShop s) {
        int decay = plugin.getConfig().getInt("demand-decay-seconds", 60);
        double step = plugin.getConfig().getDouble("demand-money-step", 0.10);
        StringBuilder sb = new StringBuilder();
        sb.append(s.type.color).append("§l» ").append(s.type.label).append(" «\n");
        if (s.type == TradeType.MYSTERY) {
            sb.append("§dPremio misterioso\n");
        } else {
            sb.append("§f").append(s.offer.getAmount()).append("x ").append(Items.name(s.offer)).append("\n");
        }
        if (s.type == TradeType.XP) {
            sb.append("§7Pide: §b").append(s.xpLevels).append(" niveles\n");
        } else {
            String items = s.price == null ? "" : s.effectiveAmount(decay) + "x " + Items.name(s.price);
            String money = plugin.eco().format(s.moneyCost(decay, step));
            String pay = switch (s.payMode) {
                case ITEM -> items;
                case MONEY -> money;
                case BOTH -> items + " §7o §e" + money;
            };
            sb.append("§7Pide: §e").append(pay).append("\n");
        }
        sb.append("§8Stock: ").append(s.stock < 0 ? "∞" : String.valueOf(s.stock));
        return sb.toString();
    }

    // ---------- tareas ----------
    public void startTasks() {
        // giro suave del item (cada segundo gira 90 grados interpolados)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (TradeShop s : shops.values()) {
                if (s.displays.isEmpty()) continue;
                Entity e = Bukkit.getEntity(s.displays.get(0));
                if (e instanceof ItemDisplay d) {
                    s.spin = (s.spin + HALF_PI) % TWO_PI;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(20);
                    d.setTransformation(new Transformation(new Vector3f(),
                            new Quaternionf().rotateY(s.spin), new Vector3f(SCALE).mul(s.holoScale), new Quaternionf()));
                }
            }
        }, 20L, 20L);

        // autoguardado cada 5 minutos si hubo cambios
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 6000L, 6000L);
    }
}
