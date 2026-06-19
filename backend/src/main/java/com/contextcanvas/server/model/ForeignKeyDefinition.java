package com.contextcanvas.server.model;

/** Represents a foreign key relationship between tables. */
public record ForeignKeyDefinition(
        String fromColumn,
        String referencedTable,
        String referencedColumn) {}