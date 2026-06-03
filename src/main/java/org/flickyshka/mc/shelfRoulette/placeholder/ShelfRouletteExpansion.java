package org.flickyshka.mc.shelfRoulette.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ShelfRouletteExpansion extends PlaceholderExpansion {

    private final ShelfRoulette plugin;
    private final DecimalFormat formatter;

    public ShelfRouletteExpansion(ShelfRoulette plugin) {
        this.plugin = plugin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        this.formatter = new DecimalFormat("#,##0", symbols);
    }

    @Override
    public String getIdentifier() {
        return "shelfroulette";
    }

    @Override
    public String getAuthor() {
        return "Flickyshka";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equalsIgnoreCase("money_taken")) {
            return String.format(Locale.US, "%.0f", plugin.getStatsManager().getMoneyTaken());
        }
        if (params.equalsIgnoreCase("money_given")) {
            return String.format(Locale.US, "%.0f", plugin.getStatsManager().getMoneyGiven());
        }
        if (params.equalsIgnoreCase("money_taken_formatted")) {
            return formatter.format(plugin.getStatsManager().getMoneyTaken());
        }
        if (params.equalsIgnoreCase("money_given_formatted")) {
            return formatter.format(plugin.getStatsManager().getMoneyGiven());
        }

        return null;
    }
}
