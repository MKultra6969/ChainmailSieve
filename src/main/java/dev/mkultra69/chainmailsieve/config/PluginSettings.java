package dev.mkultra69.chainmailsieve.config;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record PluginSettings(
        boolean enabled,
        boolean debugEnabled,
        int durabilityCost,
        Set<Material> supportedMaterials,
        String usePermission,
        String bypassPermission,
        String adminPermission,
        ParticleSettings particleSettings,
        SoundSettings soundSettings,
        String reloadedMessage,
        String noPermissionMessage,
        String usageMessage) {

    public static PluginSettings fromConfig(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        return new PluginSettings(
                config.getBoolean("enabled", true),
                config.getBoolean("debug.enabled", true),
                Math.max(0, config.getInt("durability-cost", 1)),
                parseMaterials(plugin, config.getStringList("supported-materials")),
                config.getString("permissions.use", "chainmailsieve.use"),
                config.getString("permissions.bypass", "chainmailsieve.bypass"),
                config.getString("permissions.admin", "chainmailsieve.admin"),
                new ParticleSettings(
                        config.getBoolean("effects.particles.enabled", true),
                        Math.max(0, config.getInt("effects.particles.count", 18)),
                        Math.max(0.0D, config.getDouble("effects.particles.offset", 0.18D)),
                        Math.max(0.0D, config.getDouble("effects.particles.extra", 0.0D))),
                new SoundSettings(
                        config.getBoolean("effects.sound.enabled", true),
                        (float) config.getDouble("effects.sound.volume", 1.0D),
                        (float) config.getDouble("effects.sound.pitch", 1.0D)),
                config.getString("messages.reloaded", "&aChainmailSieve config reloaded."),
                config.getString("messages.no-permission", "&cYou do not have permission to use this command."),
                config.getString("messages.usage", "&eUsage: /chainmailsieve reload"));
    }

    private static Set<Material> parseMaterials(JavaPlugin plugin, List<String> values) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);

        for (String value : values) {
            Material material = Material.matchMaterial(value);
            if (material == null) {
                plugin.getLogger().warning("Ignoring unsupported material in config: " + value);
                continue;
            }
            materials.add(material);
        }

        if (isLegacyDefaultMaterialList(values) && !materials.contains(Material.RED_SAND)) {
            materials.add(Material.RED_SAND);
            plugin.getLogger().info("Auto-enabled RED_SAND support for legacy default config.");
        }

        if (materials.isEmpty()) {
            plugin.getLogger().warning("No valid materials configured, falling back to SAND, RED_SAND and GRAVEL.");
            materials.add(Material.SAND);
            materials.add(Material.RED_SAND);
            materials.add(Material.GRAVEL);
        }

        return Collections.unmodifiableSet(materials);
    }

    private static boolean isLegacyDefaultMaterialList(List<String> values) {
        if (values.size() != 2) {
            return false;
        }

        Set<String> normalized = values.stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        return normalized.contains("SAND") && normalized.contains("GRAVEL");
    }

    public record ParticleSettings(
            boolean enabled,
            int count,
            double offset,
            double extra) {
    }

    public record SoundSettings(
            boolean enabled,
            float volume,
            float pitch) {
    }
}
