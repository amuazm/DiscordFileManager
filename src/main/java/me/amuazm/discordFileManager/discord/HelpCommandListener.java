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

        if (args[0].equals("$help") || args[0].equals("$help2") || args[0].equals("$help-dfm")) {
            StringBuilder helpMessage = new StringBuilder("<@" + event.getAuthor().getId() + ">\n");
            helpMessage.append("### 📖 Help\n");
            helpMessage.append("`$help` | `$help2` | `$help-dfm` - Show this message.\n");

            Map<String, ConfigManager.FileManagerConfig> fileManagers = configManager.getFileManagers();

            if (fileManagers.isEmpty()) {
                helpMessage.append("No file managers are currently configured.");
            } else {
                for (Map.Entry<String, ConfigManager.FileManagerConfig> entry : fileManagers.entrySet()) {
                    String prefix = entry.getKey();
                    ConfigManager.FileManagerConfig config = entry.getValue();
                    boolean allowNestedDirs = config.isAllowNestedDirs();

                    helpMessage.append("\n### ").append(config.getEmoji()).append(" ").append(config.getItemCategory()).append(" Files");
                    if (allowNestedDirs) {
                        helpMessage.append(" 📁");
                    }
                    helpMessage.append("\n");

                    // Basic commands
                    if (allowNestedDirs) {
                        helpMessage.append("`$").append(prefix).append("-list [directory/path]` - List files/directories. Use without path to list root directory.\n");
                        helpMessage.append("`$").append(prefix).append("-read <path/to/filename>` - Get a file from any directory.\n");
                        helpMessage.append("`$").append(prefix).append("-upload [path/to/filename]` - Upload a file. Optionally specify directory path.\n");
                        helpMessage.append("`$").append(prefix).append("-delete <path/to/filename>` - Delete a file and upload it as backup.\n");
                    } else {
                        helpMessage.append("`$").append(prefix).append("-list` - List files in the ").append(config.getItemCategory().toLowerCase()).append(" directory.\n");
                        helpMessage.append("`$").append(prefix).append("-read <filename>` - Get a ").append(config.getItemCategory().toLowerCase()).append(" file. Uploads the file in the channel.\n");
                        helpMessage.append("`$").append(prefix).append("-upload` - Upload a ").append(config.getItemCategory().toLowerCase()).append(". Requires an attachment. Replaces an existing file and uploads it in the channel.\n");
                        helpMessage.append("`$").append(prefix).append("-delete <filename>` - Delete a ").append(config.getItemCategory().toLowerCase()).append(" file and uploads it in the channel.\n");
                    }

                    // Directory management commands (only show if nested dirs are enabled)
                    if (allowNestedDirs) {
                        helpMessage.append("`$").append(prefix).append("-mkdir <directory/path>` - Create a new directory.\n");
                        helpMessage.append("`$").append(prefix).append("-rmdir <directory/path>` - Remove an empty directory.\n");
                    }
                }
            }

            // Add examples section if any file manager has nested directories enabled
            boolean hasNestedDirs = fileManagers.values().stream().anyMatch(ConfigManager.FileManagerConfig::isAllowNestedDirs);

            if (hasNestedDirs) {
                helpMessage.append("\n### 📁 Directory Navigation Examples:\n");
                helpMessage.append("`$q-list                         # List root directory`\n");
                helpMessage.append("`$q-list Ancestral               # List 'Ancestral' folder`\n");
                helpMessage.append("`$q-read Ancestral/quest.yml     # Get file from subfolder`\n");
                helpMessage.append("`$q-upload Ancestral/new.yml     # Upload to specific path`\n");
                helpMessage.append("`$q-upload Ancestral/            # Upload with original name`\n");
                helpMessage.append("`$q-mkdir Tower                  # Create new directory`\n");
                helpMessage.append("`$q-delete Ancestral/unused.yml  # Delete from subfolder`\n");
            }

            // Split message if it's too long
            String[] chunks = splitIntoChunks(helpMessage.toString(), 1900);

            for (String chunk : chunks) {
                channel.sendMessage(chunk).queue();
            }
        }
    }

    private String[] splitIntoChunks(String text, int maxChunkSize) {
        if (text.length() <= maxChunkSize) {
            return new String[]{text};
        }

        String[] lines = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        java.util.List<String> chunks = new java.util.ArrayList<>();

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > maxChunkSize) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }

                if (line.length() > maxChunkSize) {
                    // Split long lines
                    for (int i = 0; i < line.length(); i += maxChunkSize) {
                        chunks.add(line.substring(i, Math.min(i + maxChunkSize, line.length())));
                    }
                } else {
                    currentChunk.append(line);
                }
            } else {
                if (!currentChunk.isEmpty()) {
                    currentChunk.append("\n");
                }
                currentChunk.append(line);
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks.toArray(new String[0]);
    }
}