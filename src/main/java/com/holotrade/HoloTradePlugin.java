package com.holotrade;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HoloTradePlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ShopManager shops;
    private TradeService trades;
    private NpcManager npcs;
    private NpcTradeService npcTrades;
    private EconomyHook eco;
    private NamespacedKey npcKey;

    /** jugador -> id de NPC que esta vinculando un cofre */
    public final Map<UUID, String> pendingLink = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        npcKey = new NamespacedKey(this, "npc_id");
        eco = new EconomyHook();
        shops = new ShopManager(this);
        shops.load();
        trades = new TradeService(this);
        npcs = new NpcManager(this);
        npcs.load();
        npcTrades = new NpcTradeService(this);

        Bukkit.getPluginManager().registerEvents(new ShopListener(this), this);
        getCommand("holotrade").setExecutor(this);
        getCommand("holotrade").setTabCompleter(this);

        shops.startTasks();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (npcs.isDirty()) npcs.save();
        }, 6000L, 6000L);

        // esperar a que los mundos esten cargados
        Bukkit.getScheduler().runTask(this, () -> {
            shops.refreshAll();
            npcs.ensureAll();
        });
        getLogger().info("HoloTrade activado: " + shops.all().size() + " tiendas holograma. Economia: "
                + (eco.available() ? "Vault OK" : "no disponible (solo pagos con items)"));
    }

    @Override
    public void onDisable() {
        if (shops != null) {
            shops.save();
            shops.removeAllDisplays();
        }
        if (npcs != null) npcs.save();
    }

    public ShopManager shops() { return shops; }
    public TradeService trades() { return trades; }
    public NpcManager npcs() { return npcs; }
    public NpcTradeService npcTrades() { return npcTrades; }
    public EconomyHook eco() { return eco; }
    public NamespacedKey npcKey() { return npcKey; }

    /** Texto de config con placeholders, sin prefijo, ya con codigos §. */
    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "&c[falta mensaje: " + key + "]").replace('&', '§');
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    /** Mensaje de config.yml con prefijo. */
    public Component msg(String key, String... kv) {
        String prefix = getConfig().getString("messages.prefix", "").replace('&', '§');
        return Txt.t(prefix + raw(key, kv));
    }

    public boolean canManage(Player p, UUID owner) {
        return p.getUniqueId().equals(owner) || p.hasPermission("holotrade.admin");
    }

    // ---------- crear tiendas ----------
    public void createShop(Player p) {
        if (!p.hasPermission("holotrade.create")) {
            p.sendMessage(msg("no-permission"));
            return;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) {
            p.sendMessage(msg("look-at-block"));
            return;
        }
        if (shops.get(b) != null) {
            p.sendMessage(msg("already-shop"));
            return;
        }
        int max = getConfig().getInt("max-shops-per-player", 5);
        if (!p.hasPermission("holotrade.admin") && shops.byOwner(p.getUniqueId()).size() >= max) {
            p.sendMessage(msg("limit-reached", "max", String.valueOf(max)));
            return;
        }
        TradeShop s = new TradeShop(UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), b);
        shops.add(s);
        p.sendMessage(msg("created"));
        new EditorMenu(this, s).open(p);
    }

    public void createNpc(Player p, boolean admin) {
        if (!p.hasPermission("holotrade.create")) {
            p.sendMessage(msg("no-permission"));
            return;
        }
        if (admin && !p.hasPermission("holotrade.admin")) {
            p.sendMessage(msg("admin-only"));
            return;
        }
        int max = getConfig().getInt("max-npc-shops-per-player", 3);
        if (!p.hasPermission("holotrade.admin") && npcs.byOwner(p.getUniqueId()).size() >= max) {
            p.sendMessage(msg("npc-limit", "max", String.valueOf(max)));
            return;
        }
        Location l = p.getLocation();
        NpcShop s = new NpcShop(UUID.randomUUID().toString().substring(0, 8), p.getUniqueId(), admin,
                l.getWorld().getName(), l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5, l.getYaw() + 180f);
        npcs.add(s);
        npcs.spawn(s);
        p.sendMessage(msg("npc-created"));
        new NpcEditorMenu(this, s).open(p);
    }

    /** NPC de tienda al que mira el jugador (a menos de 6 bloques). */
    public NpcShop targetNpc(Player p) {
        Entity en = p.getTargetEntity(6);
        return npcs.byEntity(en);
    }

    // ---------- comandos ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("holotrade.admin")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            reloadConfig();
            shops.removeAllDisplays();
            shops.load();
            shops.refreshAll();
            npcs.save();
            npcs.load();
            npcs.ensureAll();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores (excepto /ht reload).");
            return true;
        }

        switch (sub) {
            case "menu" -> new MainMenu(this).open(p);
            case "create" -> createShop(p);
            case "list" -> new MyShopsMenu(this, p).open(p);
            case "delete" -> {
                Block b = p.getTargetBlockExact(6);
                TradeShop s = b == null ? null : shops.get(b);
                if (s == null) p.sendMessage(msg("not-a-shop"));
                else if (!canManage(p, s.owner)) p.sendMessage(msg("not-yours"));
                else {
                    shops.remove(s);
                    p.sendMessage(msg("deleted"));
                }
            }
            case "npc" -> npcCommand(p, args);
            default -> {
                p.sendMessage(Txt.t("§6HoloTrade §7- comandos:"));
                p.sendMessage(Txt.t("§e/ht §7menu principal"));
                p.sendMessage(Txt.t("§e/ht create §7tienda holograma en el bloque que miras"));
                p.sendMessage(Txt.t("§e/ht list §7mis tiendas holograma"));
                p.sendMessage(Txt.t("§e/ht delete §7borrar la tienda holograma que miras"));
                p.sendMessage(Txt.t("§e/ht npc create [admin] §7crear NPC"));
                p.sendMessage(Txt.t("§e/ht npc edit|name <texto>|link|delete|list §7(mirando al NPC)"));
                p.sendMessage(Txt.t("§e/ht reload §7(admin)"));
            }
        }
        return true;
    }

    private void npcCommand(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "list";
        switch (sub) {
            case "create" -> createNpc(p, args.length > 2 && args[2].equalsIgnoreCase("admin"));
            case "list" -> new NpcListMenu(this, p).open(p);
            default -> {
                NpcShop s = targetNpc(p);
                if (s == null) {
                    p.sendMessage(msg("npc-not-found"));
                    return;
                }
                if (!canManage(p, s.owner)) {
                    p.sendMessage(msg("not-yours"));
                    return;
                }
                switch (sub) {
                    case "edit" -> new NpcEditorMenu(this, s).open(p);
                    case "name" -> {
                        if (args.length < 3) {
                            p.sendMessage(Txt.t("§cUso: /ht npc name <texto>"));
                            return;
                        }
                        s.name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                        npcs.respawn(s);
                        p.sendMessage(msg("npc-renamed"));
                    }
                    case "link" -> {
                        if (s.admin) return;
                        pendingLink.put(p.getUniqueId(), s.id);
                        p.sendMessage(msg("npc-link-start"));
                    }
                    case "delete" -> {
                        npcs.remove(s);
                        p.sendMessage(msg("npc-deleted"));
                    }
                    default -> p.sendMessage(Txt.t("§cUso: /ht npc create|edit|name|link|delete|list"));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"menu", "create", "list", "delete", "npc", "reload", "help"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            for (String s : new String[]{"create", "edit", "name", "link", "delete", "list"}) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("create")) {
            if ("admin".startsWith(args[2].toLowerCase())) out.add("admin");
        }
        return out;
    }
}
