package org.flickyshka.mc.shelfRoulette.listener;

import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShelfInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShelfListener implements Listener {

    private final ShelfRoulette plugin;
    private final Map<UUID, Long> guiCooldowns = new HashMap<>();

    public ShelfListener(ShelfRoulette plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Фикс дублирования клика (обрабатываем только основную руку)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getState() instanceof Shelf) {
                Location loc = block.getLocation();
                if (plugin.getShelvesManager().isRegistered(loc)) {
                    event.setCancelled(true); // Отменяем стандартное открытие полки
                    
                    Player player = event.getPlayer();
                    
                    // Находим все соединенные полки
                    List<Block> connected = plugin.getShelvesManager().findConnectedShelves(block);
                    
                    // Проверяем, идет ли игра на любой из соединенных полок
                    AbstractGame activeGame = null;
                    for (Block b : connected) {
                        AbstractGame g = plugin.getActiveGameAt(b.getLocation());
                        if (g != null) {
                            activeGame = g;
                            break;
                        }
                    }
                    
                    if (activeGame != null) {
                        if (activeGame.isDisplayingResult()) {
                            activeGame.restoreOriginalContents();
                        } else {
                            plugin.getConfigManager().sendMessage(player, plugin.getConfigManager().getMessage("game-in-progress"));
                            return;
                        }
                    }
                    
                    // Shift + ПКМ: Открыть меню изменения ставки
                    if (player.isSneaking()) {
                        plugin.getMenuManager().openMenu(player);
                    } else {
                        // Обычный ПКМ: Запуск рулетки!
                        startGame(player, block);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ShelfRouletteMenuHolder) {
            event.setCancelled(true);
            
            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                return;
            }
            
            if (event.getClickedInventory() == null || event.getClickedInventory().equals(event.getView().getBottomInventory())) {
                return;
            }
            
            Player player = (Player) event.getWhoClicked();
            
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            if (guiCooldowns.containsKey(uuid)) {
                long lastClick = guiCooldowns.get(uuid);
                if (now - lastClick < 150) {
                    return;
                }
            }
            guiCooldowns.put(uuid, now);
            
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null) {
                NamespacedKey commandKey = plugin.getMenuManager().getCommandKey();
                NamespacedKey shiftCommandKey = plugin.getMenuManager().getShiftCommandKey();
                
                NamespacedKey activeKey = commandKey;
                if (event.isShiftClick() && meta.getPersistentDataContainer().has(shiftCommandKey, PersistentDataType.STRING)) {
                    activeKey = shiftCommandKey;
                }

                if (meta.getPersistentDataContainer().has(activeKey, PersistentDataType.STRING)) {
                    String joined = meta.getPersistentDataContainer().get(activeKey, PersistentDataType.STRING);
                    if (joined != null && !joined.isEmpty()) {
                        String[] commands = joined.split(";");
                        boolean refresh = false;
                        for (String cmd : commands) {
                            cmd = cmd.trim();
                            if (cmd.equals("[close]")) {
                                player.closeInventory();
                                return;
                            } else if (cmd.startsWith("[+") && cmd.endsWith("]")) {
                                String amtStr = cmd.substring(2, cmd.length() - 1);
                                try {
                                    double amount = Double.parseDouble(amtStr);
                                    double currentBet = plugin.getPlayerBet(player.getUniqueId());
                                    double balance = plugin.getEconomyManager().getBalance(player);
                                    
                                    double newBet = Math.min(currentBet + amount, balance);
                                    newBet = Math.max(1.0, newBet);
                                    
                                    if (newBet > currentBet) {
                                        plugin.setPlayerBet(player.getUniqueId(), newBet);
                                        refresh = true;
                                        plugin.getConfigManager().playSound(player, "click", "UI_BUTTON_CLICK", 1.0f, 1.2f);
                                    } else {
                                        plugin.getConfigManager().playSound(player, "click", "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
                                    }
                                } catch (NumberFormatException ignored) {}
                            } else if (cmd.startsWith("[-") && cmd.endsWith("]")) {
                                String amtStr = cmd.substring(2, cmd.length() - 1);
                                try {
                                    double amount = Double.parseDouble(amtStr);
                                    double currentBet = plugin.getPlayerBet(player.getUniqueId());
                                    double newBet = Math.max(1.0, currentBet - amount);
                                    if (newBet < currentBet) {
                                        plugin.setPlayerBet(player.getUniqueId(), newBet);
                                        refresh = true;
                                        plugin.getConfigManager().playSound(player, "click", "UI_BUTTON_CLICK", 1.0f, 0.8f);
                                    } else {
                                        plugin.getConfigManager().playSound(player, "click", "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        if (refresh) {
                            plugin.getMenuManager().refreshMenu(player, event.getInventory());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShelfRouletteMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void startGame(Player player, Block block) {
        double betAmount = plugin.getPlayerBet(player.getUniqueId());
        ConfigManager configManager = plugin.getConfigManager();
        
        String setupName = plugin.getShelvesManager().getSetupName(block.getLocation());
        ConfigManager.RouletteSetup setup = configManager.getSetup(setupName);

        if (betAmount < setup.getMinBet()) {
            configManager.sendMessage(player, configManager.getMessage("bet-too-small")
                    .replace("{min_bet}", configManager.formatMoney(setup.getMinBet())));
            return;
        }
        
        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.hasEnough(player, betAmount)) {
            configManager.sendMessage(player, configManager.getMessage("not-enough-money"));
            return;
        }

        // Проверка лимита одновременных игр
        int maxGames = configManager.getMaxConcurrentGames();
        if (maxGames > 0) {
            int playerGames = 0;
            java.util.Set<AbstractGame> uniqueGames = new java.util.HashSet<>(plugin.getActiveGames().values());
            for (AbstractGame active : uniqueGames) {
                if (active.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    playerGames++;
                }
            }
            if (playerGames >= maxGames) {
                configManager.sendMessage(player, configManager.getMessage("max-concurrent-games-reached"));
                return;
            }
        }

        if (!economy.withdraw(player, betAmount)) {
            configManager.sendMessage(player, configManager.getMessage("error-withdraw"));
            return;
        }

        configManager.sendMessage(player, configManager.getMessage("bet-placed")
                .replace("{amount_commas}", configManager.formatMoneyCommas(betAmount))
                .replace("{amount}", configManager.formatMoney(betAmount)));

        AbstractGame game;
        if (setup.getGameMode().equals("SLOT")) {
            game = new SlotGame(plugin, player, betAmount, block);
        } else {
            game = new RouletteGame(plugin, player, betAmount, block);
        }

        for (Block b : plugin.getShelvesManager().findConnectedShelves(block)) {
            plugin.getActiveGames().put(b.getLocation(), game);
        }
        game.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.removePlayerBet(event.getPlayer().getUniqueId());
        guiCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() instanceof ShelfInventory) {
            Location loc = event.getInventory().getLocation();
            if (loc != null && plugin.getActiveGames().containsKey(loc)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() instanceof ShelfInventory) {
            Location loc = event.getInventory().getLocation();
            if (loc != null && plugin.getActiveGames().containsKey(loc)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getState() instanceof Shelf) {
            Location loc = event.getBlock().getLocation();
            if (plugin.getActiveGames().containsKey(loc)) {
                event.setCancelled(true);
                plugin.getConfigManager().sendMessage(event.getPlayer(), plugin.getConfigManager().getMessage("game-in-progress"));
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getSource() instanceof ShelfInventory) {
            Location loc = event.getSource().getLocation();
            if (loc != null && plugin.getActiveGames().containsKey(loc)) {
                event.setCancelled(true);
            }
        }
        if (event.getDestination() instanceof ShelfInventory) {
            Location loc = event.getDestination().getLocation();
            if (loc != null && plugin.getActiveGames().containsKey(loc)) {
                event.setCancelled(true);
            }
        }
    }
}
