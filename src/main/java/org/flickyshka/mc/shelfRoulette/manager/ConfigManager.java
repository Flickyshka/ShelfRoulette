package org.flickyshka.mc.shelfRoulette.manager;
import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ConfigManager {

    public static class RouletteItem {
        private final ItemStack item;
        private final double multiplier;
        private final int weight;

        public RouletteItem(ItemStack item, double multiplier, int weight) {
            this.item = item;
            this.multiplier = multiplier;
            this.weight = weight;
        }

        public ItemStack getItem() {
            return item.clone();
        }

        public double getMultiplier() {
            return multiplier;
        }

        public int getWeight() {
            return weight;
        }
    }

    public static class RouletteSetup {
        private final String name;
        private final String gameMode;
        private final List<RouletteItem> rouletteItems = new ArrayList<>();
        private int totalWeight = 0;
        private final Random random = new Random();

        public RouletteSetup(String name, String gameMode) {
            this.name = name;
            this.gameMode = gameMode;
        }

        public String getName() {
            return name;
        }

        public String getGameMode() {
            return gameMode;
        }

        public List<RouletteItem> getRouletteItems() {
            return rouletteItems;
        }

        public int getTotalWeight() {
            return totalWeight;
        }

        public void addRouletteItem(RouletteItem item) {
            rouletteItems.add(item);
            totalWeight += item.getWeight();
        }

        public ItemStack getRandomItem() {
            if (rouletteItems.isEmpty()) {
                return null;
            }
            int r = random.nextInt(totalWeight);
            int cumulative = 0;
            for (RouletteItem rItem : rouletteItems) {
                cumulative += rItem.getWeight();
                if (r < cumulative) {
                    return rItem.getItem();
                }
            }
            return rouletteItems.get(0).getItem();
        }

        public double getMultiplier(ItemStack itemStack) {
            if (itemStack == null || itemStack.getType().isAir()) return 0.0;
            for (RouletteItem rItem : rouletteItems) {
                if (rItem.getItem().isSimilar(itemStack)) {
                    return rItem.getMultiplier();
                }
            }
            // Запасной вариант для обычных предметов без меты (кроме голов)
            for (RouletteItem rItem : rouletteItems) {
                if (rItem.getItem().getType() == itemStack.getType() && itemStack.getType() != Material.PLAYER_HEAD) {
                    return rItem.getMultiplier();
                }
            }
            return 0.0;
        }
    }

    private final ShelfRoulette plugin;
    private FileConfiguration config;

    private int minAnimationDuration;
    private int maxAnimationDuration;
    private int animationSpeedTicks;
    private int resultDisplayDuration;
    private double defaultBet;
    private String moneyFormat;
    private Map<String, String> messages = new HashMap<>();
    
    private final Map<String, RouletteSetup> setups = new HashMap<>();

    public ConfigManager(ShelfRoulette plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig(); // Сохраняет config.yml, если его нет
        plugin.reloadConfig(); // Перезагружает конфиг
        this.config = plugin.getConfig();
        loadConfig();
    }

    private void loadConfig() {
        if (config.isString("animation-duration")) {
            String durStr = config.getString("animation-duration");
            if (durStr != null && durStr.contains("-")) {
                String[] parts = durStr.split("-");
                try {
                    minAnimationDuration = Integer.parseInt(parts[0].trim());
                    maxAnimationDuration = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    minAnimationDuration = 60;
                    maxAnimationDuration = 60;
                }
            } else {
                try {
                    minAnimationDuration = Integer.parseInt(durStr);
                    maxAnimationDuration = minAnimationDuration;
                } catch (NumberFormatException e) {
                    minAnimationDuration = 60;
                    maxAnimationDuration = 60;
                }
            }
        } else {
            minAnimationDuration = config.getInt("animation-duration", 60);
            maxAnimationDuration = minAnimationDuration;
        }
        animationSpeedTicks = config.getInt("animation-speed-ticks", 3);
        resultDisplayDuration = config.getInt("result-display-duration", 20);
        defaultBet = config.getDouble("default-bet", 10.0);

        // Формат денег
        moneyFormat = config.getString("money-format", "#.##");

        // Загрузка сообщений
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                if (messagesSection.isList(key)) {
                    java.util.List<String> list = messagesSection.getStringList(key);
                    String joined = String.join("\n", list);
                    messages.put(key, ColorUtil.format(joined));
                } else {
                    messages.put(key, ColorUtil.format(messagesSection.getString(key, "")));
                }
            }
        }

        setups.clear();
        loadSetupsFromSection("roulette-items", "ROULETTE", "default");
        loadSetupsFromSection("slot-items", "SLOT", "slot");
    }

    private void loadSetupsFromSection(String sectionName, String mode, String defaultName) {
        if (config.isConfigurationSection(sectionName)) {
            ConfigurationSection sec = config.getConfigurationSection(sectionName);
            boolean hasSubTypes = false;
            for (String key : sec.getKeys(false)) {
                ConfigurationSection child = sec.getConfigurationSection(key);
                if (child != null && !child.contains("multiplier") && !child.contains("weight")) {
                    hasSubTypes = true;
                    break;
                }
            }
            
            if (hasSubTypes) {
                for (String typeKey : sec.getKeys(false)) {
                    ConfigurationSection typeSec = sec.getConfigurationSection(typeKey);
                    if (typeSec != null) {
                        String fullName = defaultName + ":" + typeKey.toLowerCase();
                        RouletteSetup setup = new RouletteSetup(fullName, mode);
                        loadItemsIntoSetup(setup, typeSec);
                        setups.put(fullName, setup);
                    }
                }
            } else {
                RouletteSetup setup = new RouletteSetup(defaultName, mode);
                loadItemsIntoSetup(setup, sec);
                setups.put(defaultName, setup);
            }
        }
    }

    private void loadItemsIntoSetup(RouletteSetup setup, ConfigurationSection itemsSection) {
        for (String key : itemsSection.getKeys(false)) {
            try {
                ItemStack itemStack;
                if (key.startsWith("base64:")) {
                    itemStack = createSkull(key.substring(7));
                } else {
                    Material material = Material.valueOf(key.toUpperCase());
                    itemStack = new ItemStack(material);
                }
                
                double multiplier;
                int weight;
                
                if (itemsSection.isConfigurationSection(key)) {
                    ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
                    multiplier = itemConfig.getDouble("multiplier", 1.0);
                    weight = itemConfig.getInt("weight", 1);
                } else {
                    multiplier = itemsSection.getDouble(key);
                    weight = 10;
                }
                
                if (weight <= 0) {
                    weight = 1;
                }
                
                setup.addRouletteItem(new RouletteItem(itemStack, multiplier, weight));
            } catch (Exception e) {
                plugin.getLogger().warning("Неверный материал или ошибка base64 в config.yml: " + key);
            }
        }
    }

    private ItemStack createSkull(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();
        if (meta != null) {
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(base64.getBytes());
            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(uuid);
            try {
                String decoded = new String(java.util.Base64.getDecoder().decode(base64));
                String url = null;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(decoded);
                if (m.find()) {
                    url = m.group(1);
                    org.bukkit.profile.PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new java.net.URL(url));
                    profile.setTextures(textures);
                    meta.setOwnerProfile(profile);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при загрузке base64 текстуры.");
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }

    public int getAnimationDuration() {
        if (maxAnimationDuration <= minAnimationDuration) {
            return minAnimationDuration;
        }
        return minAnimationDuration + new java.util.Random().nextInt((maxAnimationDuration - minAnimationDuration) + 1);
    }

    public int getAnimationSpeedTicks() {
        return animationSpeedTicks;
    }

    public boolean isAnimationSlowdown() {
        return config.getBoolean("animation-slowdown", true);
    }

    public int getResultDisplayDuration() {
        return resultDisplayDuration;
    }

    public int getMaxConcurrentGames() {
        return config.getInt("max-concurrent-games", 1);
    }

    public double getDefaultBet() {
        return defaultBet;
    }

    public String formatMoney(double amount) {
        java.text.DecimalFormat df = new java.text.DecimalFormat(moneyFormat);
        df.setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        return df.format(amount);
    }

    public String formatMoneyCommas(double amount) {
        java.text.DecimalFormat df = new java.text.DecimalFormat(moneyFormat);
        df.setDecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        df.setGroupingUsed(true);
        df.setGroupingSize(3);
        return df.format(amount);
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String message) {
        if (message != null && !message.trim().isEmpty()) {
            if (message.contains("\n")) {
                for (String line : message.split("\n")) {
                    sender.sendMessage(line);
                }
            } else {
                sender.sendMessage(message);
            }
        }
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "Сообщение не найдено: " + key);
    }

    public void playSound(Location loc, String soundKey, String defaultSoundName, float defaultVolume, float defaultPitch) {
        String soundStr = config.getString("sounds." + soundKey + ".sound", defaultSoundName);
        double volume = config.getDouble("sounds." + soundKey + ".volume", defaultVolume);
        double pitch = config.getDouble("sounds." + soundKey + ".pitch", defaultPitch);
        try {
            Sound sound = Sound.valueOf(soundStr.toUpperCase());
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, sound, (float) volume, (float) pitch);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный звук в config.yml: " + soundStr);
        }
    }

    public void playSound(org.bukkit.entity.Player player, String soundKey, String defaultSoundName, float defaultVolume, float defaultPitch) {
        String soundStr = config.getString("sounds." + soundKey + ".sound", defaultSoundName);
        double volume = config.getDouble("sounds." + soundKey + ".volume", defaultVolume);
        double pitch = config.getDouble("sounds." + soundKey + ".pitch", defaultPitch);
        try {
            Sound sound = Sound.valueOf(soundStr.toUpperCase());
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный звук в config.yml: " + soundStr);
        }
    }

    public List<RouletteItem> getRouletteItems() {
        return getSetup("default").getRouletteItems();
    }

    public ItemStack getRandomItem() {
        return getSetup("default").getRandomItem();
    }

    public double getMultiplier(ItemStack item) {
        return getSetup("default").getMultiplier(item);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadConfig();
    }

    public boolean isArrowsEnabled() {
        String top = config.getString("hologram.top-arrow", "");
        String bottom = config.getString("hologram.bottom-arrow", "");
        return !(top.isEmpty() && bottom.isEmpty());
    }

    public String getHologramType(String category) {
        return config.getString("hologram.types." + category, "TEXT_DISPLAY").toUpperCase();
    }

    public boolean isIdleEnabled(String mode) {
        String path = "SLOT".equalsIgnoreCase(mode) ? "hologram.lines.idle-slot" : "hologram.lines.idle-roulette";
        if (!config.contains(path) && config.contains("hologram.lines.idle")) {
            path = "hologram.lines.idle";
        }
        return !config.getStringList(path).isEmpty();
    }

    public boolean isSpinningEnabled() {
        return !config.getStringList("hologram.lines.spinning").isEmpty();
    }

    public boolean isWinEnabled() {
        return !config.getStringList("hologram.lines.win").isEmpty();
    }

    public boolean isLoseEnabled() {
        return !config.getStringList("hologram.lines.lose").isEmpty();
    }

    public String getTopArrow() {
        return ColorUtil.format(config.getString("hologram.top-arrow", ""));
    }

    public String getBottomArrow() {
        return ColorUtil.format(config.getString("hologram.bottom-arrow", ""));
    }

    public String getGameMode() {
        return getSetup("default").getGameMode();
    }

    public RouletteSetup getSetup(String name) {
        if (!setups.containsKey(name) && !setups.isEmpty()) {
            for (String key : setups.keySet()) {
                if (key.startsWith(name + ":")) return setups.get(key);
            }
            return setups.values().iterator().next();
        }
        return setups.get(name);
    }

    public java.util.Collection<String> getSetupNames() {
        return setups.keySet();
    }

    public double getGameOffsetX() {
        return config.getDouble("hologram.offsets.game.x", 0.0);
    }

    public double getGameOffsetY() {
        return config.getDouble("hologram.offsets.game.y", 1.5);
    }

    public double getGameOffsetZ() {
        return config.getDouble("hologram.offsets.game.z", 0.0);
    }

    public double getTopOffsetX() {
        return config.getDouble("hologram.offsets.top.x", 0.0);
    }

    public double getTopOffsetY() {
        return config.getDouble("hologram.offsets.top.y", 1.0);
    }

    public double getTopOffsetZ() {
        return config.getDouble("hologram.offsets.top.z", 0.0);
    }

    public double getBottomOffsetX() {
        return config.getDouble("hologram.offsets.bottom.x", 0.0);
    }

    public double getBottomOffsetY() {
        return config.getDouble("hologram.offsets.bottom.y", -0.4);
    }

    public double getBottomOffsetZ() {
        return config.getDouble("hologram.offsets.bottom.z", 0.0);
    }

    public List<String> getHologramIdleLines(String mode) {
        String path = "SLOT".equalsIgnoreCase(mode) ? "hologram.lines.idle-slot" : "hologram.lines.idle-roulette";
        if (!config.contains(path) && config.contains("hologram.lines.idle")) {
            path = "hologram.lines.idle";
        }
        List<String> list = config.getStringList(path);
        List<String> resolved = new ArrayList<>();
        for (String line : list) {
            resolved.add(ColorUtil.format(line));
        }
        return resolved;
    }

    public List<String> getHologramSpinLines(org.bukkit.entity.Player player, double bet) {
        List<String> list = config.getStringList("hologram.lines.spinning");
        List<String> resolved = new ArrayList<>();
        for (String line : list) {
            line = line.replace("%player%", player.getName())
                       .replace("%bet_commas%", formatMoneyCommas(bet))
                       .replace("%bet%", formatMoney(bet));
            resolved.add(ColorUtil.format(player, line));
        }
        return resolved;
    }

    public List<String> getHologramWinLines(org.bukkit.entity.Player player, double bet, double winnings) {
        List<String> list = config.getStringList("hologram.lines.win");
        List<String> resolved = new ArrayList<>();
        for (String line : list) {
            line = line.replace("%player%", player.getName())
                       .replace("%bet_commas%", formatMoneyCommas(bet))
                       .replace("%bet%", formatMoney(bet))
                       .replace("%winnings_commas%", formatMoneyCommas(winnings))
                       .replace("%winnings%", formatMoney(winnings));
            resolved.add(ColorUtil.format(player, line));
        }
        return resolved;
    }

    public List<String> getHologramLoseLines(org.bukkit.entity.Player player, double bet) {
        List<String> list = config.getStringList("hologram.lines.lose");
        List<String> resolved = new ArrayList<>();
        for (String line : list) {
            line = line.replace("%player%", player.getName())
                       .replace("%bet%", formatMoney(bet))
                       .replace("%bet_commas%", formatMoneyCommas(bet));
            resolved.add(ColorUtil.format(player, line));
        }
        return resolved;
    }
}
