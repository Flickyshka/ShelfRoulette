package org.flickyshka.mc.shelfRoulette.command;
import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Shelf;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.command.TabCompleter;

public class ShelfRouletteCommand implements CommandExecutor, TabCompleter {

    private final ShelfRoulette plugin;
    private final ConfigManager configManager;

    public ShelfRouletteCommand(ShelfRoulette plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shelfroulette.admin")) {
                configManager.sendMessage(sender, configManager.getMessage("no-permission"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(ColorUtil.format("&aКонфигурация плагина успешно перезагружена!"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Эту команду могут использовать только игроки.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("shelfroulette.admin")) {
            configManager.sendMessage(player, configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("register")) {
            Block block = player.getTargetBlock(null, 5);
            if (!(block.getState() instanceof Shelf)) {
                configManager.sendMessage(player, configManager.getMessage("not-a-shelf"));
                return true;
            }
            
            String setupName = "default";
            if (args.length > 1) {
                String baseMode = args[1].toLowerCase();
                if (args.length > 2) {
                    setupName = baseMode + ":" + args[2].toLowerCase();
                } else {
                    setupName = baseMode;
                }
                
                if (!configManager.getSetupNames().contains(setupName)) {
                    java.util.List<String> available = new java.util.ArrayList<>();
                    for(String sn : configManager.getSetupNames()) {
                        if (sn.startsWith(baseMode + ":")) {
                            available.add(sn.split(":")[1]);
                        }
                    }
                    if (!available.isEmpty()) {
                        player.sendMessage(ColorUtil.format("&cУкажите подтип для " + baseMode + ": " + String.join(", ", available)));
                    } else {
                        player.sendMessage(ColorUtil.format("&cУказанный тип рулетки \"" + setupName + "\" не найден в конфиге!"));
                    }
                    return true;
                }
            }
            
            if (plugin.getShelvesManager().register(block.getLocation(), setupName)) {
                configManager.sendMessage(player, configManager.getMessage("registered-success").replace("{setup}", setupName));
            } else {
                configManager.sendMessage(player, configManager.getMessage("already-registered"));
            }
            return true;
        } else if (subCommand.equals("unregister")) {
            Block block = player.getTargetBlock(null, 5);
            if (!(block.getState() instanceof Shelf)) {
                configManager.sendMessage(player, configManager.getMessage("not-a-shelf"));
                return true;
            }
            if (plugin.getShelvesManager().unregister(block.getLocation())) {
                configManager.sendMessage(player, configManager.getMessage("unregistered-success"));
            } else {
                configManager.sendMessage(player, configManager.getMessage("not-registered-shelf"));
            }
            return true;
        } else if (subCommand.equals("list")) {
            player.sendMessage(ChatColor.GOLD + "=== Зарегистрированные рулетки ===");
            for (org.bukkit.Location loc : plugin.getShelvesManager().getRegisteredShelves()) {
                player.sendMessage(ChatColor.YELLOW + "- Мир: " + loc.getWorld().getName() + " X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ());
            }
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.format("&6=== Управление ShelfRoulette ==="));
        sender.sendMessage(ColorUtil.format("&e/sr register &7- Зарегистрировать полку"));
        sender.sendMessage(ColorUtil.format("&e/sr unregister &7- Удалить полку из списка"));
        sender.sendMessage(ColorUtil.format("&e/sr list &7- Список всех рулеток"));
        sender.sendMessage(ColorUtil.format("&e/sr reload &7- Перезагрузить конфигурацию плагина"));
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (!sender.hasPermission("shelfroulette.admin")) {
            return completions;
        }

        if (args.length == 1) {
            java.util.List<String> subCommands = java.util.Arrays.asList("register", "unregister", "list", "reload");
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("register")) {
            String input = args[1].toLowerCase();
            for (String setupName : configManager.getSetupNames()) {
                String base = setupName.split(":")[0];
                if (base.startsWith(input) && !completions.contains(base)) {
                    completions.add(base);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("register")) {
            String baseMode = args[1].toLowerCase();
            String input = args[2].toLowerCase();
            
            for (String setupName : configManager.getSetupNames()) {
                if (setupName.startsWith(baseMode + ":")) {
                    String subtype = setupName.split(":")[1];
                    if (subtype.startsWith(input)) {
                        completions.add(subtype);
                    }
                }
            }
        }
        return completions;
    }
}
