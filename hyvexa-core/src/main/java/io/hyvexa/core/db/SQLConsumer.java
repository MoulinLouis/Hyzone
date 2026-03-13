package io.hyvexa.core.db;

import java.sql.SQLException;

/**
 * A consumer that accepts a parameter, potentially throwing SQLException.
 * Used with {@link DatabaseManager#withTransaction} for void transaction actions.
 */
@FunctionalInterface
public interface SQLConsumer<T> {
    void accept(T t) throws SQLException;
}
