package dev.mkultra69.chainmailsieve.command;

import dev.mkultra69.chainmailsieve.ChainmailSievePlugin;
import dev.mkultra69.chainmailsieve.config.PluginSettings;
import dev.mkultra69.chainmailsieve.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

public final class ChainmailSieveCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload");

    private final ChainmailSievePlugin plugin;

    public ChainmailSieveCommand(ChainmailSievePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginSettings settings = plugin.getPluginSettings();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(settings.adminPermission())) {
                sender.sendMessage(ColorUtil.colorize(settings.noPermissionMessage()));
                return true;
            }

            plugin.reloadPluginSettings();
            sender.sendMessage(ColorUtil.colorize(plugin.getPluginSettings().reloadedMessage()));
            return true;
        }

        sender.sendMessage(ColorUtil.colorize(settings.usageMessage()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(plugin.getPluginSettings().adminPermission())) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
        return completions;
    }
}

