package io.hyvexa.core.db;

import java.sql.SQLException;

/**
 * A function that accepts a parameter and returns a result, potentially throwing SQLException.
 * Used with {@link DatabaseManager#withTransaction} to avoid boilerplate transaction management.
 */
@FunctionalInterface
public interface SQLFunction<T, R> {
    R apply(T t) throws SQLException;
}
