package dev.mkultra69.chainmailsieve;

import dev.mkultra69.chainmailsieve.command.ChainmailSieveCommand;
import dev.mkultra69.chainmailsieve.config.PluginSettings;
import dev.mkultra69.chainmailsieve.listener.FallingBlockHeadListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChainmailSievePlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 30051;

    private PluginSettings settings;
    private FallingBlockHeadListener fallingBlockHeadListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginSettings();
        registerCommands();
        initializeMetrics();

        fallingBlockHeadListener = new FallingBlockHeadListener(this);
        fallingBlockHeadListener.start();
        getServer().getPluginManager().registerEvents(fallingBlockHeadListener, this);
    }

    @Override
    public void onDisable() {
        if (fallingBlockHeadListener != null) {
            fallingBlockHeadListener.stop();
            fallingBlockHeadListener = null;
        }
    }

    public PluginSettings getPluginSettings() {
        return settings;
    }

    public void reloadPluginSettings() {
        reloadConfig();
        settings = PluginSettings.fromConfig(this);
        debug("Debug logging is enabled.");
    }

    public void debug(String message) {
        if (settings != null && settings.debugEnabled()) {
            getLogger().info("[debug] " + message);
        }
    }

    private void initializeMetrics() {
        PluginSettings.BStatsSettings bStatsSettings = settings.bStatsSettings();
        if (!bStatsSettings.enabled()) {
            debug("bStats metrics are disabled in config.");
            return;
        }

        new Metrics(this, BSTATS_PLUGIN_ID);
        debug("bStats metrics initialized with plugin id " + BSTATS_PLUGIN_ID + ".");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("chainmailsieve");
        if (command == null) {
            throw new IllegalStateException("Command 'chainmailsieve' is not defined in plugin.yml");
        }

        ChainmailSieveCommand executor = new ChainmailSieveCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
