package io.hyvexa.core.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Minimal database access contract for stores that need pooled SQL connections.
 */
public interface ConnectionProvider {

    Connection getConnection() throws SQLException;

    boolean isInitialized();
}
