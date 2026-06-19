package com.contextcanvas.server.model;

import java.util.List;
import java.util.Map;

/** Result of a SELECT query: column names and rows of data. */
public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows) {}