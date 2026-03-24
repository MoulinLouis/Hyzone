package io.hyvexa.core.db;

import java.sql.SQLException;

/**
 * A bi-consumer that accepts two parameters, potentially throwing SQLException.
 * Used with {@link DatabaseManager#executeBatch} for binding each item onto a PreparedStatement.
 */
@FunctionalInterface
public interface SQLBiConsumer<T, U> {
    void accept(T t, U u) throws SQLException;
}
