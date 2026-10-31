package pl.anarchia.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.Locale;

public final class Util {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Util() {}

    /** Tekst z kolorami & -> string z kodami § (do wiadomosci na czacie). */
    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** Tekst z kolorami & -> Component. */
    public static Component comp(String s) {
        return LEGACY.deserialize(s == null ? "" : s);
    }

    /** Component bez domyslnej kursywy (do nazw i opisow przedmiotow). */
    public static Component item(String s) {
        return comp(s).decoration(TextDecoration.ITALIC, false);
    }

    /** 1500 -> 1.5k, 2500000 -> 2.5M */
    public static String shortNum(long v) {
        if (v >= 1_000_000) return trim(v / 1_000_000.0) + "M";
        if (v >= 1_000) return trim(v / 1_000.0) + "k";
        return String.valueOf(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
