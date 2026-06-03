package org.flickyshka.mc.shelfRoulette.game;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class GameState {
    public int spinCount = 0;
    
    // For Roulette
    public List<ItemStack> rouletteWheel = null;
    public int wheelOffset = 0;
    
    // For Slot
    public List<ItemStack>[] reels = null;
    public int[] reelIndices = null;
}
