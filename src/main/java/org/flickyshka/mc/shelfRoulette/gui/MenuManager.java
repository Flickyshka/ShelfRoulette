package org.flickyshka.mc.shelfRoulette.gui;
import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MenuManager {

    private final ShelfRoulette plugin;
    private File file;
    private FileConfiguration config;
    private String menuTitle;
    private int size;
    private final NamespacedKey commandKey;
    private final NamespacedKey shiftCommandKey;

    public MenuManager(ShelfRoulette plugin) {
        this.plugin = plugin;
        this.commandKey = new NamespacedKey(plugin, "gui-commands");
        this.shiftCommandKey = new NamespacedKey(plugin, "gui-shift-commands");
        setup();
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        file = new File(plugin.getDataFolder(), "menu.yml");
        if (!file.exists()) {
            // Сохраняем дефолтный menu.yml из ресурсов, если его нет
            plugin.saveResource("menu.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        
        menuTitle = ColorUtil.format(config.getString("menu_title", "&0Изменение ставки"));
        size = config.getInt("size", 9);
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        menuTitle = ColorUtil.format(config.getString("menu_title", "&0Изменение ставки"));
        size = config.getInt("size", 9);
    }

    public void openMenu(Player player) {
        String formattedTitle = ColorUtil.format(player, config.getString("menu_title", "&0Изменение ставки"));
        Inventory gui = Bukkit.createInventory(new ShelfRouletteMenuHolder(), size, formattedTitle);
        double currentBet = plugin.getPlayerBet(player.getUniqueId());

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", 0);
                if (slot < 0 || slot >= size) continue;

                String matName = itemSec.getString("material", "STONE");
                Material material;
                try {
                    material = Material.valueOf(matName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    material = Material.STONE;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    // Замена плейсхолдеров
                    String displayName = itemSec.getString("display_name", "");
                    displayName = displayName.replace("%current_bet_commas%", plugin.getConfigManager().formatMoneyCommas(currentBet));
                    displayName = displayName.replace("%current_bet%", plugin.getConfigManager().formatMoney(currentBet));
                    meta.setDisplayName(ColorUtil.format(player, displayName));

                    List<String> rawLore = itemSec.getStringList("lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : rawLore) {
                        line = line.replace("%current_bet_commas%", plugin.getConfigManager().formatMoneyCommas(currentBet));
                        line = line.replace("%current_bet%", plugin.getConfigManager().formatMoney(currentBet));
                        lore.add(ColorUtil.format(player, line));
                    }
                    meta.setLore(lore);

                    // Сохранение команд во внутреннюю память предмета (PDC)
                    List<String> clickCommands = itemSec.getStringList("click_commands");
                    if (!clickCommands.isEmpty()) {
                        String joined = String.join(";", clickCommands);
                        meta.getPersistentDataContainer().set(commandKey, PersistentDataType.STRING, joined);
                    }

                    List<String> shiftClickCommands = itemSec.getStringList("shift_click_commands");
                    if (!shiftClickCommands.isEmpty()) {
                        String joinedShift = String.join(";", shiftClickCommands);
                        meta.getPersistentDataContainer().set(shiftCommandKey, PersistentDataType.STRING, joinedShift);
                    }

                    item.setItemMeta(meta);
                }
                gui.setItem(slot, item);
            }
        }

        player.openInventory(gui);
    }

    public void refreshMenu(Player player, Inventory gui) {
        double currentBet = plugin.getPlayerBet(player.getUniqueId());

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", 0);
                if (slot < 0 || slot >= size) continue;

                ItemStack item = gui.getItem(slot);
                if (item == null || item.getType() == Material.AIR) continue;

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String displayName = itemSec.getString("display_name", "");
                    displayName = displayName.replace("%current_bet_commas%", plugin.getConfigManager().formatMoneyCommas(currentBet));
                    displayName = displayName.replace("%current_bet%", plugin.getConfigManager().formatMoney(currentBet));
                    meta.setDisplayName(ColorUtil.format(player, displayName));

                    List<String> rawLore = itemSec.getStringList("lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : rawLore) {
                        line = line.replace("%current_bet_commas%", plugin.getConfigManager().formatMoneyCommas(currentBet));
                        line = line.replace("%current_bet%", plugin.getConfigManager().formatMoney(currentBet));
                        lore.add(ColorUtil.format(player, line));
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    public String getMenuTitle() {
        return menuTitle;
    }

    public NamespacedKey getCommandKey() {
        return commandKey;
    }

    public NamespacedKey getShiftCommandKey() {
        return shiftCommandKey;
    }
}
