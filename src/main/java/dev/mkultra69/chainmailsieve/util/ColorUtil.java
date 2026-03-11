package dev.mkultra69.chainmailsieve.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {
    }

    public static Component colorize(String text) {
        return LEGACY_SERIALIZER.deserialize(text);
    }
}

