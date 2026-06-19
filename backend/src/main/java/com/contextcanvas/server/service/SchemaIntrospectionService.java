package com.contextcanvas.server.service;

import com.contextcanvas.server.model.ColumnDefinition;
import com.contextcanvas.server.model.ForeignKeyDefinition;
import com.contextcanvas.server.model.TableDefinition;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Introspects SQLite database schema (tables, columns, foreign keys).
 * Called once at LLM session startup via the introspect_schema() tool.
 */
@Service
public class SchemaIntrospectionService {

    private final JdbcTemplate jdbc;

    public SchemaIntrospectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all user tables with their columns and foreign key relationships.
     * Excludes Flyway migration tables and internal tables.
     */
    public List<TableDefinition> introspect() {
        List<String> userTables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' "
                        + "AND name NOT LIKE 'flyway_%' "
                        + "AND name NOT IN ('user_preferences')",
                String.class);

        return userTables.stream()
                .map(this::describeTable)
                .toList();
    }

    private TableDefinition describeTable(String tableName) {
        List<ColumnDefinition> columns = jdbc.query(
                "PRAGMA table_info(%s)".formatted(tableName),
                (rs, row) -> new ColumnDefinition(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getBoolean("notnull"),
                        rs.getString("dflt_value"),
                        rs.getBoolean("pk")));

        List<ForeignKeyDefinition> foreignKeys = jdbc.query(
                "PRAGMA foreign_key_list(%s)".formatted(tableName),
                (rs, row) -> new ForeignKeyDefinition(
                        rs.getString("from"),
                        rs.getString("table"),
                        rs.getString("to")));

        return new TableDefinition(tableName, columns, foreignKeys);
    }
}