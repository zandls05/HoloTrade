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
        for (int i = 0; i < 36; i++) inv.setItem(i, Items.pane());

        inv.setItem(10, Items.icon(Material.VILLAGER_SPAWN_EGG,
                "§6Apariencia: §f" + capital(shop.entityType),
                "§7Izq: siguiente | Der: anterior"));

        if (NpcManager.typeIndex(shop.entityType) == 0) {
            inv.setItem(12, Items.icon(Material.LECTERN, "§6Profesion: §f" + capital(shop.profession),
                    "§7Izq: siguiente | Der: anterior"));
        }

        inv.setItem(14, Items.icon(Material.CHEST, "§e§lOfertas §7(" + shop.trades.size() + ")",
                "§7Crea y edita lo que vende el NPC.", "", "§eClick para abrir"));

        if (shop.admin) {
            inv.setItem(16, Items.icon(Material.NETHER_STAR, "§dTienda ADMIN",
                    "§7Stock infinito o repuesto automatico.", "§7No usa cofre."));
        } else {
            inv.setItem(16, Items.icon(Material.BARREL, "§6Cofre de stock: §f" + (shop.hasChest
                            ? shop.cx + ", " + shop.cy + ", " + shop.cz : "sin vincular"),
                    "§7Los items a vender salen de ese cofre", "§7y los pagos en items entran en el.",
                    "§7El dinero va a la cuenta del dueño.", "", "§eClick para vincular"));
        }

        inv.setItem(20, Items.icon(Material.NAME_TAG, "§6Nombre: §f" + shop.name.replace('&', '§'),
                "§7Cambialo con:", "§e/ht npc name <texto>", "§8(admite colores con &)"));

        inv.setItem(22, Items.icon(Material.COMMAND_BLOCK, "§dModo admin: §f" + (shop.admin ? "SI" : "NO"),
                "§7Stock infinito, sin cofre.", "§8Requiere permiso holotrade.admin", "", "§eClick para cambiar"));

        inv.setItem(24, Items.icon(Material.SPYGLASS, "§aVer como cliente", "§7Abre la tienda como la veran los jugadores."));

        inv.setItem(27, Items.icon(Material.ARROW, "§7Volver al menu"));
        inv.setItem(31, Items.icon(Material.EMERALD_BLOCK, "§a§lGuardar y cerrar"));
        inv.setItem(35, Items.icon(Material.TNT, "§c§lEliminar NPC", "§7Shift + click para confirmar"));
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
