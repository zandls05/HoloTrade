package com.holotrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Txt {
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();

    private Txt() {}

    /** Texto con codigos § para lore/nombres de item (sin cursiva por defecto). */
    public static Component c(String s) {
        return SEC.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Texto con codigos § para titulos, hologramas y chat. */
    public static Component t(String s) {
        return SEC.deserialize(s);
    }

    /** Texto con codigos & (config). */
    public static Component amp(String s) {
        return SEC.deserialize(s.replace('&', '§'));
    }

    public static String legacy(Component c) {
        return SEC.serialize(c);
    }
}
