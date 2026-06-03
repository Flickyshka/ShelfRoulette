package org.flickyshka.mc.shelfRoulette;

import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.placeholder.ShelfRouletteExpansion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class ShelfRoulette extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    private final Map<Location, AbstractGame> activeGames = new HashMap<>();
    private final Map<UUID, Double> playerBets = new HashMap<>();
    private final Map<Location, GameState> savedStates = new HashMap<>();
    private EconomyManager economyManager;
    private ConfigManager configManager;
    private ShelvesManager shelvesManager;
    private MenuManager menuManager;
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        // Setup Config
        configManager = new ConfigManager(this);
        
        // Setup Shelves
        shelvesManager = new ShelvesManager(this);
        
        // Setup Menu
        menuManager = new MenuManager(this);
        
        // Setup Stats
        statsManager = new StatsManager(this);
        
        // Setup Economy
        economyManager = new EconomyManager();
        if (!economyManager.setupEconomy()) {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ShelfRouletteExpansion(this).register();
        }
        
        // Register safety listener
        getServer().getPluginManager().registerEvents(new ShelfListener(this), this);
        
        // Register our commands
        ShelfRouletteCommand cmdExecutor = new ShelfRouletteCommand(this);
        this.getCommand("shelfroulette").setExecutor(cmdExecutor);
        this.getCommand("shelfroulette").setTabCompleter(cmdExecutor);
    }

    @Override
    public void onDisable() {
        if (shelvesManager != null) {
            shelvesManager.clearPermanentArrows();
        }
        // Restore all active games' original items to avoid item loss
        for (AbstractGame game : new java.util.HashSet<>(activeGames.values())) {
            game.restoreOriginalContents();
        }
        activeGames.clear();
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    public void reloadPlugin() {
        configManager.reload();
        menuManager.reload();
        shelvesManager.load();
    }

    public AbstractGame getActiveGameAt(Location loc) {
        return activeGames.get(loc);
    }

    public Map<Location, AbstractGame> getActiveGames() {
        return activeGames;
    }

    public GameState getSavedState(Location loc) {
        return savedStates.computeIfAbsent(loc, k -> new GameState());
    }

    public void clearSavedState(Location loc) {
        savedStates.remove(loc);
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public StatsManager getStatsManager() {
        return statsManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShelvesManager getShelvesManager() {
        return shelvesManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public double getPlayerBet(UUID uuid) {
        return playerBets.getOrDefault(uuid, configManager.getDefaultBet());
    }

    public void setPlayerBet(UUID uuid, double bet) {
        playerBets.put(uuid, bet);
    }

    public void removePlayerBet(UUID uuid) {
        playerBets.remove(uuid);
    }
}
