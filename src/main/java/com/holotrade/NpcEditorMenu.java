package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Ajustes generales de un NPC. */
public class NpcEditorMenu extends Menu {
    private final HoloTradePlugin plugin;
    private final NpcShop shop;

    public NpcEditorMenu(HoloTradePlugin plugin, NpcShop shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.inv = Bukkit.createInventory(this, 36, Txt.t("§8Editor de NPC §7#" + shop.id));
        render();
    }

    private void render() {
        for (int i = 0; i < 36; i++) inv.setItem(i, Items.pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) inv.setItem(i, Items.pane(Material.PURPLE_STAINED_GLASS_PANE));
        for (int i = 27; i < 36; i++) inv.setItem(i, Items.pane(Material.PURPLE_STAINED_GLASS_PANE));

        long ready = shop.trades.stream().filter(NpcTrade::ready).count();
        int totalBuys = shop.purchases.values().stream().mapToInt(Integer::intValue).sum();
        inv.setItem(4, Items.icon(Material.NETHER_STAR, "§d§l" + shop.name.replace('&', '§'),
                "",
                "§5▸ §dID: §f#" + shop.id,
                "§5▸ §dTipo: " + (shop.admin ? "§bAdmin (stock infinito)" : "§aJugador (cofre)"),
                "§5▸ §dUbicacion: §f" + (int) shop.x + ", " + (int) shop.y + ", " + (int) shop.z + " §8(" + shop.world + ")",
                "§5▸ §dOfertas listas: §a" + ready + "§7/§f" + shop.trades.size(),
                "§5▸ §dCompras totales: §f" + totalBuys,
                "§5▸ §dClientes distintos: §f" + shop.purchases.size()));

        inv.setItem(10, Items.icon(Material.VILLAGER_SPAWN_EGG,
                "§6Apariencia: §f" + capital(shop.entityType),
                "", "§7Cambia el modelo del NPC.", "§eIzq §7siguiente  §8|  §eDer §7anterior"));

        if (NpcManager.typeIndex(shop.entityType) == 0) {
            inv.setItem(12, Items.icon(Material.LECTERN, "§6Profesion: §f" + capital(shop.profession),
                    "§7Izq: siguiente | Der: anterior"));
        }

        inv.setItem(14, Items.icon(Material.CHEST, "§e§lOfertas §7(" + shop.trades.size() + ")",
                "", "§7Crea y edita lo que vende el NPC.",
                "§7Listas: §a" + shop.trades.stream().filter(NpcTrade::ready).count()
                        + " §8| §7Incompletas: §c" + shop.trades.stream().filter(t -> !t.ready()).count(),
                "", "§eClick para abrir"));

        if (shop.admin) {
            inv.setItem(16, Items.icon(Material.NETHER_STAR, "§dTienda ADMIN",
                    "§7Stock infinito o repuesto automatico.", "§7No usa cofre."));
        } else {
            inv.setItem(16, Items.icon(Material.BARREL, "§6Cofre de stock: §f" + (shop.hasChest
                            ? shop.cx + ", " + shop.cy + ", " + shop.cz : "sin vincular"),
                    "", "§7Los items a vender salen de ese cofre",
                    "§7y los pagos en items entran en el.",
                    "§7El dinero va a la cuenta del dueño.", "",
                    shop.hasChest ? "§a✔ Cofre vinculado" : "§c✖ Sin cofre: no podra vender",
                    "", "§eClick para vincular"));
        }

        inv.setItem(20, Items.icon(Material.NAME_TAG, "§6Nombre: §f" + shop.name.replace('&', '§'),
                "", "§7Cambialo mirando al NPC con:", "§e/ht npc name <texto>", "§8Admite colores con &  (ej: &6&lMi Tienda)"));

        inv.setItem(22, Items.icon(Material.COMMAND_BLOCK, "§dModo admin: §f" + (shop.admin ? "SI" : "NO"),
                "", "§7Stock infinito o repuesto automatico,", "§7sin cofre vinculado.", "§8Requiere permiso holotrade.admin", "", "§eClick para cambiar"));

        inv.setItem(24, Items.icon(Material.SPYGLASS, "§a§lVer como cliente", "", "§7Abre la tienda como la veran", "§7los jugadores.", "", "§eClick para abrir"));

        inv.setItem(27, Items.icon(Material.ARROW, "§7Volver al menu"));
        inv.setItem(31, Items.icon(Material.EMERALD_BLOCK, "§a§lGuardar y cerrar", "", "§7Los cambios se guardan solos."));
        inv.setItem(35, Items.icon(Material.TNT, "§c§lEliminar NPC", "", "§7Borra el NPC y todas sus ofertas.", "§cNo se puede deshacer.", "", "§eShift + click §7para confirmar"));
    }

    private static String capital(String s) {
        String t = s.toLowerCase().replace('_', ' ');
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        switch (e.getRawSlot()) {
            case 10 -> {
                int n = NpcManager.TYPES.length;
                int i = NpcManager.typeIndex(shop.entityType) + (e.isRightClick() ? n - 1 : 1);
                shop.entityType = NpcManager.TYPES[i % n].getKey().getKey();
                plugin.npcs().respawn(shop);
            }
            case 12 -> {
                if (NpcManager.typeIndex(shop.entityType) != 0) return;
                int n = NpcManager.PROF_NAMES.length;
                int i = NpcManager.profIndex(shop.profession) + (e.isRightClick() ? n - 1 : 1);
                shop.profession = NpcManager.PROF_NAMES[i % n];
                plugin.npcs().respawn(shop);
            }
            case 14 -> {
                new NpcTradesMenu(plugin, shop).open(p);
                return;
            }
            case 16 -> {
                if (shop.admin) return;
                plugin.pendingLink.put(p.getUniqueId(), shop.id);
                p.closeInventory();
                p.sendMessage(plugin.msg("npc-link-start"));
                return;
            }
            case 22 -> {
                if (!p.hasPermission("holotrade.admin")) {
                    p.sendMessage(plugin.msg("admin-only"));
                    return;
                }
                shop.admin = !shop.admin;
            }
            case 24 -> {
                new NpcShopMenu(plugin, shop, p).open(p);
                return;
            }
            case 27 -> {
                new MainMenu(plugin).open(p);
                return;
            }
            case 31 -> {
                p.closeInventory();
                return;
            }
            case 35 -> {
                if (e.isShiftClick()) {
                    plugin.npcs().remove(shop);
                    p.closeInventory();
                    p.sendMessage(plugin.msg("npc-deleted"));
                }
                return;
            }
            default -> {
                return;
            }
        }
        plugin.npcs().markDirty();
        render();
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        plugin.npcs().markDirty();
    }
}
