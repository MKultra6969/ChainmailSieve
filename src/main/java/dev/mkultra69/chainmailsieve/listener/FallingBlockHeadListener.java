package dev.mkultra69.chainmailsieve.listener;

import dev.mkultra69.chainmailsieve.ChainmailSievePlugin;
import dev.mkultra69.chainmailsieve.config.PluginSettings;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class FallingBlockHeadListener implements Listener {

    private static final double CONTACT_PADDING = 0.03D;
    private static final double SEARCH_EXPANSION = 0.75D;
    private static final double HELMET_ZONE_HEIGHT = 0.45D;
    private static final double IMPACT_Y_OFFSET = 0.10D;
    private static final Material REQUIRED_HELMET = Material.CHAINMAIL_HELMET;

    private final ChainmailSievePlugin plugin;
    private final Set<UUID> recentlyHandled = ConcurrentHashMap.newKeySet();
    private BukkitTask scanTask;

    public FallingBlockHeadListener(ChainmailSievePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (scanTask != null) {
            return;
        }

        scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanFallingBlocks, 1L, 1L);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        recentlyHandled.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        PluginSettings settings = plugin.getPluginSettings();
        if (!settings.enabled()) {
            return;
        }

        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }

        UUID entityId = fallingBlock.getUniqueId();
        if (recentlyHandled.remove(entityId)) {
            plugin.debug("Ignoring fallback EntityChangeBlockEvent for already handled falling block " + entityId + ".");
            return;
        }

        Material material = event.getTo();
        if (!settings.supportedMaterials().contains(material)) {
            return;
        }

        BoundingBox impactBox = createImpactBox(event.getBlock(), fallingBlock);
        Location blockCenter = event.getBlock().getLocation().toCenterLocation();
        plugin.debug("Fallback EntityChangeBlockEvent material=" + material
                + " blockType=" + event.getBlock().getType()
                + " target=" + formatLocation(event.getBlock().getLocation())
                + " fallingLoc=" + formatLocation(fallingBlock.getLocation())
                + " impactBox=" + formatBoundingBox(impactBox));

        Player player = findMatchingPlayer(blockCenter, impactBox, event.getBlock().getWorld(), settings);
        if (player == null) {
            plugin.debug("Fallback event found no matching player for impact at " + formatLocation(event.getBlock().getLocation()));
            return;
        }

        handleImpact(fallingBlock, player, material, "fallback-event", event);
    }

    private void scanFallingBlocks() {
        PluginSettings settings = plugin.getPluginSettings();
        if (!settings.enabled()) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            for (FallingBlock fallingBlock : world.getEntitiesByClass(FallingBlock.class)) {
                Material material = fallingBlock.getBlockData().getMaterial();
                if (!settings.supportedMaterials().contains(material)) {
                    continue;
                }

                BoundingBox fallingBox = fallingBlock.getBoundingBox().expand(CONTACT_PADDING);
                Player player = findMatchingPlayer(fallingBlock.getLocation(), fallingBox, world, settings);
                if (player == null) {
                    continue;
                }

                plugin.debug("Tick collision material=" + material
                        + " fallingLoc=" + formatLocation(fallingBlock.getLocation())
                        + " fallingBox=" + formatBoundingBox(fallingBox)
                        + " player=" + player.getName()
                        + " helmetZone=" + formatBoundingBox(getHelmetZone(player)));

                handleImpact(fallingBlock, player, material, "tick-scan", null);
            }
        }
    }

    private Player findMatchingPlayer(Location center, BoundingBox impactBox, World world, PluginSettings settings) {
        List<Player> nearbyPlayers = world.getNearbyEntities(impactBox.clone().expand(SEARCH_EXPANSION), this::isPlayer)
                .stream()
                .map(Player.class::cast)
                .toList();

        return nearbyPlayers.stream()
                .filter(player -> player.isValid() && !player.isDead())
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .filter(player -> player.hasPermission(settings.usePermission()))
                .filter(player -> !player.hasPermission(settings.bypassPermission()))
                .filter(player -> isWearingRequiredHelmet(player.getInventory().getHelmet()))
                .filter(player -> impactBox.overlaps(getHelmetZone(player)))
                .min(Comparator.comparingDouble(player -> player.getEyeLocation().distanceSquared(center)))
                .orElse(null);
    }

    private void handleImpact(FallingBlock fallingBlock, Player player, Material material, String source, EntityChangeBlockEvent event) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (!isWearingRequiredHelmet(helmet)) {
            plugin.debug("Impact source=" + source + " rejected because helmet is " + formatHelmet(helmet));
            return;
        }

        if (event != null) {
            event.setCancelled(true);
        }

        rememberHandled(fallingBlock.getUniqueId());
        fallingBlock.setDropItem(false);
        fallingBlock.remove();

        Location impactLocation = createImpactLocation(player, fallingBlock);
        plugin.debug("Mechanic triggered source=" + source
                + " player=" + player.getName()
                + " material=" + material
                + " impact=" + formatLocation(impactLocation));

        dropBrokenBlock(impactLocation, material);
        playImpactEffects(impactLocation, material, plugin.getPluginSettings());
        damageHelmet(player, plugin.getPluginSettings().durabilityCost());
    }

    private void rememberHandled(UUID entityId) {
        recentlyHandled.add(entityId);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> recentlyHandled.remove(entityId), 5L);
    }

    private Location createImpactLocation(Player player, FallingBlock fallingBlock) {
        BoundingBox helmetZone = getHelmetZone(player);
        Location fallingLocation = fallingBlock.getLocation();
        double x = clamp(fallingLocation.getX(), helmetZone.getMinX(), helmetZone.getMaxX());
        double z = clamp(fallingLocation.getZ(), helmetZone.getMinZ(), helmetZone.getMaxZ());
        double y = helmetZone.getMaxY() - IMPACT_Y_OFFSET;
        return new Location(player.getWorld(), x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    private BoundingBox createImpactBox(Block block, FallingBlock fallingBlock) {
        BoundingBox landingBox = new BoundingBox(
                block.getX(),
                block.getY(),
                block.getZ(),
                block.getX() + 1.0D,
                block.getY() + 1.0D,
                block.getZ() + 1.0D).expand(CONTACT_PADDING);

        return landingBox.union(fallingBlock.getBoundingBox().expand(CONTACT_PADDING));
    }

    private boolean isPlayer(Entity entity) {
        return entity instanceof Player;
    }

    private BoundingBox getHelmetZone(Player player) {
        BoundingBox bodyBox = player.getBoundingBox();
        double helmetZoneMinY = Math.max(bodyBox.getMinY(), bodyBox.getMaxY() - HELMET_ZONE_HEIGHT);

        return new BoundingBox(
                bodyBox.getMinX(),
                helmetZoneMinY,
                bodyBox.getMinZ(),
                bodyBox.getMaxX(),
                bodyBox.getMaxY(),
                bodyBox.getMaxZ()).expand(CONTACT_PADDING);
    }

    private boolean isWearingRequiredHelmet(ItemStack helmet) {
        return helmet != null && helmet.getType() == REQUIRED_HELMET;
    }

    private void dropBrokenBlock(Location location, Material material) {
        if (location.getWorld() == null) {
            return;
        }

        location.getWorld().dropItem(location, new ItemStack(material), item -> item.setVelocity(new Vector(0.0D, 0.05D, 0.0D)));
    }

    private void playImpactEffects(Location location, Material material, PluginSettings settings) {
        if (location.getWorld() == null) {
            return;
        }

        PluginSettings.ParticleSettings particleSettings = settings.particleSettings();
        if (particleSettings.enabled() && particleSettings.count() > 0) {
            BlockData blockData = material.createBlockData();
            location.getWorld().spawnParticle(
                    Particle.FALLING_DUST,
                    location,
                    particleSettings.count(),
                    particleSettings.offset(),
                    particleSettings.offset(),
                    particleSettings.offset(),
                    particleSettings.extra(),
                    blockData);
        }

        PluginSettings.SoundSettings soundSettings = settings.soundSettings();
        if (soundSettings.enabled()) {
            location.getWorld().playSound(location, resolveBreakSound(material), soundSettings.volume(), soundSettings.pitch());
        }
    }

    private Sound resolveBreakSound(Material material) {
        return switch (material) {
            case GRAVEL -> Sound.BLOCK_GRAVEL_BREAK;
            default -> Sound.BLOCK_SAND_BREAK;
        };
    }

    private void damageHelmet(Player player, int configuredCost) {
        if (configuredCost <= 0) {
            return;
        }

        player.damageItemStack(EquipmentSlot.HEAD, configuredCost);
        plugin.debug("Applied helmet damage to " + player.getName() + " amount=" + configuredCost);
    }

    private String formatHelmet(ItemStack helmet) {
        if (helmet == null) {
            return "none";
        }

        return helmet.getType() + "x" + helmet.getAmount();
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + "@"
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ()
                + " ("
                + round(location.getX()) + ","
                + round(location.getY()) + ","
                + round(location.getZ()) + ")";
    }

    private String formatBoundingBox(BoundingBox box) {
        return "["
                + round(box.getMinX()) + ","
                + round(box.getMinY()) + ","
                + round(box.getMinZ())
                + " -> "
                + round(box.getMaxX()) + ","
                + round(box.getMaxY()) + ","
                + round(box.getMaxZ()) + "]";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
