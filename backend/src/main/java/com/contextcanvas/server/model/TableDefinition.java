package com.contextcanvas.server.model;

import java.util.List;

/** Represents a database table with its columns and foreign keys. */
public record TableDefinition(
        String tableName,
        List<ColumnDefinition> columns,
        List<ForeignKeyDefinition> foreignKeys) {}