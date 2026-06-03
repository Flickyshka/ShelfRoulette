package org.flickyshka.mc.shelfRoulette.game;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.flickyshka.mc.shelfRoulette.ShelfRoulette;

public class SlotGame extends AbstractGame {

    private int totalSlots;
    private int[] slotStopTicks;
    private java.util.List<ItemStack>[] reels;
    private int[] reelIndices;

    public SlotGame(ShelfRoulette plugin, Player player, double betAmount, Block casinoBlock) {
        super(plugin, player, betAmount, casinoBlock);
        initializeGame();
    }

    @Override
    protected void initializeGame() {
        GameState state = plugin.getSavedState(casinoBlock.getLocation());
        totalSlots = shelves.size() * 3;
        slotStopTicks = new int[totalSlots];
        
        reels = new java.util.List[totalSlots];
        reelIndices = new int[totalSlots];

        int randomizeInterval = configManager.getRandomizeEverySpins();
        boolean reuseState = (state.reels != null && state.reels.length == totalSlots && state.spinCount < randomizeInterval && configManager.isStartFromLastPosition());

        int spinPart = duration / 2;
        int stopPart = duration - spinPart;

        if (reuseState) {
            state.spinCount++;
            for (int slot = 0; slot < totalSlots; slot++) {
                reels[slot] = new java.util.ArrayList<>(state.reels[slot]);
                reelIndices[slot] = state.reelIndices[slot];
                
                int randomJitter = -5 + random.nextInt(11);
                int baseStop = spinPart + (stopPart / totalSlots) * (slot + 1);
                int stopTick = Math.min(duration, Math.max(spinPart, baseStop + randomJitter));
                if (slot == totalSlots - 1) {
                    stopTick = duration;
                }
                slotStopTicks[slot] = stopTick;
                
                if (!reels[slot].isEmpty()) {
                    setItemAt(slot, reels[slot].get(reelIndices[slot]));
                }
            }
        } else {
            state.spinCount = 1;
            state.reels = new java.util.List[totalSlots];
            state.reelIndices = new int[totalSlots];
            
            for (int slot = 0; slot < totalSlots; slot++) {
                int randomJitter = -5 + random.nextInt(11);
                int baseStop = spinPart + (stopPart / totalSlots) * (slot + 1);
                int stopTick = Math.min(duration, Math.max(spinPart, baseStop + randomJitter));
                if (slot == totalSlots - 1) {
                    stopTick = duration;
                }
                slotStopTicks[slot] = stopTick;
                
                reels[slot] = new java.util.ArrayList<>();
                ItemStack lastItem = null;
                int reelSize = 40; // Length of the physical reel
                for (int i = 0; i < reelSize; i++) {
                    ItemStack nextItem = setup.getRandomItem();
                    int attempts = 0;
                    while (nextItem != null && attempts < 20) {
                        boolean conflict = (lastItem != null && nextItem.isSimilar(lastItem));
                        // Prevent same item wrapping around the reel
                        if (i == reelSize - 1 && !reels[slot].isEmpty() && nextItem.isSimilar(reels[slot].get(0))) {
                            conflict = true;
                        }
                        if (!conflict) break;
                        nextItem = setup.getRandomItem();
                        attempts++;
                    }
                    if (nextItem != null) {
                        reels[slot].add(nextItem);
                        lastItem = nextItem;
                    }
                }
                ItemStack startingItem = null;
                if (configManager.isStartFromLastPosition()) {
                    startingItem = originalContents[slot / 3][slot % 3];
                }
                if (startingItem != null && !startingItem.getType().isAir()) {
                    reels[slot].add(0, startingItem.clone());
                    reelIndices[slot] = 0;
                } else {
                    reelIndices[slot] = random.nextInt(Math.max(1, reels[slot].size()));
                }
                
                state.reels[slot] = new java.util.ArrayList<>(reels[slot]);
                state.reelIndices[slot] = reelIndices[slot];
                
                if (!reels[slot].isEmpty()) {
                    setItemAt(slot, reels[slot].get(reelIndices[slot]));
                }
            }
        }

        if (configManager.isSpinningEnabled()) {
            spawnHologram(configManager.getHologramSpinLines(player, betAmount), "spinning");
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
                    if (!reels[slot].isEmpty()) {
                        reelIndices[slot] = (reelIndices[slot] + 1) % reels[slot].size();
                        setItemAt(slot, reels[slot].get(reelIndices[slot]));
                        plugin.getSavedState(casinoBlock.getLocation()).reelIndices[slot] = reelIndices[slot];
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
