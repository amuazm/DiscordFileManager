package me.amuazm.discordFileManager.commands;

import me.amuazm.discordFileManager.DiscordFileManager;
import me.amuazm.discordFileManager.utils.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class DfmReloadCommand implements CommandExecutor, TabCompleter {
    private final DiscordFileManager plugin;
    private final ConfigManager configManager;

    public DfmReloadCommand(DiscordFileManager plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("discordfilemanager.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /dfm reload");
            return true;
        }

        try {
            // Reload the config
            plugin.getConfigManager().reloadConfig();

            // Validate the reloaded config
            boolean configValid = configManager.validateConfig();

            if (configValid) {
                // Reload file managers with new config
                plugin.reloadFileManagers();

                if (sender instanceof Player) {
                    sender.sendMessage(ChatColor.GREEN + "DiscordFileManager config and file managers reloaded successfully!");
                }

                plugin.getLogger().info("Config and file managers reloaded by " + sender.getName());
            } else {
                if (sender instanceof Player) {
                    sender.sendMessage(ChatColor.RED + "Config reloaded but contains invalid values! Check console for details.");
                }

                plugin.getLogger().warning("Config reloaded by " + sender.getName() + " but contains invalid values!");
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload config: " + e.getMessage());
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload");
        }

        return null;
    }
}