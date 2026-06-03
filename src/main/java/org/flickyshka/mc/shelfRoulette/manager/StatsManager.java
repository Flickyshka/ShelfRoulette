package org.flickyshka.mc.shelfRoulette.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import java.io.File;
import java.io.IOException;

public class StatsManager {

    private final ShelfRoulette plugin;
    private File file;
    private FileConfiguration config;

    private double moneyTaken = 0.0;
    private double moneyGiven = 0.0;

    public StatsManager(ShelfRoulette plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "stats.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        moneyTaken = config.getDouble("money-taken", 0.0);
        moneyGiven = config.getDouble("money-given", 0.0);
    }

    public void save() {
        config.set("money-taken", moneyTaken);
        config.set("money-given", moneyGiven);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public synchronized void addMoneyTaken(double amount) {
        this.moneyTaken += amount;
        saveAsync();
    }

    public synchronized void addMoneyGiven(double amount) {
        this.moneyGiven += amount;
        saveAsync();
    }

    public double getMoneyTaken() {
        return moneyTaken;
    }

    public double getMoneyGiven() {
        return moneyGiven;
    }
}
