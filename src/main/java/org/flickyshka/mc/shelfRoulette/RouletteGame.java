package org.flickyshka.mc.shelfRoulette;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShelfInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouletteGame extends BukkitRunnable {

    private final ShelfRoulette plugin;
    private final ConfigManager configManager;
    private final Player player;
    private final double betAmount;
    private final ConfigManager.RouletteSetup setup;
    
    private final List<Block> shelves;
    private final List<ShelfInventory> inventories = new ArrayList<>();
    private final Map<Block, ItemStack[]> originalContents = new HashMap<>();
    private final Block middleBlock;
    
    private boolean restored = false;
    private boolean displayingResult = false;

    private int totalTicksPassed = 0;
    private int nextActionTick = 0;
    private final int duration;
    
    private final List<Entity> hologramLines = new ArrayList<>();
    private String dhHologramName = null;
    
    private final List<ItemStack> rouletteWheel = new ArrayList<>();
    private int wheelOffset = 0;
    
    private final int[] slotStopTicks;
    private final java.util.Random random = new java.util.Random();

    public RouletteGame(ShelfRoulette plugin, Player player, double betAmount, Block casinoBlock) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.player = player;
        this.betAmount = betAmount;

        String setupName = plugin.getShelvesManager().getSetupName(casinoBlock.getLocation());
        this.setup = configManager.getSetup(setupName);

        this.duration = configManager.getAnimationDuration();

        // Находим все соединенные полки и кэшируем их инвентари
        this.shelves = plugin.getShelvesManager().findConnectedShelves(casinoBlock);
        for (Block b : shelves) {
            Shelf shelfState = (Shelf) b.getState();
            inventories.add(shelfState.getInventory());
            originalContents.put(b, shelfState.getInventory().getContents().clone());
        }
        
        // Находим центральный блок полки
        this.middleBlock = shelves.get(shelves.size() / 2);

        int totalSlots = shelves.size() * 3;
        this.slotStopTicks = new int[totalSlots];
        int interval = duration / (totalSlots + 1);
        for (int i = 0; i < totalSlots; i++) {
            slotStopTicks[i] = duration - (totalSlots - 1 - i) * interval;
        }

        // Прячем idle голограмму
        plugin.getShelvesManager().setIdleHologramVisible(middleBlock.getLocation(), false);

        // Инициализируем колесо рулетки
        if (!setup.getGameMode().equals("SLOT")) {
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

            // Создаем единую ленту (размером как минимум в 3 раза больше слотов).
            // Мы просто дублируем базовый набор предметов (baseWheel), чтобы 
            // 1) не нарушить оригинальные шансы из конфига
            // 2) сохранить строгий порядок предметов (без хаотичного мельтешения рандома)
            // 3) обеспечить плавную цикличную прокрутку
            totalSlots = shelves.size() * 3;
            int targetSize = totalSlots * 3;
            
            while (rouletteWheel.size() < targetSize) {
                rouletteWheel.addAll(baseWheel);
            }
        }

        // Инициализируем полки начальными предметами
        totalSlots = shelves.size() * 3;
        for (int slot = 0; slot < totalSlots; slot++) {
            if (setup.getGameMode().equals("SLOT")) {
                ItemStack randomItem = setup.getRandomItem();
                if (randomItem != null) {
                    setItemAt(slot, randomItem);
                }
            } else {
                if (!rouletteWheel.isEmpty()) {
                    setItemAt(slot, rouletteWheel.get(slot % rouletteWheel.size()));
                }
            }
        }

        // Спавним голограмму над центральной полкой, если она включена
        if (configManager.isSpinningEnabled()) {
            spawnHologram(configManager.getHologramSpinLines(player, betAmount), "spinning");
        }
    }

    @Override
    public void run() {
        if (setup.getGameMode().equals("SLOT")) {
            int totalSlots = shelves.size() * 3;
            for (int slot = 0; slot < totalSlots; slot++) {
                if (totalTicksPassed == slotStopTicks[slot]) {
                    configManager.playSound(middleBlock.getLocation(), "stop", "BLOCK_NOTE_BLOCK_BELL", 1.0f, 1.2f);
                }
            }
        }

        if (totalTicksPassed >= duration) {
            this.cancel();
            determineResult();
            this.displayingResult = true;
            // Дадим игроку настроенное количество тиков посмотреть на результат перед удалением
            new BukkitRunnable() {
                @Override
                public void run() {
                    restoreOriginalContents();
                }
            }.runTaskLater(plugin, (long) configManager.getResultDisplayDuration());
            return;
        }

        if (totalTicksPassed >= nextActionTick) {
            if (setup.getGameMode().equals("SLOT")) {
                int totalSlots = shelves.size() * 3;
                for (int slot = 0; slot < totalSlots; slot++) {
                    if (totalTicksPassed >= slotStopTicks[slot]) {
                        continue; // Этот барабан (слот) уже остановился
                    }
                    
                    ItemStack currentItem = getItemAt(slot);
                    ItemStack randomItem = setup.getRandomItem();
                    
                    // Гарантируем смену кадра для красивой анимации, НО только если это не последний (победный) тик.
                    // Для последнего тика берем чистый рандом, чтобы не сломать математические шансы выпадения!
                    boolean isLastTick = (totalTicksPassed == slotStopTicks[slot] - 1);
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
            } else {
                // В режиме ROULETTE используем подготовленное колесо
                if (!rouletteWheel.isEmpty()) {
                    wheelOffset = (wheelOffset - 1 + rouletteWheel.size()) % rouletteWheel.size();
                    int totalSlots = shelves.size() * 3;
                    for (int slot = 0; slot < totalSlots; slot++) {
                        int wheelIndex = (wheelOffset + slot) % rouletteWheel.size();
                        setItemAt(slot, rouletteWheel.get(wheelIndex));
                    }
                }
            }
            
            // Воспроизводим звук прокрутки
            configManager.playSound(middleBlock.getLocation(), "spin", "BLOCK_NOTE_BLOCK_HAT", 1.0f, 1.0f);

            int delay = configManager.getAnimationSpeedTicks();
            if (configManager.isAnimationSlowdown()) {
                double progress = (double) totalTicksPassed / duration;
                if (progress > 0.5) {
                    double factor = (progress - 0.5) * 2.0;
                    int baseSlowdown = (int)(factor * factor * 15);
                    int randomJitter = -1 + random.nextInt(4); // Рандом от -1 до +2 тиков
                    delay += Math.max(0, baseSlowdown + randomJitter);
                }
            }
            nextActionTick = totalTicksPassed + delay;
        }

        totalTicksPassed++;
    }

    private void determineResult() {
        boolean won = false;
        double multiplier = 0.0;
        ItemStack winningItem = null;

        if (setup.getGameMode().equals("SLOT")) {
            // В режиме SLOT выигрыш засчитывается, если в ЛЮБОЙ из полок все 3 слота совпадают
            for (int shelfIndex = 0; shelfIndex < shelves.size(); shelfIndex++) {
                ItemStack item1 = inventories.get(shelfIndex).getItem(0);
                ItemStack item2 = inventories.get(shelfIndex).getItem(1);
                ItemStack item3 = inventories.get(shelfIndex).getItem(2);

                if (item1 != null && item2 != null && item3 != null &&
                    !item1.getType().isAir() && item1.isSimilar(item2) && item1.isSimilar(item3)) {
                    
                    double mult = setup.getMultiplier(item1);
                    if (mult > multiplier) {
                        multiplier = mult;
                        winningItem = item1;
                    }
                    won = true;
                }
            }
        } else {
            // В режиме ROULETTE выигрывает предмет строго в центральном слоте
            int totalSlots = shelves.size() * 3;
            int middleSlotIndex = (totalSlots - 1) / 2;
            winningItem = getItemAt(middleSlotIndex);
            
            if (winningItem != null && !winningItem.getType().isAir()) {
                double mult = setup.getMultiplier(winningItem);
                if (mult > 0.0) {
                    multiplier = mult;
                    won = true;
                }
            }
        }

        if (won && multiplier > 0.0 && winningItem != null) {
            double winnings = betAmount * multiplier;
            plugin.getEconomyManager().deposit(player, winnings);
            
            String itemName = winningItem.getType().name();
            configManager.sendMessage(player, configManager.getMessage("win")
                    .replace("{amount_commas}", configManager.formatMoneyCommas(winnings))
                    .replace("{amount}", configManager.formatMoney(winnings))
                    .replace("{item}", itemName)
                    .replace("{multiplier}", String.valueOf(multiplier)));
            
            // Звук победы
            configManager.playSound(middleBlock.getLocation(), "win", "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
            
            // Обновляем голограмму победными линиями
            if (configManager.isWinEnabled()) {
                removeHologram();
                spawnHologram(configManager.getHologramWinLines(player, betAmount, winnings), "win");
            } else {
                removeHologram();
            }
        } else {
            configManager.sendMessage(player, configManager.getMessage("lose"));
            // Звук проигрыша
            configManager.playSound(middleBlock.getLocation(), "lose", "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
            
            // Обновляем голограмму проигрышными линиями
            if (configManager.isLoseEnabled()) {
                removeHologram();
                spawnHologram(configManager.getHologramLoseLines(player, betAmount), "lose");
            } else {
                removeHologram();
            }
        }
    }

    public synchronized void restoreOriginalContents() {
        if (restored) {
            return;
        }
        restored = true;
        
        // Удаляем голограмму
        removeHologram();

        // Показываем idle голограмму
        plugin.getShelvesManager().setIdleHologramVisible(middleBlock.getLocation(), true);

        // Восстанавливаем оригинальные предметы во всех полках
        for (Block b : shelves) {
            if (b.getState() instanceof Shelf) {
                Shelf shelfState = (Shelf) b.getState();
                ItemStack[] original = originalContents.get(b);
                if (original != null) {
                    shelfState.getInventory().setContents(original);
                }
            }
        }
        
        // Убираем игру из списка активных для всех соединенных полок
        for (Block b : shelves) {
            plugin.getActiveGames().remove(b.getLocation());
        }
    }

    private void spawnHologram(List<String> lines, String category) {
        double gx = configManager.getGameOffsetX();
        double gy = configManager.getGameOffsetY();
        double gz = configManager.getGameOffsetZ();
        Location baseLoc = middleBlock.getLocation().add(0.5 + gx, gy, 0.5 + gz);
        baseLoc.setYaw(0.0f);
        baseLoc.setPitch(0.0f);
        double spacing = 0.25;

        // Рассчитываем yOffset, чтобы центрировать голограмму по вертикали
        double yOffset = 0.1 + (lines.size() - 1) * spacing;
        String type = configManager.getHologramType(category).toUpperCase();

            for (String line : lines) {
                Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                lineLoc.setYaw(0.0f);
                lineLoc.setPitch(0.0f);
                
                org.bukkit.entity.Entity stand;
                if (type.equals("TEXT_DISPLAY")) {
                    stand = baseLoc.getWorld().spawn(lineLoc, TextDisplay.class, s -> {
                        s.setPersistent(false);
                        s.setText(line);
                        s.setBillboard(Display.Billboard.CENTER);
                        s.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        s.setShadowed(false);
                    });
                } else {
                    stand = baseLoc.getWorld().spawn(lineLoc, org.bukkit.entity.ArmorStand.class, s -> {
                        s.setPersistent(false);
                        s.setCustomName(line);
                        s.setCustomNameVisible(true);
                        s.setInvisible(true);
                        s.setMarker(true);
                        s.setGravity(false);
                        s.setBasePlate(false);
                    });
                }
                hologramLines.add(stand);
                yOffset -= spacing;
            }
    }

    private void removeHologram() {
        for (Entity stand : hologramLines) {
            stand.remove();
        }
        hologramLines.clear();
    }

    private ItemStack getItemAt(int slot) {
        int shelfIndex = slot / 3;
        int localSlot = slot % 3;
        return inventories.get(shelfIndex).getItem(localSlot);
    }

    private void setItemAt(int slot, ItemStack item) {
        int shelfIndex = slot / 3;
        int localSlot = slot % 3;
        inventories.get(shelfIndex).setItem(localSlot, item);
    }

    public List<Block> getShelves() {
        return shelves;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isDisplayingResult() {
        return displayingResult;
    }
}
