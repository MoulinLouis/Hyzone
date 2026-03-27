package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.ConnectionProvider;
import io.hyvexa.core.db.DatabaseManager;
import io.hyvexa.core.db.RowMapper;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** MySQL-backed storage for global announcement messages and cadence. */
public class GlobalMessageStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final long DEFAULT_INTERVAL_MINUTES = 10L;
    private static final long MIN_INTERVAL_MINUTES = 1L;
    private static final long MAX_INTERVAL_MINUTES = 1440L;
    private static final int MAX_MESSAGE_LENGTH = 240;
    private static final List<String> DEFAULT_MESSAGES = List.of(
            "Want to contribute? Join our Discord ({link}).",
            "Need help or want to report a bug? Discord ({link}).",
            "Any suggestion? Tell us on Discord! ({link})."
    );

    private final ConnectionProvider db;
    private final List<String> messages = new ArrayList<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private long intervalMinutes = DEFAULT_INTERVAL_MINUTES;

    public GlobalMessageStore(ConnectionProvider db) {
        this.db = db;
    }

    public void syncLoad() {
        if (!this.db.isInitialized()) {
            LOGGER.atWarning().log("Database not initialized, using defaults for GlobalMessageStore");
            applyDefaults();
            return;
        }

        fileLock.writeLock().lock();
        try {
            messages.clear();
            loadSettings();
            loadMessages();

            if (messages.isEmpty()) {
                applyDefaults();
                saveAllToDatabase();
            }

            LOGGER.atInfo().log("GlobalMessageStore loaded " + messages.size() + " messages from database");
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void loadSettings() {
        String sql = "SELECT interval_minutes FROM global_message_settings WHERE id = 1";

        // Use -1 as sentinel to detect "no row found"
        long loaded = DatabaseManager.queryOne(this.db, sql,
                rs -> clampInterval(rs.getLong("interval_minutes")), -1L);
        if (loaded == -1L) {
            intervalMinutes = DEFAULT_INTERVAL_MINUTES;
            insertDefaultSettings();
        } else {
            intervalMinutes = loaded;
        }
    }

    private void insertDefaultSettings() {
        String sql = "INSERT INTO global_message_settings (id, interval_minutes) VALUES (1, ?)";
        DatabaseManager.execute(this.db, sql, stmt -> stmt.setLong(1, intervalMinutes));
    }

    private void loadMessages() {
        String sql = "SELECT message FROM global_messages ORDER BY display_order, id";

        RowMapper<String> mapper = rs -> normalizeMessage(rs.getString("message"));
        List<String> loaded = DatabaseManager.queryList(this.db, sql, mapper);
        for (String msg : loaded) {
            if (!msg.isEmpty()) {
                messages.add(msg);
            }
        }
    }

    private void saveAllToDatabase() {
        if (!this.db.isInitialized()) return;

        // Save settings
        String settingsSql = """
            INSERT INTO global_message_settings (id, interval_minutes) VALUES (1, ?)
            ON DUPLICATE KEY UPDATE interval_minutes = VALUES(interval_minutes)
            """;
        DatabaseManager.execute(this.db, settingsSql, stmt -> stmt.setLong(1, intervalMinutes));

        // Clear and re-insert messages
        String deleteSql = "DELETE FROM global_messages";
        String insertSql = "INSERT INTO global_messages (message, display_order) VALUES (?, ?)";

        this.db.withTransaction(conn -> {
            try (PreparedStatement deleteStmt = DatabaseManager.prepare(conn, deleteSql);
                 PreparedStatement insertStmt = DatabaseManager.prepare(conn, insertSql)) {
                deleteStmt.executeUpdate();

                for (int i = 0; i < messages.size(); i++) {
                    insertStmt.setString(1, messages.get(i));
                    insertStmt.setInt(2, i);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        });
    }

    public List<String> getMessages() {
        fileLock.readLock().lock();
        try {
            return List.copyOf(messages);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public long getIntervalMinutes() {
        fileLock.readLock().lock();
        try {
            return intervalMinutes;
        } finally {
            fileLock.readLock().unlock();
        }
    }

    public void setIntervalMinutes(long minutes) {
        long clamped = clampInterval(minutes);
        fileLock.writeLock().lock();
        try {
            intervalMinutes = clamped;
        } finally {
            fileLock.writeLock().unlock();
        }
        saveSettings();
    }

    private void saveSettings() {
        if (!this.db.isInitialized()) return;

        String sql = "UPDATE global_message_settings SET interval_minutes = ? WHERE id = 1";
        DatabaseManager.execute(this.db, sql, stmt -> stmt.setLong(1, intervalMinutes));
    }

    public boolean addMessage(String message) {
        String cleaned = normalizeMessage(message);
        if (cleaned.isEmpty()) {
            return false;
        }

        int newOrder;
        fileLock.writeLock().lock();
        try {
            messages.add(cleaned);
            newOrder = messages.size() - 1;
        } finally {
            fileLock.writeLock().unlock();
        }

        addMessageToDatabase(cleaned, newOrder);
        return true;
    }

    private void addMessageToDatabase(String message, int order) {
        if (!this.db.isInitialized()) return;

        String sql = "INSERT INTO global_messages (message, display_order) VALUES (?, ?)";
        DatabaseManager.execute(this.db, sql, stmt -> {
            stmt.setString(1, message);
            stmt.setInt(2, order);
        });
    }

    public boolean removeMessage(int index) {
        boolean removed = false;
        fileLock.writeLock().lock();
        try {
            if (index >= 0 && index < messages.size()) {
                messages.remove(index);
                removed = true;
            }
        } finally {
            fileLock.writeLock().unlock();
        }

        if (removed) {
            saveAllToDatabase();
        }
        return removed;
    }

    private void applyDefaults() {
        messages.clear();
        messages.addAll(DEFAULT_MESSAGES);
        intervalMinutes = DEFAULT_INTERVAL_MINUTES;
    }

    private static long clampInterval(long minutes) {
        if (minutes < MIN_INTERVAL_MINUTES) {
            return MIN_INTERVAL_MINUTES;
        }
        if (minutes > MAX_INTERVAL_MINUTES) {
            return MAX_INTERVAL_MINUTES;
        }
        return minutes;
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_MESSAGE_LENGTH);
        }
        return trimmed;
    }
}
