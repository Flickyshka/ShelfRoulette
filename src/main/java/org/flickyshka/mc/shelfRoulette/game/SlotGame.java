package org.flickyshka.mc.shelfRoulette.game;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

public class SlotGame extends AbstractGame {

    private int totalSlots;
    private int[] slotStopTicks;

    public SlotGame(ShelfRoulette plugin, Player player, double betAmount, Block casinoBlock) {
        super(plugin, player, betAmount, casinoBlock);
    }

    @Override
    protected void initializeGame() {
        totalSlots = shelves.size() * 3;
        slotStopTicks = new int[totalSlots];

        int spinPart = duration / 2;
        int stopPart = duration - spinPart;
        for (int slot = 0; slot < totalSlots; slot++) {
            int randomJitter = -5 + random.nextInt(11);
            int baseStop = spinPart + (stopPart / totalSlots) * (slot + 1);
            int stopTick = Math.min(duration, Math.max(spinPart, baseStop + randomJitter));
            if (slot == totalSlots - 1) {
                stopTick = duration;
            }
            slotStopTicks[slot] = stopTick;
            
            ItemStack randomItem = setup.getRandomItem();
            if (randomItem != null) {
                setItemAt(slot, randomItem);
            }
        }
    }

    @Override
    public void run() {
        for (int slot = 0; slot < totalSlots; slot++) {
            if (totalTicksPassed == slotStopTicks[slot]) {
                configManager.playSound(middleBlock.getLocation(), "stop", "BLOCK_NOTE_BLOCK_BELL", 1.0f, 1.2f);
            }
        }

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
            
            for (int slot = 0; slot < totalSlots; slot++) {
                if (totalTicksPassed < slotStopTicks[slot]) {
                    ItemStack currentItem = getItemAt(slot);
                    ItemStack randomItem = setup.getRandomItem();
                    
                    boolean isLastTick = (totalTicksPassed + delay >= slotStopTicks[slot]);
                    if (!isLastTick) {
                        int attempts = 0;
                        while (randomItem != null && currentItem != null && randomItem.isSimilar(currentItem) && attempts < 10) {
                            randomItem = setup.getRandomItem();
                            attempts++;
                        }
                    }
                    if (randomItem != null) {
                        setItemAt(slot, randomItem);
                    }
                }
            }

            configManager.playSound(middleBlock.getLocation(), "spin", "BLOCK_NOTE_BLOCK_HAT", 1.0f, 1.0f);
            nextActionTick = totalTicksPassed + delay;
        }

        totalTicksPassed++;
    }

    @Override
    protected void determineResult() {
        boolean won = false;
        ItemStack wonItem = null;
        for (int i = 0; i < shelves.size(); i++) {
            int baseSlot = i * 3;
            ItemStack i1 = getItemAt(baseSlot);
            ItemStack i2 = getItemAt(baseSlot + 1);
            ItemStack i3 = getItemAt(baseSlot + 2);
            if (i1 != null && i2 != null && i3 != null) {
                if (i1.isSimilar(i2) && i2.isSimilar(i3)) {
                    won = true;
                    wonItem = i1;
                    break;
                }
            }
        }
        
        this.winningItem = won ? wonItem : null;
        applyWinnings();
    }
}
