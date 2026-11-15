package com.holotrade;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Sound;

import java.util.List;

public class ShopListener implements Listener {
    private final HoloTradePlugin plugin;

    public ShopListener(HoloTradePlugin plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  TIENDAS HOLOGRAMA (bloque)  +  vinculacion de cofre de NPC
    // =====================================================================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();

        // --- vinculando cofre de un NPC ---
        String pending = plugin.pendingLink.get(p.getUniqueId());
        if (pending != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            plugin.pendingLink.remove(p.getUniqueId());
            NpcShop npc = plugin.npcs().get(pending);
            if (npc == null || !plugin.canManage(p, npc.owner)) return;
            if (e.getClickedBlock().getState() instanceof Container c && c.getInventory().getSize() % 9 == 0) {
                Block b = e.getClickedBlock();
                npc.hasChest = true;
                npc.chestWorld = b.getWorld().getName();
                npc.cx = b.getX();
                npc.cy = b.getY();
                npc.cz = b.getZ();
                plugin.npcs().markDirty();
                p.sendMessage(plugin.msg("npc-link-ok"));
            } else {
                p.sendMessage(plugin.msg("npc-link-invalid"));
            }
            return;
        }

        // --- tienda holograma ---
        TradeShop s = plugin.shops().get(e.getClickedBlock());
        if (s == null) return;

        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;

        if (!p.hasPermission("holotrade.use")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }

        // dueño/admin + agachado = editar
        if (p.isSneaking() && plugin.canManage(p, s.owner)) {
            new EditorMenu(plugin, s).open(p);
            return;
        }
        if (!s.isReady()) {
            p.sendMessage(plugin.msg("not-configured"));
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (s.acceptsItems() && s.price != null && hand.isSimilar(s.price)) {
            plugin.trades().trade(p, s, true, false);   // tradeo rapido con el item en la mano
        } else {
            new TradeMenu(plugin, s).open(p);           // menu de tradeo (items / dinero)
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.pendingLink.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        TradeShop s = plugin.shops().get(e.getBlock());
        if (s == null) return;
        Player p = e.getPlayer();
        if (plugin.canManage(p, s.owner)) {
            plugin.shops().remove(s);
            p.sendMessage(plugin.msg("deleted"));
        } else {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("break-denied"));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.shops().get(b) != null);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.shops().get(b) != null);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyShop(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyShop(e.getBlocks())) e.setCancelled(true);
    }

    private boolean anyShop(List<Block> blocks) {
        for (Block b : blocks) if (plugin.shops().get(b) != null) return true;
        return false;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        plugin.shops().onChunkLoad(e.getChunk().getX(), e.getChunk().getZ(), e.getWorld().getName());
    }

    // =====================================================================
    //  NPC
    // =====================================================================
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        plugin.npcs().onEntitiesLoad(e.getWorld().getName(), e.getChunk().getX(), e.getChunk().getZ());
    }

    @EventHandler
    public void onInteractNpc(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(plugin.npcKey(), PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true); // nunca abrir el comercio vanilla
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        NpcShop s = plugin.npcs().get(id);
        if (s == null) {                       // NPC huerfano (tienda borrada)
            e.getRightClicked().remove();
            return;
        }
        if (!p.hasPermission("holotrade.use")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (p.isSneaking() && plugin.canManage(p, s.owner)) {
            new NpcEditorMenu(plugin, s).open(p);
        } else {
            new NpcShopMenu(plugin, s, p).open(p);
        }
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(plugin.npcKey(), PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    // =====================================================================
    //  MENUS
    // =====================================================================
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        if (clicked.equals(top)) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1f);
            }
            menu.onClick(e);
        } else if (e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            // impedir que se metan items al menu; el resto de clicks en tu inventario se permiten
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        int size = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < size) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu) menu.onClose(e);
    }
}
