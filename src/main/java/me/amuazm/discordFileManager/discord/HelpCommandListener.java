package me.amuazm.discordFileManager.discord;

import lombok.Getter;
import me.amuazm.discordFileManager.DiscordFileManager;
import me.amuazm.discordFileManager.utils.ConfigManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;

@Getter
public class HelpCommandListener extends ListenerAdapter {
    private final DiscordFileManager plugin;
    private final ConfigManager configManager;

    public HelpCommandListener(DiscordFileManager plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages from bots (including our own bot)
        if (event.getAuthor().isBot()) {
            return;
        }

        // Check if message is from the correct guild and channel
        if (!event.getGuild().getId().equals(configManager.getGuildId())) {
            return;
        }

        if (!event.getChannel().getId().equals(configManager.getChannelId())) {
            return;
        }

        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();

        // Check if user is allowed to run this command
        if (!configManager.getAllowedUserIds().contains(event.getAuthor().getId())) {
            return;
        }

        String messageContent = message.getContentRaw();
        String[] args = messageContent.split(" ");

        if (args.length == 0) {
            return;
        }

        if (args[0].equals("$help2") || args[0].equals("$help-dfm")) {
            StringBuilder helpMessage = new StringBuilder("<@" + event.getAuthor().getId() + ">\n");
            helpMessage.append("### 📖 Help\n");
            helpMessage.append("`$help2` | `$help-dfm` - Show this message.\n");

            Map<String, ConfigManager.FileManagerConfig> fileManagers = configManager.getFileManagers();

            if (fileManagers.isEmpty()) {
                helpMessage.append("No file managers are currently configured.");
            } else {
                for (Map.Entry<String, ConfigManager.FileManagerConfig> entry : fileManagers.entrySet()) {
                    String prefix = entry.getKey();
                    ConfigManager.FileManagerConfig config = entry.getValue();

                    helpMessage.append("### ").append(getEmojiForCategory(config.getItemCategory())).append(" ").append(config.getItemCategory()).append(" Files\n");
                    helpMessage.append("`$").append(prefix).append("-list` - List files in the ").append(config.getItemCategory().toLowerCase()).append(" directory.\n");
                    helpMessage.append("`$").append(prefix).append("-read <filename>` - Get a ").append(config.getItemCategory().toLowerCase()).append(" file. Uploads the file in the channel.\n");
                    helpMessage.append("`$").append(prefix).append("-upload` - Upload a ").append(config.getItemCategory().toLowerCase()).append(". Requires an attachment. Replaces an existing file and uploads it in the channel.\n");
                    helpMessage.append("`$").append(prefix).append("-delete <filename>` - Delete a ").append(config.getItemCategory().toLowerCase()).append(" file and uploads it in the channel.\n");
                }
            }

            channel.sendMessage(helpMessage.toString()).queue();
        }
    }

    private String getEmojiForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "schematic", "schematics" -> "🏗️";
            case "state machine", "state machines" -> "🛠️";
            case "quest", "quests" -> "🐲";
            case "quest item", "quest items" -> "🪓";
            case "world", "worlds" -> "🌍";
            case "datapack", "datapacks" -> "📦";
            case "plugin", "plugins" -> "🔌";
            case "config", "configs", "configuration" -> "⚙️";
            case "script", "scripts" -> "📜";
            case "backup", "backups" -> "💾";
            default -> "📁";
        };
    }
}