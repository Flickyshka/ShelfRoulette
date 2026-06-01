package org.flickyshka.mc.shelfRoulette.game;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;
import org.flickyshka.mc.shelfRoulette.manager.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class AbstractGame extends BukkitRunnable {

    protected final ShelfRoulette plugin;
    protected final Player player;
    protected final double betAmount;
    protected final Block casinoBlock;
    protected final List<Block> shelves;
    protected final List<Inventory> inventories;
    protected final ItemStack[][] originalContents;
    protected final ConfigManager.RouletteSetup setup;

    protected final Random random = new Random();
    protected int totalTicksPassed = 0;
    protected final int duration;
    protected int nextActionTick = 0;
    protected final ConfigManager configManager;

    protected boolean displayingResult = false;
    protected ItemStack winningItem = null;
    protected Block middleBlock = null;

    public AbstractGame(ShelfRoulette plugin, Player player, double betAmount, Block casinoBlock) {
        this.plugin = plugin;
        this.player = player;
        this.betAmount = betAmount;
        this.casinoBlock = casinoBlock;
        this.configManager = plugin.getConfigManager();
        this.setup = plugin.getConfigManager().getSetup(plugin.getShelvesManager().getSetupName(casinoBlock.getLocation()));
        
        this.shelves = plugin.getShelvesManager().findConnectedShelves(casinoBlock);
        if (!shelves.isEmpty()) {
            this.middleBlock = shelves.get((shelves.size() - 1) / 2);
        } else {
            this.middleBlock = casinoBlock;
        }
        
        this.inventories = new ArrayList<>();
        this.originalContents = new ItemStack[shelves.size()][];

        for (int i = 0; i < shelves.size(); i++) {
            Block b = shelves.get(i);
            if (b.getState() instanceof org.bukkit.block.Shelf) {
                org.bukkit.block.Shelf shelf = (org.bukkit.block.Shelf) b.getState();
                inventories.add(shelf.getInventory());
                
                ItemStack[] contents = shelf.getInventory().getContents();
                ItemStack[] copy = new ItemStack[contents.length];
                for (int j = 0; j < contents.length; j++) {
                    copy[j] = contents[j] == null ? null : contents[j].clone();
                }
                originalContents[i] = copy;
            }
        }

        this.duration = configManager.getAnimationDuration();
        
        // Скрываем голограммы
        for (Block b : shelves) {
            plugin.getShelvesManager().setIdleHologramVisible(b.getLocation(), false);
        }
        
        initializeGame();
    }

    public boolean isDisplayingResult() {
        return displayingResult;
    }

    public Player getPlayer() {
        return player;
    }

    public List<Block> getShelves() {
        return shelves;
    }

    protected abstract void initializeGame();

    protected ItemStack getItemAt(int slot) {
        int shelfIndex = slot / 3;
        int localSlot = slot % 3;
        return inventories.get(shelfIndex).getItem(localSlot);
    }

    protected void setItemAt(int slot, ItemStack item) {
        int shelfIndex = slot / 3;
        int localSlot = slot % 3;
        inventories.get(shelfIndex).setItem(localSlot, item);
    }

    public void restoreOriginalContents() {
        for (int i = 0; i < shelves.size(); i++) {
            if (inventories.size() > i) {
                inventories.get(i).setContents(originalContents[i]);
            }
        }
        for (Block b : shelves) {
            plugin.getShelvesManager().setIdleHologramVisible(b.getLocation(), true);
        }
        plugin.getActiveGames().remove(casinoBlock.getLocation());
    }

    protected abstract void determineResult();

    protected void applyWinnings() {
        if (winningItem == null) {
            configManager.playSound(middleBlock.getLocation(), "lose", "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
            String loseMsg = configManager.getMessage("lose").replace("{amount}", configManager.formatMoney(betAmount)).replace("{amount_commas}", configManager.formatMoneyCommas(betAmount));
            player.sendMessage(org.flickyshka.mc.shelfRoulette.util.ColorUtil.format(player, loseMsg));
            return;
        }

        double multiplier = setup.getMultiplier(winningItem);
        double winAmount = betAmount * multiplier;

        if (winAmount > 0) {
            plugin.getEconomyManager().deposit(player, winAmount);
            configManager.playSound(middleBlock.getLocation(), "win", "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
            String winMsg = configManager.getMessage("win").replace("{amount}", configManager.formatMoney(winAmount)).replace("{amount_commas}", configManager.formatMoneyCommas(winAmount));
            player.sendMessage(org.flickyshka.mc.shelfRoulette.util.ColorUtil.format(player, winMsg));
        } else {
            configManager.playSound(middleBlock.getLocation(), "lose", "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
            String loseMsg = configManager.getMessage("lose").replace("{amount}", configManager.formatMoney(betAmount)).replace("{amount_commas}", configManager.formatMoneyCommas(betAmount));
            player.sendMessage(org.flickyshka.mc.shelfRoulette.util.ColorUtil.format(player, loseMsg));
        }
    }
}
