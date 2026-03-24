package io.hyvexa.core.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Binds parameters onto a {@link PreparedStatement} before execution.
 */
@FunctionalInterface
public interface ParamBinder {
    void bind(PreparedStatement stmt) throws SQLException;
}
