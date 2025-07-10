package me.amuazm.discordFileManager.discord;

import lombok.Getter;
import me.amuazm.discordFileManager.DiscordFileManager;
import me.amuazm.discordFileManager.utils.ConfigManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

@Getter
public class FileManager extends ListenerAdapter {
    private final DiscordFileManager plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final String dirFromPluginFolder;
    private final String itemCategory;
    private final String commandPrefix;
    private final File rootDir;
    private boolean isDirValid = true;

    private final String listCommand;
    private final String readCommand;
    private final String uploadCommand;
    private final String deleteCommand;

    public FileManager(DiscordFileManager plugin, String dirFromPluginFolder, String itemCategory, String commandPrefix) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getLogger();
        this.dirFromPluginFolder = dirFromPluginFolder;
        this.itemCategory = itemCategory;
        this.commandPrefix = commandPrefix;
        this.rootDir = new File(Bukkit.getPluginsFolder(), dirFromPluginFolder);

        listCommand = "$" + commandPrefix + "-list";
        readCommand = "$" + commandPrefix + "-read";
        uploadCommand = "$" + commandPrefix + "-upload";
        deleteCommand = "$" + commandPrefix + "-delete";

        // Create directory if it doesn't exist
        if (!rootDir.exists()) {
            logger.info(dirFromPluginFolder + " does not exist, attempting to create directory.");

            if (!rootDir.mkdirs()) {
                logger.severe("Could not create the folder plugins/" + dirFromPluginFolder + "!");
                isDirValid = false;
                return;
            }
        }

        if (!rootDir.isDirectory()) {
            logger.severe(dirFromPluginFolder + " is not a directory!");
            isDirValid = false;
            return;
        }
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

        List<Message.Attachment> attachments = message.getAttachments();

        if (args[0].equals(listCommand)) {
            try {
                File[] files = rootDir.listFiles();

                if (files == null || files.length == 0) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 No files found inside `" + dirFromPluginFolder + "`").queue();
                    return;
                }

                // Sort files by name
                Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));

                StringBuilder fileList = new StringBuilder("<@" + event.getAuthor().getId() + ">\n### 📖 " + itemCategory + " Files:\n");

                for (File file : files) {
                    if (file.isFile()) {
                        fileList.append("- `").append(file.getName()).append("`\n");
                    }
                }

                Arrays.stream(splitIntoChunks(fileList.toString(), 1900)).forEach(s -> {
                    channel.sendMessage(s).queue();
                });
            } catch (Exception e) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ An error occurred while listing " + itemCategory + " files.").queue();
                logger.severe("Error listing " + itemCategory + " files: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (args[0].equals(readCommand)) {
            if (args.length < 2) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 Usage: `" + readCommand + " <filename>`").queue();
                return;
            }

            String filename = args[1];

            if (handleFilename(filename, event)) {
                return;
            }

            try {
                File targetFile = new File(rootDir, filename);

                if (handleFile(targetFile, event)) {
                    return;
                }

                // Send file via DM
                uploadFile(targetFile, event);
            } catch (Exception e) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error accessing " + itemCategory + " file: " + filename).queue();
                logger.severe("Error accessing " + itemCategory + " file: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (args[0].equals(uploadCommand)) {
            if (attachments.isEmpty()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Please attach a file to upload.").queue();
                return;
            }

            if (attachments.size() > 1) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Please attach only one file at a time.").queue();
                return;
            }

            Message.Attachment attachment = attachments.get(0);
            String filename = attachment.getFileName();

            if (handleFilename(filename, event)) {
                return;
            }

            try {
                File targetFile = new File(rootDir, filename);

                // Upload any existing file to the channel
                if (targetFile.exists()) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 File `" + filename + "` already exists. Uploading existing here and replacing with new file in server.").queue();
                    uploadFile(targetFile, event);
                }

                // Download and save the file
                attachment.getProxy().downloadToFile(targetFile).whenComplete((file, throwable) -> {
                    if (throwable != null) {
                        channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to upload " + itemCategory + " file: " + throwable.getMessage()).queue();
                        logger.severe("Error uploading " + itemCategory + " file: " + throwable.getMessage());
                        throwable.printStackTrace();
                    } else {
                        channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully uploaded `" + filename + "` to " + dirFromPluginFolder).queue();
                        logger.info(itemCategory + " file uploaded: " + filename + " by " + event.getAuthor().getName());
                    }
                });
            } catch (Exception e) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error uploading " + itemCategory + " file: " + e.getMessage()).queue();
                logger.severe("Error uploading " + itemCategory + " file: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (args[0].equals(deleteCommand)) {
            if (args.length < 2) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Usage: `" + deleteCommand + " <filename>`").queue();
                return;
            }

            String filename = args[1];

            if (handleFilename(filename, event)) {
                return;
            }

            try {
                File targetFile = new File(rootDir, filename);

                if (handleFile(targetFile, event)) {
                    return;
                }

                // Upload as backup
                channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 Uploading file to delete as backup.").queue();
                uploadFile(targetFile, event);

                // Attempt to delete the file
                if (targetFile.delete()) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully deleted `" + filename + "` from " + dirFromPluginFolder).queue();
                    logger.info(itemCategory + " file deleted: " + filename + " by " + event.getAuthor().getName());
                } else {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to delete `" + filename + "`. The file may be in use or permission denied.").queue();
                    logger.warning("Failed to delete " + itemCategory + " file: " + filename);
                }
            } catch (Exception e) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error deleting file: " + e.getMessage()).queue();
                logger.severe("Error deleting " + itemCategory + " file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private boolean handleFilename(String filename, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (filename.trim().isEmpty()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Filename cannot be empty.").queue();
            return true;
        }

        // Prevent directory travel
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid filename `" + filename + "`. Filename cannot contain path separators or '..' sequences.").queue();
            return true;
        }

        return false;
    }

    private boolean handleFile(File file, MessageReceivedEvent event) throws IOException {
        MessageChannel channel = event.getChannel();

        if (!file.exists()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ File not found: `" + file.getName() + "`").queue();
            return true;
        }

        if (!file.isFile()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ `" + file.getName() + "` is not a file.").queue();
            return true;
        }

        // Ensure file is within the allowed directory
        if (!file.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid file path.").queue();
            return true;
        }

        return false;
    }

    private void uploadFile(File file, MessageReceivedEvent event) throws IOException {
        uploadFile(Files.readAllBytes(file.toPath()), file.getName(), event);
    }

    private void uploadFile(byte[] data, String fileName, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        // Check file size (Discord 8MB limit)
        long maxSizeBytes = 8 * 1024 * 1024;

        if (data.length > maxSizeBytes) {
            double fileSizeInMB = data.length / (1024.0 * 1024.0);
            channel.sendMessage(String.format("@%s ❌ The file %s is too large (%.2f MB). Discord limit is 8MB.", event.getAuthor().getEffectiveName(), fileName, fileSizeInMB)).queue();
            return;
        }

        channel.sendMessage("<@" + event.getAuthor().getId() + ">")
                .addFiles(FileUpload.fromData(data, fileName))
                .queue(success -> {
                }, throwable -> {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to upload file. Error: " + throwable.getMessage()).queue();
                    logger.warning("Failed to upload file: " + throwable.getMessage());
                    throwable.printStackTrace();
                });

        logger.info("Uploaded file to discord: " + fileName);
    }

    private String[] splitIntoChunks(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");

        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            // Check if adding this line would exceed the limit
            // +1 for the newline character we'll add
            if (currentChunk.length() + line.length() + 1 > maxChunkSize) {
                // If current chunk has content, save it and start a new one
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }

                // Handle case where a single line is longer than maxChunkSize
                if (line.length() > maxChunkSize) {
                    // Split the long line at word boundaries if possible
                    chunks.addAll(splitLongLine(line, maxChunkSize));
                } else {
                    currentChunk.append(line);
                }
            } else {
                // Add newline if this isn't the first line in the chunk
                if (!currentChunk.isEmpty()) {
                    currentChunk.append("\n");
                }
                currentChunk.append(line);
            }
        }

        // Add the last chunk if it has content
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks.toArray(new String[0]);
    }

    private List<String> splitLongLine(String line, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();

        // Try to split at word boundaries first
        String[] words = line.split(" ");
        StringBuilder currentChunk = new StringBuilder();

        for (String word : words) {
            // Check if adding this word would exceed the limit
            // +1 for the space we'll add
            if (currentChunk.length() + word.length() + 1 > maxChunkSize) {
                // If current chunk has content, save it
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }

                // If even a single word is too long, we have to split it
                if (word.length() > maxChunkSize) {
                    for (int i = 0; i < word.length(); i += maxChunkSize) {
                        chunks.add(word.substring(i, Math.min(i + maxChunkSize, word.length())));
                    }
                } else {
                    currentChunk.append(word);
                }
            } else {
                // Add space if this isn't the first word in the chunk
                if (!currentChunk.isEmpty()) {
                    currentChunk.append(" ");
                }
                currentChunk.append(word);
            }
        }

        // Add the last chunk if it has content
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }
}
