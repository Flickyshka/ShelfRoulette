package org.flickyshka.mc.shelfRoulette.game;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;
import org.flickyshka.mc.shelfRoulette.manager.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public class RouletteGame extends AbstractGame {

    private final List<ItemStack> rouletteWheel = new ArrayList<>();
    private int wheelOffset = 0;
    private int totalSlots;

    public RouletteGame(ShelfRoulette plugin, Player player, double betAmount, Block casinoBlock) {
        super(plugin, player, betAmount, casinoBlock);
        initializeGame();
    }

    @Override
    protected void initializeGame() {
        List<ItemStack> baseWheel = new ArrayList<>();
        for (ConfigManager.RouletteItem rItem : setup.getRouletteItems()) {
            for (int i = 0; i < rItem.getWeight(); i++) {
                baseWheel.add(rItem.getItem());
            }
        }
        if (baseWheel.isEmpty()) {
            ItemStack fallback = setup.getRandomItem();
            if (fallback != null) baseWheel.add(fallback);
        }
        
        java.util.Collections.shuffle(baseWheel);

        totalSlots = shelves.size() * 3;
        int targetSize = totalSlots * 3;
        
        while (rouletteWheel.size() < targetSize) {
            rouletteWheel.addAll(baseWheel);
        }

        // Initialize display
        for (int slot = 0; slot < totalSlots; slot++) {
            int wheelIndex = (wheelOffset + slot) % rouletteWheel.size();
            setItemAt(slot, rouletteWheel.get(wheelIndex));
        }

        if (configManager.isSpinningEnabled()) {
            spawnHologram(configManager.getHologramSpinLines(player, betAmount), "spinning");
        }
    }

    @Override
    public void run() {
        if (totalTicksPassed >= duration) {
            this.cancel();
            determineResult();
            this.displayingResult = true;
            new BukkitRunnable() {
                @Override
                public void run() {
                    restoreOriginalContents();
                }
            }.runTaskLater(plugin, (long) configManager.getResultDisplayDuration());
            return;
        }

        if (totalTicksPassed >= nextActionTick) {
            if (!rouletteWheel.isEmpty()) {
                wheelOffset = (wheelOffset - 1 + rouletteWheel.size()) % rouletteWheel.size();
                for (int slot = 0; slot < totalSlots; slot++) {
                    int wheelIndex = (wheelOffset + slot) % rouletteWheel.size();
                    setItemAt(slot, rouletteWheel.get(wheelIndex));
                }
            }

            int delay = configManager.getAnimationSpeedTicks();
            if (configManager.isAnimationSlowdown()) {
                double progress = (double) totalTicksPassed / duration;
                if (progress > 0.5) {
                    double factor = (progress - 0.5) * 2.0;
                    int baseSlowdown = (int)(factor * factor * 15);
                    int randomJitter = -1 + random.nextInt(4);
                    delay += Math.max(0, baseSlowdown + randomJitter);
                }
            }
            
            configManager.playSound(middleBlock.getLocation(), "spin", "BLOCK_NOTE_BLOCK_HAT", 1.0f, 1.0f);
            nextActionTick = totalTicksPassed + delay;
        }

        totalTicksPassed++;
    }

    @Override
    protected void determineResult() {
        int middleSlotIndex = (totalSlots - 1) / 2;
        winningItem = getItemAt(middleSlotIndex);
        applyWinnings();
    }
}
