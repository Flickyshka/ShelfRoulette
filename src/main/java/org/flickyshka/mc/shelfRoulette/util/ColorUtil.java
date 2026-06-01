package org.flickyshka.mc.shelfRoulette.util;
import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static boolean adventureAvailable = false;

    static {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            adventureAvailable = true;
        } catch (ClassNotFoundException e) {
            adventureAvailable = false;
        }
    }

    public static String format(String message) {
        if (message == null) {
            return "";
        }

        // 1. Обрабатываем MiniMessage через изолированный класс, если библиотека доступна
        if (adventureAvailable) {
            try {
                message = AdventureFormatter.formatMiniMessage(message);
            } catch (Throwable ignored) {}
        }

        // 2. Обрабатываем &#HEX форматирование
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of("#" + hexColor).toString());
        }
        matcher.appendTail(sb);

        // 3. Обрабатываем классические цвета &
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Изолированный статический внутренний класс для работы с Kyori Adventure.
    // JVM загрузит этот класс только при первом обращении к нему,
    // что предотвратит NoClassDefFoundError на серверах Spigot, где Kyori отсутствует.
    private static class AdventureFormatter {
        private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY_SERIALIZER =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                        .character('&')
                        .hexColors()
                        .useUnusualXRepeatedCharacterHexFormat()
                        .build();

        public static String formatMiniMessage(String message) {
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message);
            return LEGACY_SERIALIZER.serialize(component);
        }
    }

    public static String format(org.bukkit.entity.Player player, String message) {
        if (message == null) return "";
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            message = PAPIHook.setPlaceholders(player, message);
        }
        return format(message);
    }

    private static class PAPIHook {
        public static String setPlaceholders(org.bukkit.entity.Player player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
