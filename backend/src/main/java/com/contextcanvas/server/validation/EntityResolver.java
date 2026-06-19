package com.contextcanvas.server.validation;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves entity names using Exact → LIKE → Fuzzy matching chain.
 * Supports self-healing query recovery (PRD Section 6.2).
 */
@Component
public class EntityResolver {

    private final JdbcTemplate jdbc;

    public EntityResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Attempts to resolve an entity name across all tables.
     * Returns a list of matches with their table, column, id, and display name.
     */
    public List<EntityMatch> resolve(String name, String table) {
        var results = new java.util.ArrayList<EntityMatch>();

        // Step 1: Exact match
        var exactSql = "SELECT id, company_name AS display FROM %s WHERE company_name = ?"
                .formatted(table);
        results.addAll(queryMatches(exactSql, List.of(name), table, "exact"));

        if (!results.isEmpty()) {
            return results;
        }

        // Step 2: LIKE match
        var likeSql = "SELECT id, company_name AS display FROM %s WHERE company_name LIKE ?"
                .formatted(table);
        results.addAll(queryMatches(likeSql, List.of("%" + name + "%"), table, "like"));

        if (!results.isEmpty()) {
            return results;
        }

        // Step 3: Try contact name if clients table
        if (table.equals("clients")) {
            var contactSql = """
                    SELECT c.id, c.company_name AS display
                    FROM clients c
                    JOIN contacts ct ON ct.client_id = c.id
                    WHERE ct.full_name LIKE ?
                    """;
            results.addAll(queryMatches(contactSql, List.of("%" + name + "%"), table, "contact"));
        }

        return results;
    }

    private List<EntityMatch> queryMatches(String sql, List<Object> params, String table, String matchType) {
        return jdbc.query(sql, params.toArray(), (rs, row) -> {
            var id = rs.getLong("id");
            var display = rs.getString("display");
            return new EntityMatch(id, display, table, matchType);
        });
    }

    /** A single entity match result. */
    public record EntityMatch(
            long id,
            String displayName,
            String table,
            String matchType) {}
}