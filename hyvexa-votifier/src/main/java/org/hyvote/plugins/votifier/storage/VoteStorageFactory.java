package org.hyvote.plugins.votifier.storage;

import com.hypixel.hytale.logger.HytaleLogger;
import org.hyvote.plugins.votifier.VoteStorageConfig;

import java.nio.file.Path;

/**
 * Factory for creating {@link VoteStorage} instances based on configuration.
 *
 * <p>Supports the following storage types:</p>
 * <ul>
 *   <li>{@code memory} - Creates an {@link InMemoryVoteStorage}</li>
 *   <li>{@code sqlite} - Creates a {@link SQLiteVoteStorage}</li>
 * </ul>
 */
public final class VoteStorageFactory {

    private VoteStorageFactory() {
        // Utility class
    }

    /**
     * Creates a VoteStorage instance based on the provided configuration.
     *
     * @param config        the storage configuration
     * @param dataDirectory the plugin's data directory for resolving relative paths
     * @param logger        the logger for the storage implementation
     * @return a new VoteStorage instance
     * @throws StorageException if the storage type is unknown or initialization fails
     */
    public static VoteStorage create(VoteStorageConfig config, Path dataDirectory, HytaleLogger logger) throws StorageException {
        if (config == null) {
            config = VoteStorageConfig.defaults();
        }

        String type = config.type() != null ? config.type().toLowerCase() : "sqlite";

        return switch (type) {
            case "memory" -> new InMemoryVoteStorage();
            case "sqlite" -> createSQLiteStorage(config, dataDirectory, logger);
            default -> throw new StorageException("Unknown storage type: " + type + ". Supported types: memory, sqlite");
        };
    }

    /**
     * Creates and initializes a SQLite storage instance.
     */
    private static VoteStorage createSQLiteStorage(VoteStorageConfig config, Path dataDirectory, HytaleLogger logger) throws StorageException {
        String filePath = config.filePath() != null ? config.filePath() : "votes.db";
        Path databasePath = dataDirectory.resolve(filePath);

        SQLiteVoteStorage storage = new SQLiteVoteStorage(databasePath, logger);
        storage.initialize();
        return storage;
    }
}
