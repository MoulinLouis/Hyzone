package org.hyvote.plugins.votifier;

/**
 * Configuration for vote storage backend.
 *
 * <p>Supports multiple storage types:</p>
 * <ul>
 *   <li>{@code memory} - In-memory storage (clears on restart)</li>
 *   <li>{@code sqlite} - SQLite file-based storage (persistent)</li>
 * </ul>
 *
 * @param type                 The storage type: "memory" or "sqlite" (default "sqlite")
 * @param filePath             Path to the database file, relative to plugin data directory (default "votes.db")
 * @param cleanupIntervalHours How often (in hours) to run cleanup of expired vote records (default 6)
 */
public record VoteStorageConfig(
        String type,
        String filePath,
        Integer cleanupIntervalHours
) {

    /**
     * Returns a VoteStorageConfig with default values.
     *
     * @return default storage configuration (SQLite with votes.db)
     */
    public static VoteStorageConfig defaults() {
        return new VoteStorageConfig(
                "sqlite",
                "votes.db",
                6
        );
    }

    /**
     * Merges this config with defaults, using default values for any null fields.
     *
     * @param defaults the default configuration to fall back to for null fields
     * @return a new VoteStorageConfig with null fields replaced by defaults
     */
    public VoteStorageConfig merge(VoteStorageConfig defaults) {
        return new VoteStorageConfig(
                this.type != null ? this.type : defaults.type(),
                this.filePath != null ? this.filePath : defaults.filePath(),
                this.cleanupIntervalHours != null ? this.cleanupIntervalHours : defaults.cleanupIntervalHours()
        );
    }
}
