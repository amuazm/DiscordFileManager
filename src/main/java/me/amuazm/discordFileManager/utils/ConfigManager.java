package me.amuazm.discordFileManager.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, FileManagerConfig> getFileManagers() {
        Map<String, FileManagerConfig> fileManagers = new HashMap<>();

        ConfigurationSection fileManagersSection = plugin.getConfig().getConfigurationSection("file-managers");
        if (fileManagersSection == null) {
            return fileManagers;
        }

        for (String commandPrefix : fileManagersSection.getKeys(false)) {
            ConfigurationSection managerSection = fileManagersSection.getConfigurationSection(commandPrefix);
            if (managerSection == null) {
                continue;
            }

            String dirFromPluginFolder = managerSection.getString("dir-from-plugin-folder");
            String itemCategory = managerSection.getString("item-category");

            if (dirFromPluginFolder != null && itemCategory != null) {
                fileManagers.put(commandPrefix, new FileManagerConfig(dirFromPluginFolder, itemCategory));
            } else {
                plugin.getLogger().warning("Invalid file manager configuration for prefix '" + commandPrefix + "': missing dir-from-plugin-folder or item-category");
            }
        }

        return fileManagers;
    }

    public boolean validateConfig() {
        boolean configValid = true;

        if (getBotToken() == null || getBotToken().isEmpty()) {
            plugin.getLogger().warning("Bot token is not set in config.yml!");
            configValid = false;
        }

        if (getGuildId() == null || getGuildId().isEmpty()) {
            plugin.getLogger().warning("Guild ID is not set in config.yml!");
            configValid = false;
        }

        if (getChannelId() == null || getChannelId().isEmpty()) {
            plugin.getLogger().warning("Channel ID is not set in config.yml!");
            configValid = false;
        }

        if (getAllowedUserIds().isEmpty() || getAllowedUserIds().stream().allMatch(String::isEmpty)) {
            plugin.getLogger().warning("No allowed user IDs found in config.yml!");
            configValid = false;
        }

        Map<String, FileManagerConfig> fileManagers = getFileManagers();
        if (fileManagers.isEmpty()) {
            plugin.getLogger().warning("No file managers configured in config.yml!");
            configValid = false;
        }

        return configValid;
    }

    public static class FileManagerConfig {
        private final String dirFromPluginFolder;
        private final String itemCategory;

        public FileManagerConfig(String dirFromPluginFolder, String itemCategory) {
            this.dirFromPluginFolder = dirFromPluginFolder;
            this.itemCategory = itemCategory;
        }

        public String getDirFromPluginFolder() {
            return dirFromPluginFolder;
        }

        public String getItemCategory() {
            return itemCategory;
        }
    }
}