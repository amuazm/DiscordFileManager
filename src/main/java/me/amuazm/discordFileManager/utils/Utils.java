package me.amuazm.discordFileManager.utils;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static String[] splitIntoChunks(String text, int maxChunkSize) {
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

    private static List<String> splitLongLine(String line, int maxChunkSize) {
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
