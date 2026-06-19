package com.contextcanvas.server.model;

/** Represents a single column in a database table. */
public record ColumnDefinition(
        String name,
        String type,
        boolean required,
        String defaultValue,
        boolean primaryKey) {}