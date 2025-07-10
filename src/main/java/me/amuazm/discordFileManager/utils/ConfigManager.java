package me.amuazm.discordFileManager.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;

        plugin.saveDefaultConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }

    public String getBotToken() {
        return plugin.getConfig().getString("bot-token");
    }

    public String getGuildId() {
        return plugin.getConfig().getString("guild-id");
    }

    public String getChannelId() {
        return plugin.getConfig().getString("channel-id");
    }

    public List<String> getAllowedUserIds() {
        return plugin.getConfig().getStringList("allowed-user-ids");
    }

    public boolean validateConfig() {
        boolean configValid = true;

        if (getBotToken().isEmpty()) {
            plugin.getLogger().warning("Bot token is not set in config.yml!");
            configValid = false;
        }

        if (getGuildId().isEmpty()) {
            plugin.getLogger().warning("Guild ID is not set in config.yml!");
            configValid = false;
        }

        if (getChannelId().isEmpty()) {
            plugin.getLogger().warning("Channel ID is not set in config.yml!");
            configValid = false;
        }

        if (getAllowedUserIds().isEmpty() || getAllowedUserIds().stream().allMatch(String::isEmpty)) {
            plugin.getLogger().warning("No allowed user IDs found in config.yml!");
            configValid = false;
        }

        return configValid;
    }
}
