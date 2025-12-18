package com.project.fim;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

// Watches a directory and verifies file integrity using SHA-256
class DirectoryWatcher {
    private static final String FOLDER_NAME = "watched_files";
    private static Map<Path, String> fileHashes = new HashMap<>();
    private static final MongoManager mongoManager = new MongoManager();

    public static void startWatching() throws IOException {
        // Add a shutdown hook to close the MongoDB connection
        Runtime.getRuntime().addShutdownHook(new Thread(mongoManager::close));

        Path folderPath = Paths.get(FOLDER_NAME);
        Files.createDirectories(folderPath);
        loadHashes();

        System.out.println("👀👀 Watching: " + folderPath.toAbsolutePath());

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            folderPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
            );

            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> eventType = event.kind();
                    Path changedFile = folderPath.resolve((Path) event.context());
                    Path relativePath = folderPath.relativize(changedFile);

                    if (eventType == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isRegularFile(changedFile) && !fileHashes.containsKey(relativePath)) {
                            System.out.println("🎁🎁 Created: " + relativePath);
                            String newHash = computeSHA256(changedFile);
                            fileHashes.put(relativePath, newHash);
                            mongoManager.logEvent(relativePath, "CREATE", newHash);
                            logToSyslog("Created: " + relativePath);
                            System.out.println("SHA256: " + newHash);
                        }
                    } else if (eventType == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (Files.isRegularFile(changedFile)) {
                            String newHash = computeSHA256(changedFile);
                            String oldHash = fileHashes.get(relativePath);

                            if (!newHash.equals(oldHash)) {
                                System.out.println("🔧🔧 Modified: " + relativePath);
                                System.out.println("File content changed!");
                                System.out.println("   Old: " + oldHash);
                                System.out.println("   New: " + newHash);
                                fileHashes.put(relativePath, newHash);
                                mongoManager.logEvent(relativePath, "MODIFY", newHash);
                                logToSyslog("Modified: " + relativePath);
                                System.out.println("SHA256: " + newHash);
                            }
                        }
                    } else if (eventType == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (fileHashes.containsKey(relativePath)) {
                            System.out.println("🗑️🗑️ Deleted: " + relativePath);
                            fileHashes.remove(relativePath);
                            mongoManager.logEvent(relativePath, "DELETE", null);
                            logToSyslog("Deleted: " + relativePath);
                        }
                    }
                }

                if (!key.reset()) break;
            }
        }
    }

    // Computes SHA-256 hash of a file
    private static String computeSHA256(Path filePath) throws IOException {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = sha256.digest(fileBytes);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("SHA-256 hash fail", e);
        }
    }

    // Load saved hashes from MongoDB
    private static void loadHashes() {
        System.out.println("Loading hash baseline from MongoDB...");
        fileHashes = mongoManager.loadHashes();
        if (fileHashes.isEmpty()) {
            System.out.println("No saved hash baseline found in the database.");
        } else {
            System.out.println("Loaded " + fileHashes.size() + " file hashes from the database.");
        }
    }

    // Send message to system log
    private static void logToSyslog(String message) {
        try {
            Runtime.getRuntime().exec(new String[] {
                    "logger", "FIM-Watcher: " + message
            });
        } catch (IOException e) {
            System.out.println("Failed to log to syslog.");
        }
    }
}

public class Main {
    public static void main(String[] args) {
        try {
            DirectoryWatcher.startWatching();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
