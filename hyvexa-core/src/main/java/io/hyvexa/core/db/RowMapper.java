package io.hyvexa.core.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a single {@link ResultSet} row to a domain object.
 * The cursor is already positioned on the current row when {@link #map} is called.
 */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
