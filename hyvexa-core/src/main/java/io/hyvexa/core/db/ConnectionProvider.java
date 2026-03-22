package io.hyvexa.core.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Minimal database access contract for stores that need pooled SQL connections.
 */
public interface ConnectionProvider {

    Connection getConnection() throws SQLException;

    boolean isInitialized();

    default <T> T withTransaction(SQLFunction<Connection, T> action, T defaultValue) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    default boolean withTransaction(SQLConsumer<Connection> action) {
        return withTransaction(conn -> {
            action.accept(conn);
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }
}
