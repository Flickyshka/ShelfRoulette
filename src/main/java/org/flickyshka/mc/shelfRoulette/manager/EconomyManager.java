package org.flickyshka.mc.shelfRoulette.manager;
import org.flickyshka.mc.shelfRoulette.util.*;
import org.flickyshka.mc.shelfRoulette.manager.*;
import org.flickyshka.mc.shelfRoulette.listener.*;
import org.flickyshka.mc.shelfRoulette.command.*;
import org.flickyshka.mc.shelfRoulette.gui.*;
import org.flickyshka.mc.shelfRoulette.game.*;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import static org.bukkit.Bukkit.getServer;

public class EconomyManager {

    private Economy economy;

    public boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasEnough(Player player, double amount) {
        return economy.has(player, amount);
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
}
