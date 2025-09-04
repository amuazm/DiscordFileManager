package me.amuazm.discordFileManager.discord;

import lombok.Getter;
import me.amuazm.discordFileManager.DiscordFileManager;
import me.amuazm.discordFileManager.utils.ConfigManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static me.amuazm.discordFileManager.utils.Utils.splitIntoChunks;

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
    private final boolean allowNestedDirs;

    private final String listCommand;
    private final String readCommand;
    private final String uploadCommand;
    private final String deleteCommand;
    private final String mkdirCommand;
    private final String rmdirCommand;
    private final String searchCommand;

    // Store search results temporarily for button interactions
    private final Map<String, List<File>> searchResultsCache = new HashMap<>();

    public FileManager(DiscordFileManager plugin, String dirFromPluginFolder, String itemCategory, String commandPrefix, boolean allowNestedDirs) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.logger = plugin.getLogger();
        this.dirFromPluginFolder = dirFromPluginFolder;
        this.itemCategory = itemCategory;
        this.commandPrefix = commandPrefix;
        this.rootDir = new File(Bukkit.getPluginsFolder(), dirFromPluginFolder);
        this.allowNestedDirs = allowNestedDirs;

        listCommand = "$" + commandPrefix + "-list";
        readCommand = "$" + commandPrefix + "-read";
        uploadCommand = "$" + commandPrefix + "-upload";
        deleteCommand = "$" + commandPrefix + "-delete";
        mkdirCommand = "$" + commandPrefix + "-mkdir";
        rmdirCommand = "$" + commandPrefix + "-rmdir";
        searchCommand = "$" + commandPrefix + "-search";

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
            handleListCommand(args, event);
        } else if (args[0].equals(readCommand)) {
            handleReadCommand(args, event);
        } else if (args[0].equals(uploadCommand)) {
            handleUploadCommand(args, attachments, event);
        } else if (args[0].equals(deleteCommand)) {
            handleDeleteCommand(args, event);
        } else if (args[0].equals(mkdirCommand) && allowNestedDirs) {
            handleMkdirCommand(args, event);
        } else if (args[0].equals(rmdirCommand) && allowNestedDirs) {
            handleRmdirCommand(args, event);
        } else if (args[0].equals(searchCommand)) {
            handleSearchCommand(args, event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.isAcknowledged()) {
            return;
        }

        String buttonId = event.getButton().getId();

        if (buttonId == null || !buttonId.startsWith("download_search_")) {
            return;
        }

        Member member = event.getMember();

        if (member == null) {
            return;
        }

        // Check if user is allowed
        if (!configManager.getAllowedUserIds().contains(event.getUser().getId())) {
            event.reply("<@" + member.getId() + "> ❌ You don't have permission to use this button.").queue();
            return;
        }

        // Extract the search ID from the button ID
        String searchId = buttonId.substring("download_search_".length());
        List<File> files = searchResultsCache.get(searchId);

        if (files == null || files.isEmpty()) {
            event.reply("<@" + member.getId() + "> ❌ Search results have expired. Please run the search again.").queue();
            return;
        }

        try {
            // Create zip file in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            for (File file : files) {
                if (file.exists() && file.isFile()) {
                    addFileToZip(file, rootDir, zos);
                }
            }

            zos.close();
            byte[] zipData = baos.toByteArray();

            // Check file size (Discord 8MB limit)
            long maxSizeBytes = 8 * 1024 * 1024;

            if (zipData.length > maxSizeBytes) {
                double fileSizeInMB = zipData.length / (1024.0 * 1024.0);
                event.reply(String.format("<@" + member.getId() + "> ❌ The resulting zip file is too large (%.2f MB). Discord limit is 8MB.", fileSizeInMB)).queue();
                return;
            }

            // Upload the zip file
            String zipFileName = "search_results_" + searchId + ".zip";
            event.reply("<@" + member.getId() + "> ✅ Here are your search results:")
                    .addFiles(FileUpload.fromData(zipData, zipFileName))
                    .queue();

            logger.info("Created and uploaded zip file with " + files.size() + " files for search ID: " + searchId);

            // Clean up the cache entry after successful download
            searchResultsCache.remove(searchId);
        } catch (Exception e) {
            event.reply("<@" + member.getId() + "> ❌ Error creating zip file: " + e.getMessage()).queue();
            logger.severe("Error creating zip file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSearchCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Usage: `" + searchCommand + " <prefix>` - searches for files starting with the given prefix").queue();
            return;
        }

        // Join all arguments after the command to form the search query
        String searchQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();

        if (searchQuery.length() <= 3) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Search query must be 4 or more characters.").queue();
        }

        try {
            // Search for files recursively if nested dirs allowed, otherwise just in root
            List<File> matchingFiles = new ArrayList<>();
            searchFiles(rootDir, searchQuery, matchingFiles);

            if (matchingFiles.isEmpty()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 No files found starting with `" + searchQuery + "`").queue();
                return;
            }

            // Sort files alphabetically
            matchingFiles.sort((f1, f2) -> {
                String path1 = getRelativePath(f1);
                String path2 = getRelativePath(f2);
                return path1.toLowerCase().compareTo(path2.toLowerCase());
            });

            // Build the results message
            StringBuilder results = new StringBuilder("<@" + event.getAuthor().getId() + ">\n### 🔍 Search Results for prefix `" + searchQuery + "`:\n");
            results.append("Found **").append(matchingFiles.size()).append(" file(s)**:\n\n");

            for (File file : matchingFiles) {
                String relativePath = getRelativePath(file);
                results.append("📄 `").append(relativePath).append("`\n");
            }

            // Generate a unique ID for this search
            String searchId = UUID.randomUUID().toString().substring(0, 8);

            // Store the results in cache
            searchResultsCache.put(searchId, new ArrayList<>(matchingFiles));

            // Clean up old cache entries (keep only last 10 searches)
            if (searchResultsCache.size() > 10) {
                Iterator<String> iterator = searchResultsCache.keySet().iterator();
                iterator.next();
                iterator.remove();
            }

            // Create download button
            Button downloadButton = Button.primary("download_search_" + searchId, "📥 Download as ZIP");

            // Send message with results and button
            String[] chunks = splitIntoChunks(results.toString(), 1900);

            // Send all chunks except the last one
            for (int i = 0; i < chunks.length - 1; i++) {
                channel.sendMessage(chunks[i]).queue();
            }

            // Send the last chunk with the button
            if (chunks.length > 0) {
                channel.sendMessage(chunks[chunks.length - 1])
                        .setActionRow(downloadButton)
                        .queue();
            }

            logger.info("Search performed for prefix '" + searchQuery + "' by " + event.getAuthor().getName() + " - Found " + matchingFiles.size() + " files");

        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error searching files: " + e.getMessage()).queue();
            logger.severe("Error searching files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void searchFiles(File directory, String prefix, List<File> results) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().startsWith(prefix)) {
                results.add(file);
            }

            // Recursively search subdirectories if nested dirs are allowed
            if (allowNestedDirs && file.isDirectory()) {
                searchFiles(file, prefix, results);
            }
        }
    }

    private String getRelativePath(File file) {
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = rootDir.getCanonicalPath();

            if (filePath.startsWith(rootPath)) {
                String relativePath = filePath.substring(rootPath.length());
                if (relativePath.startsWith(File.separator)) {
                    relativePath = relativePath.substring(1);
                }
                return relativePath.replace(File.separator, "/");
            }
        } catch (IOException e) {
            logger.warning("Error getting relative path for file: " + file.getPath());
        }
        return file.getName();
    }

    private void addFileToZip(File file, File rootDir, ZipOutputStream zos) throws IOException {
        String relativePath = getRelativePath(file);

        ZipEntry zipEntry = new ZipEntry(relativePath);
        zos.putNextEntry(zipEntry);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }

        zos.closeEntry();
    }

    private void handleListCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        try {
            String relativePath = "";
            if (args.length > 1) {
                // Join all arguments after the command to form the path
                relativePath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }

            File targetDir = resolveDirectory(relativePath);
            if (targetDir == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid directory path: `" + relativePath + "`").queue();
                return;
            }

            File[] files = targetDir.listFiles();
            String displayPath = relativePath.isEmpty() ? dirFromPluginFolder : dirFromPluginFolder + "/" + relativePath;

            if (files == null || files.length == 0) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 No files found inside `" + displayPath + "`").queue();
                return;
            }

            // Sort files: directories first, then files, both alphabetically
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
            });

            StringBuilder fileList = new StringBuilder("<@" + event.getAuthor().getId() + ">\n### 📖 " + itemCategory + " Files in `" + displayPath + "`:\n");

            // Add parent directory navigation if we're in a subdirectory
            if (!relativePath.isEmpty() && allowNestedDirs) {
                String parentPath = getParentPath(relativePath);
                String parentDisplay = parentPath.isEmpty() ? ".." : "../" + parentPath;
                fileList.append("📁 `").append(parentDisplay).append("/` (parent directory)\n");
            }

            for (File file : files) {
                if (file.isDirectory() && allowNestedDirs) {
                    fileList.append("📁 `").append(file.getName()).append("/`\n");
                } else if (file.isFile()) {
                    fileList.append("📄 `").append(file.getName()).append("`\n");
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
    }

    private void handleReadCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            String usage = allowNestedDirs ?
                    "`" + readCommand + " <path/to/filename>` or `" + readCommand + " <filename>`" :
                    "`" + readCommand + " <filename>`";
            channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 Usage: " + usage).queue();
            return;
        }

        // Join all arguments after the command to form the file path
        String filePath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (handleFilePath(filePath, event)) {
            return;
        }

        try {
            File targetFile = resolveFile(filePath);
            if (targetFile == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid file path: `" + filePath + "`").queue();
                return;
            }

            if (handleFile(targetFile, event)) {
                return;
            }

            // Send file via channel
            uploadFile(targetFile, event);
        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error accessing " + itemCategory + " file: " + filePath).queue();
            logger.severe("Error accessing " + itemCategory + " file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleUploadCommand(String[] args, List<Message.Attachment> attachments, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (attachments.isEmpty()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Please attach a file to upload.").queue();
            return;
        }

        if (attachments.size() > 1) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Please attach only one file at a time.").queue();
            return;
        }

        Message.Attachment attachment = attachments.getFirst();
        String originalFilename = attachment.getFileName();
        String targetPath;

        if (args.length > 1) {
            // Custom path specified
            targetPath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            // If the path ends with /, append the original filename
            if (targetPath.endsWith("/")) {
                targetPath += originalFilename;
            }
        } else {
            // No path specified, use original filename in root
            targetPath = originalFilename;
        }

        if (handleFilePath(targetPath, event)) {
            return;
        }

        try {
            // Ensure parent directories exist if nested dirs are allowed
            File targetFile = resolveFile(targetPath);
            if (targetFile == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid file path: `" + targetPath + "`").queue();
                return;
            }

            // Check if the target path is an existing directory
            if (targetFile.exists() && targetFile.isDirectory()) {
                String suggestion = allowNestedDirs ?
                        "To upload to this directory, use: `" + uploadCommand + " " + targetPath + "/`" :
                        "Cannot upload to directories. Please specify a filename.";
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ `" + targetPath + "` is a directory, not a file. " + suggestion).queue();
                return;
            }

            // Create parent directories if they don't exist and nested dirs are allowed
            if (allowNestedDirs && targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                if (!targetFile.getParentFile().mkdirs()) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to create parent directories for: `" + targetPath + "`").queue();
                    return;
                }
            }

            // Upload any existing file to the channel
            if (targetFile.exists()) {
                // Double-check it's actually a file (not a directory) before trying to read it
                if (!targetFile.isFile()) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Cannot overwrite `" + targetPath + "` - it's not a file.").queue();
                    return;
                }
                channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 File `" + targetPath + "` already exists. Uploading existing here and replacing with new file in server.").queue();
                uploadFile(targetFile, event);
            }

            // Download and save the file
            String finalTargetPath = targetPath;
            attachment.getProxy().downloadToFile(targetFile).whenComplete((file, throwable) -> {
                if (throwable != null) {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to upload " + itemCategory + " file: " + throwable.getMessage()).queue();
                    logger.severe("Error uploading " + itemCategory + " file: " + throwable.getMessage());
                    throwable.printStackTrace();
                } else {
                    channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully uploaded `" + finalTargetPath + "` to " + dirFromPluginFolder).queue();
                    logger.info(itemCategory + " file uploaded: " + finalTargetPath + " by " + event.getAuthor().getName());
                }
            });
        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error uploading " + itemCategory + " file: " + e.getMessage()).queue();
            logger.severe("Error uploading " + itemCategory + " file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDeleteCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            String usage = allowNestedDirs ?
                    "`" + deleteCommand + " <path/to/filename>` or `" + deleteCommand + " <filename>`" :
                    "`" + deleteCommand + " <filename>`";
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Usage: " + usage).queue();
            return;
        }

        // Join all arguments after the command to form the file path
        String filePath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (handleFilePath(filePath, event)) {
            return;
        }

        try {
            File targetFile = resolveFile(filePath);
            if (targetFile == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid file path: `" + filePath + "`").queue();
                return;
            }

            if (handleFile(targetFile, event)) {
                return;
            }

            // Upload as backup
            channel.sendMessage("<@" + event.getAuthor().getId() + "> 📖 Uploading file to delete as backup.").queue();
            uploadFile(targetFile, event);

            // Attempt to delete the file
            if (targetFile.delete()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully deleted `" + filePath + "` from " + dirFromPluginFolder).queue();
                logger.info(itemCategory + " file deleted: " + filePath + " by " + event.getAuthor().getName());
            } else {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to delete `" + filePath + "`. The file may be in use or permission denied.").queue();
                logger.warning("Failed to delete " + itemCategory + " file: " + filePath);
            }
        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error deleting file: " + e.getMessage()).queue();
            logger.severe("Error deleting " + itemCategory + " file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleMkdirCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Usage: `" + mkdirCommand + " <directory/path>`").queue();
            return;
        }

        // Join all arguments after the command to form the directory path
        String dirPath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (handleDirectoryPath(dirPath, event)) {
            return;
        }

        try {
            File targetDir = resolveDirectory(dirPath);
            if (targetDir == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid directory path: `" + dirPath + "`").queue();
                return;
            }

            if (targetDir.exists()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Directory `" + dirPath + "` already exists.").queue();
                return;
            }

            if (targetDir.mkdirs()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully created directory `" + dirPath + "`").queue();
                logger.info("Directory created: " + dirPath + " by " + event.getAuthor().getName());
            } else {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to create directory `" + dirPath + "`").queue();
                logger.warning("Failed to create directory: " + dirPath);
            }
        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error creating directory: " + e.getMessage()).queue();
            logger.severe("Error creating directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleRmdirCommand(String[] args, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Usage: `" + rmdirCommand + " <directory/path>`").queue();
            return;
        }

        // Join all arguments after the command to form the directory path
        String dirPath = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (handleDirectoryPath(dirPath, event)) {
            return;
        }

        try {
            File targetDir = resolveDirectory(dirPath);
            if (targetDir == null) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid directory path: `" + dirPath + "`").queue();
                return;
            }

            if (!targetDir.exists()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Directory `" + dirPath + "` does not exist.").queue();
                return;
            }

            if (!targetDir.isDirectory()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ `" + dirPath + "` is not a directory.").queue();
                return;
            }

            // Check if directory is empty
            File[] files = targetDir.listFiles();
            if (files != null && files.length > 0) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Directory `" + dirPath + "` is not empty. Please delete all files and subdirectories first.").queue();
                return;
            }

            if (targetDir.delete()) {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ✅ Successfully deleted directory `" + dirPath + "`").queue();
                logger.info("Directory deleted: " + dirPath + " by " + event.getAuthor().getName());
            } else {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Failed to delete directory `" + dirPath + "`").queue();
                logger.warning("Failed to delete directory: " + dirPath);
            }
        } catch (Exception e) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error deleting directory: " + e.getMessage()).queue();
            logger.severe("Error deleting directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private File resolveFile(String relativePath) {
        try {
            // Normalize the path and resolve it against the root directory
            Path normalizedPath = Paths.get(relativePath).normalize();
            File targetFile = new File(rootDir, normalizedPath.toString());

            // Ensure the resolved file is within the root directory
            if (!targetFile.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
                return null;
            }

            return targetFile;
        } catch (IOException e) {
            logger.warning("Error resolving file path: " + relativePath + " - " + e.getMessage());
            return null;
        }
    }

    private File resolveDirectory(String relativePath) {
        if (relativePath.isEmpty()) {
            return rootDir;
        }

        try {
            // Normalize the path and resolve it against the root directory
            Path normalizedPath = Paths.get(relativePath).normalize();
            File targetDir = new File(rootDir, normalizedPath.toString());

            // Ensure the resolved directory is within the root directory
            if (!targetDir.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
                return null;
            }

            return targetDir;
        } catch (IOException e) {
            logger.warning("Error resolving directory path: " + relativePath + " - " + e.getMessage());
            return null;
        }
    }

    private String getParentPath(String path) {
        Path pathObj = Paths.get(path);
        Path parent = pathObj.getParent();
        return parent != null ? parent.toString().replace("\\", "/") : "";
    }

    private boolean handleFilePath(String filePath, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (filePath.trim().isEmpty()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ File path cannot be empty.").queue();
            return true;
        }

        // More restrictive path validation
        if (filePath.contains("..") || (!allowNestedDirs && (filePath.contains("/") || filePath.contains("\\")))) {
            String message = allowNestedDirs ?
                    "❌ Invalid file path `" + filePath + "`. Path cannot contain '..' sequences." :
                    "❌ Invalid filename `" + filePath + "`. Filename cannot contain path separators or '..' sequences.";
            channel.sendMessage("<@" + event.getAuthor().getId() + "> " + message).queue();
            return true;
        }

        return false;
    }

    private boolean handleDirectoryPath(String dirPath, MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (dirPath.trim().isEmpty()) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Directory path cannot be empty.").queue();
            return true;
        }

        // Path validation for directories
        if (dirPath.contains("..")) {
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Invalid directory path `" + dirPath + "`. Path cannot contain '..' sequences.").queue();
            return true;
        }

        return false;
    }

    private boolean handleFilename(String filename, MessageReceivedEvent event) {
        return handleFilePath(filename, event);
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

    private void uploadFile(File file, MessageReceivedEvent event) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            uploadFile(data, file.getName(), event);
        } catch (java.nio.file.AccessDeniedException e) {
            MessageChannel channel = event.getChannel();
            logger.warning("Access denied when trying to read file: " + file.getPath() + " - " + e.getMessage());

            if (file.isDirectory()) {
                String suggestion = allowNestedDirs ?
                        "To upload a file to this directory, specify the full path including filename." :
                        "Directories cannot be uploaded. Please specify a file.";
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ `" + file.getName() + "` is a directory. " + suggestion).queue();
            } else {
                channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Access denied when reading file `" + file.getName() + "`. The file may be in use by another process or you may not have permission to read it.").queue();
            }
        } catch (IOException e) {
            MessageChannel channel = event.getChannel();
            logger.warning("IO error when trying to read file: " + file.getPath() + " - " + e.getMessage());
            channel.sendMessage("<@" + event.getAuthor().getId() + "> ❌ Error reading file `" + file.getName() + "`: " + e.getMessage()).queue();
        }
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
}