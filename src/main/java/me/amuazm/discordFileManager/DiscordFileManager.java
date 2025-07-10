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

@Getter
public final class DiscordFileManager extends JavaPlugin {
    private ConfigManager configManager;
    private JDA jda = null;

    @Override
    public void onEnable() {
        getLogger().info("DiscordFileManager is starting...");

        this.configManager = new ConfigManager(this);

        getCommand("dfm").setExecutor(new DfmReloadCommand(this));
        getCommand("dfm").setTabCompleter(new DfmReloadCommand(this));

        if (!configManager.validateConfig()) {
            getLogger().severe("Invalid config. Disabling plugin.");
            return;
        }

        HelpCommandListener helpCommandListener = new HelpCommandListener(this);
        FileManager stateMachineFileManager = new FileManager(this, "Morpheus/state_machines", "State Machine", "sm");
        FileManager questFileManager = new FileManager(this, "Quests/quests", "Quest", "q");
        FileManager questItemFileManager = new FileManager(this, "Quests/items", "Quest Item", "qi");

        if (!stateMachineFileManager.isDirValid() || !questFileManager.isDirValid() || !questItemFileManager.isDirValid()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // Initialize JDA with required intents
            jda = JDABuilder.createDefault(configManager.getBotToken())
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(helpCommandListener)
                    .addEventListeners(stateMachineFileManager)
                    .addEventListeners(questFileManager)
                    .addEventListeners(questItemFileManager)
                    .build();

            jda.awaitReady();
            getLogger().info("Discord bot connected successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
            getLogger().info("Discord bot disconnected.");
        }
    }
}
