package me.amuazm.discordFileManager;

import lombok.Getter;
import me.amuazm.discordFileManager.commands.DfmReloadCommand;
import me.amuazm.discordFileManager.discord.FileManager;
import me.amuazm.discordFileManager.discord.HelpCommandListener;
import me.amuazm.discordFileManager.utils.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public final class DiscordFileManager extends JavaPlugin {
    private ConfigManager configManager;
    private JDA jda = null;
    private List<FileManager> fileManagers = new ArrayList<>();

    @Override
    public void onEnable() {
        getLogger().info("DiscordFileManager is starting...");

        this.configManager = new ConfigManager(this);

        getCommand("dfm").setExecutor(new DfmReloadCommand(this));
        getCommand("dfm").setTabCompleter(new DfmReloadCommand(this));

        if (!configManager.validateConfig()) {
            getLogger().severe("Invalid config. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeFileManagers();
        initializeDiscordBot();
    }

    private void initializeFileManagers() {
        fileManagers.clear();

        Map<String, ConfigManager.FileManagerConfig> configuredManagers = configManager.getFileManagers();

        for (Map.Entry<String, ConfigManager.FileManagerConfig> entry : configuredManagers.entrySet()) {
            String commandPrefix = entry.getKey();
            ConfigManager.FileManagerConfig config = entry.getValue();

            FileManager fileManager = new FileManager(
                    this,
                    config.getDirFromPluginFolder(),
                    config.getItemCategory(),
                    commandPrefix
            );

            if (!fileManager.isDirValid()) {
                getLogger().severe("Failed to initialize file manager for prefix '" + commandPrefix + "' with directory '" + config.getDirFromPluginFolder() + "'");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            fileManagers.add(fileManager);
            getLogger().info("Initialized file manager: " + commandPrefix + " -> " + config.getDirFromPluginFolder() + " (" + config.getItemCategory() + ")");
        }
    }

    private void initializeDiscordBot() {
        try {
            HelpCommandListener helpCommandListener = new HelpCommandListener(this);

            JDABuilder builder = JDABuilder.createDefault(configManager.getBotToken())
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(helpCommandListener);

            // Add all file managers as event listeners
            for (FileManager fileManager : fileManagers) {
                builder.addEventListeners(fileManager);
            }

            jda = builder.build();
            jda.awaitReady();

            getLogger().info("Discord bot connected successfully with " + fileManagers.size() + " file manager(s)!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Discord bot: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void reloadFileManagers() {
        getLogger().info("Reloading file managers...");

        // Remove old file managers from JDA
        if (jda != null) {
            for (FileManager fileManager : fileManagers) {
                jda.removeEventListener(fileManager);
            }
        }

        // Reinitialize file managers
        initializeFileManagers();

        // Add new file managers to JDA
        if (jda != null) {
            for (FileManager fileManager : fileManagers) {
                jda.addEventListener(fileManager);
            }
        }

        getLogger().info("File managers reloaded successfully!");
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                getLogger().info("Shutting down Discord bot...");

                // Remove all event listeners first to prevent further events
                for (FileManager fileManager : fileManagers) {
                    jda.removeEventListener(fileManager);
                }

                // Shutdown JDA and wait for it to complete
                jda.shutdown();

                // Wait for JDA to fully shutdown (max 10 seconds)
                if (!jda.awaitShutdown(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    getLogger().warning("JDA did not shutdown within 10 seconds, forcing shutdown...");
                    jda.shutdownNow();
                }

                getLogger().info("Discord bot disconnected successfully.");
            } catch (InterruptedException e) {
                getLogger().warning("Interrupted while waiting for JDA shutdown: " + e.getMessage());
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                getLogger().warning("Error during JDA shutdown: " + e.getMessage());
                jda.shutdownNow();
            } finally {
                jda = null;
            }
        }
    }
}