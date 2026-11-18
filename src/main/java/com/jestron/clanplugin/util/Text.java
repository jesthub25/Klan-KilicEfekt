package com.jestron.clanplugin.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk kodu çevirici. "&a" klasik kodların yanında "&#RRGGBB" ve "§#RRGGBB"
 * hex renklerini de 1.16+ formatına (§x§R§R§G§G§B§B) çevirir.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");

    private Text() {
    }

    public static String color(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        final Matcher matcher = HEX.matcher(input);
        final StringBuffer buffer = new StringBuffer(input.length() + 16);
        while (matcher.find()) {
            final String hex = matcher.group(1);
            final StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                replacement.append('§').append(Character.toLowerCase(hex.charAt(i)));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> color(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>(lines.size());
        for (final String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    public static String strip(final String input) {
        final String stripped = ChatColor.stripColor(color(input));
        return stripped == null ? "" : stripped;
    }

    public static String capitalize(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}
