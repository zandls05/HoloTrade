package com.holotrade;

import org.bukkit.Material;

public enum TradeType {
    BARTER("Trueque", Material.EMERALD, "§a",
            "Entregas lo que pide y recibes el objeto."),
    XP("Experiencia", Material.EXPERIENCE_BOTTLE, "§b",
            "Pagas niveles de XP y recibes el objeto."),
    MYSTERY("Misterio", Material.ENDER_CHEST, "§d",
            "Pagas y recibes un premio al azar del pozo."),
    DEMAND("Demanda", Material.GOLD_INGOT, "§6",
            "El precio sube con cada compra y baja con el tiempo.");

    public final String label;
    public final Material icon;
    public final String color;
    public final String description;

    TradeType(String label, Material icon, String color, String description) {
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.description = description;
    }

    public TradeType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public TradeType prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
