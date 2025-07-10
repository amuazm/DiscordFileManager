package me.amuazm.discordFileManager.discord;

import lombok.Getter;
import me.amuazm.discordFileManager.DiscordFileManager;
import me.amuazm.discordFileManager.utils.ConfigManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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

        switch (args[0]) {
            case "$help2", "$help-dfm", "$sm-help", "$q-help", "$qi-help" -> {
                channel.sendMessage("<@" + event.getAuthor().getId() + ">\n" + """
                        ### 📖 Help
                        `$help2` | `$help-dfm` | `$sm-help` | `$q-help` | `$qi-help` - Show this message.
                        ### 🛠️ Morpheus State Machines
                        `$sm-list` - List files in the Morpheus state machines directory.
                        `$sm-read <filename>` - Get a Morpheus state machine file. Uploads the file in the channel.
                        `$sm-upload` - Upload a Morpheus state machine. Requires an attachment. Replaces an existing file and uploads it in the channel.
                        `$sm-delete <filename>` - Delete a Morpheus state machine and uploads it in the channel.
                        ### 🐲 Quest Files
                        `$q-list` - List files in the quests directory.
                        `$q-read <filename>` - Get a quest file. Uploads the file in the channel.
                        `$q-upload` - Upload a quest file. Requires an attachment. Replaces an existing file and uploads it in the channel.
                        `$q-delete <filename>` - Delete a quest file and uploads it in the channel.
                        ### 🪓 Quest Item Files
                        `$qi-list` - List files in the quest items directory.
                        `$qi-read <filename>` - Get a quest item file. Uploads the file in the channel.
                        `$qi-upload` - Upload a quest item file. Requires an attachment. Replaces an existing file and uploads it in the channel.
                        `$qi-delete <filename>` - Delete a quest item file and uploads it in the channel.""").queue();
            }
        }
    }
}
