package io.hyvexa.parkour.data;

import com.hypixel.hytale.logger.HytaleLogger;
import io.hyvexa.core.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

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

    private final List<String> messages = new ArrayList<>();
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    private long intervalMinutes = DEFAULT_INTERVAL_MINUTES;

    public GlobalMessageStore() {
    }

    public void syncLoad() {
        if (!DatabaseManager.getInstance().isInitialized()) {
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

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    intervalMinutes = clampInterval(rs.getLong("interval_minutes"));
                } else {
                    intervalMinutes = DEFAULT_INTERVAL_MINUTES;
                    insertDefaultSettings();
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load global message settings: " + e.getMessage());
            intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        }
    }

    private void insertDefaultSettings() {
        String sql = "INSERT INTO global_message_settings (id, interval_minutes) VALUES (1, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setLong(1, intervalMinutes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to insert default settings: " + e.getMessage());
        }
    }

    private void loadMessages() {
        String sql = "SELECT message FROM global_messages ORDER BY display_order, id";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String cleaned = normalizeMessage(rs.getString("message"));
                    if (!cleaned.isEmpty()) {
                        messages.add(cleaned);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load global messages: " + e.getMessage());
        }
    }

    private void saveAllToDatabase() {
        if (!DatabaseManager.getInstance().isInitialized()) return;

        // Save settings
        String settingsSql = """
            INSERT INTO global_message_settings (id, interval_minutes) VALUES (1, ?)
            ON DUPLICATE KEY UPDATE interval_minutes = VALUES(interval_minutes)
            """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(settingsSql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setLong(1, intervalMinutes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to save settings: " + e.getMessage());
        }

        // Clear and re-insert messages
        String deleteSql = "DELETE FROM global_messages";
        String insertSql = "INSERT INTO global_messages (message, display_order) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                DatabaseManager.applyQueryTimeout(deleteStmt);
                DatabaseManager.applyQueryTimeout(insertStmt);

                deleteStmt.executeUpdate();

                for (int i = 0; i < messages.size(); i++) {
                    insertStmt.setString(1, messages.get(i));
                    insertStmt.setInt(2, i);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to save messages: " + e.getMessage());
        }
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
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "UPDATE global_message_settings SET interval_minutes = ? WHERE id = 1";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setLong(1, intervalMinutes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to save settings: " + e.getMessage());
        }
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
        if (!DatabaseManager.getInstance().isInitialized()) return;

        String sql = "INSERT INTO global_messages (message, display_order) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            DatabaseManager.applyQueryTimeout(stmt);
            stmt.setString(1, message);
            stmt.setInt(2, order);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).log("Failed to add message: " + e.getMessage());
        }
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
