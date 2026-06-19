package com.contextcanvas.server.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.contextcanvas.server.model.QueryResult;

/**
 * Executes SELECT queries safely using parameterized SQL. Enforces a row limit to prevent LLM
 * context window overflow.
 */
@Service
public class QueryExecutionService {

    /** Maximum rows returned by any query to prevent LLM context overflow. */
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbc;

    public QueryExecutionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Executes a parameterized SELECT query and returns structured results. Automatically appends
     * LIMIT {maxRows} if no LIMIT clause is present to prevent LLM context window overflow from
     * large result sets.
     */
    public QueryResult execute(String sql, List<Object> params) {
        // Enforce row limit to prevent context overflow (Failure Point L)
        String limitedSql = sql;
        if (!sql.toUpperCase().contains("LIMIT")) {
            limitedSql = sql + " LIMIT " + MAX_ROWS;
        }

        Object[] paramArray = params != null ? params.toArray() : new Object[0];

        List<Map<String, Object>> rows = jdbc.query(limitedSql, paramArray, (rs) -> {
            var meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            var rowList = new ArrayList<Map<String, Object>>();

            while (rs.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rowList.add(row);
            }
            return rowList;
        });

        List<String> columns =
                rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet());

        return new QueryResult(columns, rows);
    }
}
