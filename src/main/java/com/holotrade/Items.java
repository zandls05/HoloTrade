package com.holotrade;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {}

    public static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Txt.c(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    public static ItemStack withLore(ItemStack base, String... extra) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String s : extra) lore.add(Txt.c(s));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String name(ItemStack it) {
        if (it == null) return "-";
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName() && m.displayName() != null) return Txt.legacy(m.displayName());
        return pretty(it.getType());
    }

    public static int count(Inventory inv, ItemStack proto) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && s.isSimilar(proto)) n += s.getAmount();
        }
        return n;
    }

    public static void remove(Inventory inv, ItemStack proto, int amount) {
        ItemStack[] c = inv.getStorageContents();
        for (int i = 0; i < c.length && amount > 0; i++) {
            ItemStack s = c[i];
            if (s == null || !s.isSimilar(proto)) continue;
            int take = Math.min(amount, s.getAmount());
            amount -= take;
            if (s.getAmount() - take <= 0) c[i] = null;
            else s.setAmount(s.getAmount() - take);
        }
        inv.setStorageContents(c);
    }

    public static String time(long secs) {
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m " + (secs % 60) + "s";
    }

    // ---------- ayudas para editores de menu ----------

    /** Item "fantasma": el del cursor o, con tecla numerica, el de la barra rapida. */
    public static ItemStack held(InventoryClickEvent e, Player p) {
        ItemStack cur = e.getCursor();
        if (!isEmpty(cur)) return cur;
        if (e.getClick() == ClickType.NUMBER_KEY) {
            ItemStack it = p.getInventory().getItem(e.getHotbarButton());
            if (!isEmpty(it)) return it;
        }
        return null;
    }

    /** Con item en el cursor lo copia; vacio: izq +1, der -1, Shift+der quita. */
    public static ItemStack edit(ItemStack current, ItemStack held, ClickType c) {
        if (held != null) return held.clone();
        if (current == null) return null;
        if (c == ClickType.SHIFT_RIGHT) return null;
        ItemStack n = current.clone();
        int amt = n.getAmount() + (c.isLeftClick() ? 1 : -1);
        n.setAmount(Math.max(1, Math.min(n.getMaxStackSize(), amt)));
        return n;
    }

    public static int step(ClickType c, int small, int big) {
        int mag = c.isShiftClick() ? big : small;
        return c.isRightClick() ? -mag : mag;
    }

    /** Izq/der +-1, Shift +-10, Q +100, Ctrl+Q -100. */
    public static double moneyStep(ClickType c) {
        return switch (c) {
            case DROP -> 100;
            case CONTROL_DROP -> -100;
            case SHIFT_LEFT -> 10;
            case SHIFT_RIGHT -> -10;
            case RIGHT -> -1;
            default -> 1;
        };
    }
}
