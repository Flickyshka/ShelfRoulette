package org.flickyshka.mc.shelfRoulette;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Shelf;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.Color;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShelvesManager {

    private final ShelfRoulette plugin;
    private File file;
    private FileConfiguration config;
    private final Map<Location, String> registeredShelves = new HashMap<>();

    public ShelvesManager(ShelfRoulette plugin) {
        this.plugin = plugin;
        setup();
        load();
    }

    private void setup() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        file = new File(plugin.getDataFolder(), "shelves.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать shelves.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void load() {
        registeredShelves.clear();
        if (config.isConfigurationSection("shelves")) {
            ConfigurationSection sec = config.getConfigurationSection("shelves");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    Location loc = deserializeLocation(key);
                    if (loc != null) {
                        String val = sec.getString(key, "default");
                        if (val.contains(";") && (val.endsWith(";TEXT_DISPLAY") || val.endsWith(";HOLOGRAM"))) {
                            val = val.substring(0, val.lastIndexOf(';'));
                        } else if (val.contains(":") && (val.endsWith(":TEXT_DISPLAY") || val.endsWith(":HOLOGRAM"))) {
                            val = val.substring(0, val.lastIndexOf(':'));
                        }
                        registeredShelves.put(loc, val);
                    }
                }
            }
        } else if (config.isList("shelves")) {
            List<String> list = config.getStringList("shelves");
            for (String serialized : list) {
                Location loc = deserializeLocation(serialized);
                if (loc != null) {
                    registeredShelves.put(loc, "default");
                }
            }
        }
        updatePermanentArrows();
    }

    public void save() {
        config.set("shelves", null);
        ConfigurationSection sec = config.createSection("shelves");
        for (Map.Entry<Location, String> entry : registeredShelves.entrySet()) {
            sec.set(serializeLocation(entry.getKey()), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить shelves.yml!");
        }
    }

    public boolean isRegistered(Location loc) {
        return getSetupName(loc) != null;
    }

    public String getSetupName(Location loc) {
        if (loc == null) return null;
        for (Map.Entry<Location, String> entry : registeredShelves.entrySet()) {
            Location rLoc = entry.getKey();
            if (rLoc.getBlockX() == loc.getBlockX() &&
                rLoc.getBlockY() == loc.getBlockY() &&
                rLoc.getBlockZ() == loc.getBlockZ()) {
                
                String rWorld = rLoc.getWorld() != null ? rLoc.getWorld().getName() : null;
                String locWorld = loc.getWorld() != null ? loc.getWorld().getName() : null;
                
                if (rWorld == null || locWorld == null || rWorld.equalsIgnoreCase(locWorld)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public boolean register(Location loc, String setupName) {
        if (loc == null) return false;
        Block startBlock = loc.getBlock();
        List<Block> connected = findPhysicallyConnectedShelves(startBlock);
        if (connected.isEmpty()) connected.add(startBlock);

        boolean registeredAny = false;
        for (Block b : connected) {
            Location l = b.getLocation();
            if (!isRegistered(l)) {
                registeredShelves.put(l, setupName.toLowerCase());
                registeredAny = true;
            }
        }
        
        if (!registeredAny) return false;
        
        save();
        updatePermanentArrows();
        return true;
    }

    public boolean unregister(Location loc) {
        if (loc == null) return false;
        
        Block startBlock = loc.getBlock();
        List<Block> connected = findConnectedShelves(startBlock);
        if (connected.isEmpty()) connected.add(startBlock);
        
        boolean unregisteredAny = false;
        
        for (Block b : connected) {
            Location bLoc = b.getLocation();
            Location found = null;
            for (Location rLoc : registeredShelves.keySet()) {
                if (rLoc.getBlockX() == bLoc.getBlockX() &&
                    rLoc.getBlockY() == bLoc.getBlockY() &&
                    rLoc.getBlockZ() == bLoc.getBlockZ()) {
                    
                    String rWorld = rLoc.getWorld() != null ? rLoc.getWorld().getName() : null;
                    String locWorld = bLoc.getWorld() != null ? bLoc.getWorld().getName() : null;
                    
                    if (rWorld == null || locWorld == null || rWorld.equalsIgnoreCase(locWorld)) {
                        found = rLoc;
                        break;
                    }
                }
            }
            if (found != null) {
                registeredShelves.remove(found);
                unregisteredAny = true;
            }
        }
        
        if (unregisteredAny) {
            save();
            updatePermanentArrows();
            return true;
        }
        return false;
    }

    public Set<Location> getRegisteredShelves() {
        return registeredShelves.keySet();
    }

    private String serializeLocation(Location loc) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location deserializeLocation(String str) {
        try {
            String[] parts = str.split(",");
            if (parts.length == 4) {
                return new Location(
                        Bukkit.getWorld(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при десериализации локации полки: " + str);
        }
        return null;
    }

    public List<Block> findConnectedShelves(Block startBlock) {
        List<Block> connected = new ArrayList<>();
        if (startBlock == null || !(startBlock.getState() instanceof Shelf)) {
            return connected;
        }

        String startSetup = getSetupName(startBlock.getLocation());
        if (startSetup == null) {
            return connected;
        }

        List<Block> xList = new ArrayList<>();
        xList.add(startBlock);

        Block next = startBlock;
        while (true) {
            next = next.getRelative(1, 0, 0);
            if (next.getState() instanceof Shelf) {
                String nextSetup = getSetupName(next.getLocation());
                if (startSetup.equalsIgnoreCase(nextSetup)) {
                    xList.add(next);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        next = startBlock;
        while (true) {
            next = next.getRelative(-1, 0, 0);
            if (next.getState() instanceof Shelf) {
                String nextSetup = getSetupName(next.getLocation());
                if (startSetup.equalsIgnoreCase(nextSetup)) {
                    xList.add(0, next);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        List<Block> zList = new ArrayList<>();
        zList.add(startBlock);

        next = startBlock;
        while (true) {
            next = next.getRelative(0, 0, 1);
            if (next.getState() instanceof Shelf) {
                String nextSetup = getSetupName(next.getLocation());
                if (startSetup.equalsIgnoreCase(nextSetup)) {
                    zList.add(next);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        next = startBlock;
        while (true) {
            next = next.getRelative(0, 0, -1);
            if (next.getState() instanceof Shelf) {
                String nextSetup = getSetupName(next.getLocation());
                if (startSetup.equalsIgnoreCase(nextSetup)) {
                    zList.add(0, next);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        List<Block> result = (xList.size() > zList.size()) ? xList : zList;
        
        org.bukkit.block.data.BlockData data = startBlock.getBlockData();
        if (data instanceof org.bukkit.block.data.Directional) {
            org.bukkit.block.BlockFace face = ((org.bukkit.block.data.Directional) data).getFacing();
            if (result == xList && face == org.bukkit.block.BlockFace.NORTH) {
                java.util.Collections.reverse(result);
            } else if (result == zList && face == org.bukkit.block.BlockFace.EAST) {
                java.util.Collections.reverse(result);
            }
        }
        
        return result;
    }

    private List<Block> findPhysicallyConnectedShelves(Block startBlock) {
        List<Block> connected = new ArrayList<>();
        if (startBlock == null || !(startBlock.getState() instanceof Shelf)) {
            return connected;
        }

        List<Block> xList = new ArrayList<>();
        xList.add(startBlock);

        Block next = startBlock;
        while (true) {
            next = next.getRelative(1, 0, 0);
            if (next.getState() instanceof Shelf) {
                xList.add(next);
            } else {
                break;
            }
        }

        next = startBlock;
        while (true) {
            next = next.getRelative(-1, 0, 0);
            if (next.getState() instanceof Shelf) {
                xList.add(0, next);
            } else {
                break;
            }
        }

        List<Block> zList = new ArrayList<>();
        zList.add(startBlock);

        next = startBlock;
        while (true) {
            next = next.getRelative(0, 0, 1);
            if (next.getState() instanceof Shelf) {
                zList.add(next);
            } else {
                break;
            }
        }

        next = startBlock;
        while (true) {
            next = next.getRelative(0, 0, -1);
            if (next.getState() instanceof Shelf) {
                zList.add(0, next);
            } else {
                break;
            }
        }

        List<Block> result = (xList.size() > zList.size()) ? xList : zList;
        
        org.bukkit.block.data.BlockData data = startBlock.getBlockData();
        if (data instanceof org.bukkit.block.data.Directional) {
            org.bukkit.block.BlockFace face = ((org.bukkit.block.data.Directional) data).getFacing();
            if (result == xList && face == org.bukkit.block.BlockFace.NORTH) {
                java.util.Collections.reverse(result);
            } else if (result == zList && face == org.bukkit.block.BlockFace.EAST) {
                java.util.Collections.reverse(result);
            }
        }
        
        return result;
    }

    private final List<org.bukkit.entity.Entity> spawnedArrows = new ArrayList<>();
    private final Map<Location, List<org.bukkit.entity.Entity>> idleHolograms = new HashMap<>();

    public void setIdleHologramVisible(Location loc, boolean visible) {
        List<org.bukkit.entity.Entity> stands = idleHolograms.get(loc);
        if (stands != null) {
            for (org.bukkit.entity.Entity stand : stands) {
                if (stand instanceof TextDisplay) {
                    ((TextDisplay) stand).setViewRange(visible ? 1.0f : 0.0f);
                } else {
                    stand.setCustomNameVisible(visible);
                }
            }
        }
    }

    public void updatePermanentArrows() {
        clearPermanentArrows();

        boolean arrowsEnabled = plugin.getConfigManager().isArrowsEnabled();
        boolean idleREnabled = plugin.getConfigManager().isIdleEnabled("ROULETTE");
        boolean idleSEnabled = plugin.getConfigManager().isIdleEnabled("SLOT");
        if (!arrowsEnabled && !idleREnabled && !idleSEnabled) {
            return;
        }

        Set<Location> processed = new HashSet<>();
        List<List<Block>> groups = new ArrayList<>();

        for (Location loc : registeredShelves.keySet()) {
            if (processed.contains(loc)) continue;

            Block block = loc.getBlock();
            if (block.getState() instanceof Shelf) {
                List<Block> connected = findConnectedShelves(block);
                groups.add(connected);
                for (Block b : connected) {
                    processed.add(b.getLocation());
                }
            }
        }

        ConfigManager configManager = plugin.getConfigManager();
        String topArrow = configManager.getTopArrow();
        String bottomArrow = configManager.getBottomArrow();

        double tx = configManager.getTopOffsetX();
        double ty = configManager.getTopOffsetY();
        double tz = configManager.getTopOffsetZ();

        double bx = configManager.getBottomOffsetX();
        double by = configManager.getBottomOffsetY();
        double bz = configManager.getBottomOffsetZ();

        for (List<Block> group : groups) {
            if (group.isEmpty()) continue;

            Block middleBlock = group.get(group.size() / 2);
            Location blockLoc = middleBlock.getLocation();

            String setupName = getSetupName(blockLoc);
            String mode = setupName != null ? configManager.getSetup(setupName).getGameMode() : "ROULETTE";
            boolean isSlotSetup = "SLOT".equalsIgnoreCase(mode);

            float yaw = 0.0f;
            if (middleBlock.getBlockData() instanceof org.bukkit.block.data.Directional) {
                org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) middleBlock.getBlockData();
                switch (directional.getFacing()) {
                    case NORTH:
                        yaw = 180.0f;
                        break;
                    case SOUTH:
                        yaw = 0.0f;
                        break;
                    case WEST:
                        yaw = 90.0f;
                        break;
                    case EAST:
                        yaw = 270.0f;
                        break;
                    default:
                        yaw = 0.0f;
                        break;
                }
            }

            double actualTx = tx;
            double actualTz = tz;
            double actualBx = bx;
            double actualBz = bz;

            if (middleBlock.getBlockData() instanceof org.bukkit.block.data.Directional) {
                org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) middleBlock.getBlockData();
                switch (directional.getFacing()) {
                    case NORTH:
                        actualTx = -tx; actualTz = -tz;
                        actualBx = -bx; actualBz = -bz;
                        break;
                    case SOUTH:
                        actualTx = tx; actualTz = tz;
                        actualBx = bx; actualBz = bz;
                        break;
                    case WEST:
                        actualTx = -tz; actualTz = tx;
                        actualBx = -bz; actualBz = bx;
                        break;
                    case EAST:
                        actualTx = tz; actualTz = -tx;
                        actualBx = bz; actualBz = -bx;
                        break;
                    default:
                        break;
                }
            }

            String arrowType = configManager.getHologramType("arrows").toUpperCase();

            if (!isSlotSetup && configManager.isArrowsEnabled() && topArrow != null && !topArrow.isEmpty()) {
                Location topLoc = blockLoc.clone().add(0.5 + actualTx, ty, 0.5 + actualTz);
                topLoc.setYaw(yaw);
                topLoc.setPitch(0.0f);
                org.bukkit.entity.Entity topStand = null;
                if (arrowType.equals("TEXT_DISPLAY")) {
                    topStand = blockLoc.getWorld().spawn(topLoc, TextDisplay.class, s -> {
                        s.setPersistent(false);
                        s.setText(topArrow);
                        s.setBillboard(Display.Billboard.FIXED);
                        s.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        s.setShadowed(false);
                    });
                    spawnedArrows.add(topStand);
                } else {
                    topStand = blockLoc.getWorld().spawn(topLoc, org.bukkit.entity.ArmorStand.class, s -> {
                        s.setPersistent(false);
                        s.setCustomName(topArrow);
                        s.setCustomNameVisible(true);
                        s.setInvisible(true);
                        s.setMarker(true);
                        s.setGravity(false);
                        s.setBasePlate(false);
                    });
                    spawnedArrows.add(topStand);
                }
            }

            if (!isSlotSetup && configManager.isArrowsEnabled() && bottomArrow != null && !bottomArrow.isEmpty()) {
                Location bottomLoc = blockLoc.clone().add(0.5 + actualBx, by, 0.5 + actualBz);
                bottomLoc.setYaw(yaw);
                bottomLoc.setPitch(0.0f);
                org.bukkit.entity.Entity bottomStand = null;
                if (arrowType.equals("TEXT_DISPLAY")) {
                    bottomStand = blockLoc.getWorld().spawn(bottomLoc, TextDisplay.class, s -> {
                        s.setPersistent(false);
                        s.setText(bottomArrow);
                        s.setBillboard(Display.Billboard.FIXED);
                        s.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        s.setShadowed(false);
                    });
                    spawnedArrows.add(bottomStand);
                } else {
                    bottomStand = blockLoc.getWorld().spawn(bottomLoc, org.bukkit.entity.ArmorStand.class, s -> {
                        s.setPersistent(false);
                        s.setCustomName(bottomArrow);
                        s.setCustomNameVisible(true);
                        s.setInvisible(true);
                        s.setMarker(true);
                        s.setGravity(false);
                        s.setBasePlate(false);
                    });
                    spawnedArrows.add(bottomStand);
                }
            }

            if (configManager.isIdleEnabled(mode)) {
                List<String> idleLines = configManager.getHologramIdleLines(mode);
                if (!idleLines.isEmpty()) {
                    double gx = configManager.getGameOffsetX();
                    double gy = configManager.getGameOffsetY();
                    double gz = configManager.getGameOffsetZ();
                    Location baseLoc = blockLoc.clone().add(0.5 + gx, gy, 0.5 + gz);
                    baseLoc.setYaw(0.0f);
                    baseLoc.setPitch(0.0f);
                    double spacing = 0.25;
                    double yOffset = 0.1 + (idleLines.size() - 1) * spacing;

                    List<org.bukkit.entity.Entity> idleStands = new ArrayList<>();
                    String idleType = configManager.getHologramType("idle").toUpperCase();
                    
                    if (idleType.equals("TEXT_DISPLAY")) {
                        for (String line : idleLines) {
                            Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                            lineLoc.setYaw(0.0f);
                            lineLoc.setPitch(0.0f);
                            
                            org.bukkit.entity.Entity stand = baseLoc.getWorld().spawn(lineLoc, TextDisplay.class, s -> {
                                s.setPersistent(false);
                                s.setText(line);
                                s.setBillboard(Display.Billboard.CENTER);
                                s.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                                s.setShadowed(false);
                            });
                            
                            idleStands.add(stand);
                            spawnedArrows.add(stand);
                            yOffset -= spacing;
                        }
                    } else {
                        for (String line : idleLines) {
                            Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
                            lineLoc.setYaw(0.0f);
                            lineLoc.setPitch(0.0f);
                            
                            org.bukkit.entity.Entity stand = baseLoc.getWorld().spawn(lineLoc, org.bukkit.entity.ArmorStand.class, s -> {
                                s.setPersistent(false);
                                s.setCustomName(line);
                                s.setCustomNameVisible(true);
                                s.setInvisible(true);
                                s.setMarker(true);
                                s.setGravity(false);
                                s.setBasePlate(false);
                            });
                            
                            idleStands.add(stand);
                            spawnedArrows.add(stand);
                            yOffset -= spacing;
                        }
                    }
                    idleHolograms.put(blockLoc, idleStands);
                }
            }
        }
    }

    public void clearPermanentArrows() {
        for (org.bukkit.entity.Entity stand : spawnedArrows) {
            if (stand.isValid()) {
                stand.remove();
            }
        }
        spawnedArrows.clear();
        idleHolograms.clear();
    }
}
