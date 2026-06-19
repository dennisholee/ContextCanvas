package com.contextcanvas.server.validation;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates SQL queries for safety. Rejects DDL operations and ensures parameterized queries are
 * used correctly.
 */
@Component
public class SqlValidator {

        // Supports plain SELECT and WITH (CTEs): WITH ... AS (SELECT ...) SELECT ...
        // Pattern: optional WITH clause followed by SELECT
        private static final Pattern SELECT_PATTERN =
                        Pattern.compile("^\\s*(WITH\\s+.*?\\bAS\\s*\\(.*?\\)\\s*)?SELECT\\s+.*",
                                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        private static final Pattern INSERT_PATTERN = Pattern.compile("^\\s*INSERT\\s+.*",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        private static final Pattern UPDATE_PATTERN = Pattern.compile("^\\s*UPDATE\\s+.*",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        private static final Pattern DELETE_PATTERN = Pattern.compile("^\\s*DELETE\\s+.*",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        private static final Pattern DDL_PATTERN = Pattern.compile(
                        "\\b(CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE|TRUNCATE|CREATE\\s+INDEX|DROP\\s+INDEX)\\b",
                        Pattern.CASE_INSENSITIVE);

        /**
         * Asserts the SQL is a SELECT statement (for the query tool). Rejects DDL and non-SELECT
         * statements.
         */
        public void assertSelectOnly(String sql) {
                if (!SELECT_PATTERN.matcher(sql).matches()) {
                        throw new SqlValidationException(
                                        "Only SELECT statements are permitted via query()");
                }
                if (DDL_PATTERN.matcher(sql).find()) {
                        throw new SqlValidationException(
                                        "DDL operations (CREATE/ALTER/DROP) are not permitted");
                }
        }

        /**
         * Asserts the SQL is a DML statement (INSERT/UPDATE/DELETE) for the execute tool.
         */
        public void assertDmlOnly(String sql) {
                boolean isDml = INSERT_PATTERN.matcher(sql).matches()
                                || UPDATE_PATTERN.matcher(sql).matches()
                                || DELETE_PATTERN.matcher(sql).matches();
                if (!isDml) {
                        throw new SqlValidationException(
                                        "Only INSERT, UPDATE, or DELETE statements are permitted via execute()");
                }
                if (DDL_PATTERN.matcher(sql).find()) {
                        throw new SqlValidationException(
                                        "DDL operations (CREATE/ALTER/DROP) are not permitted");
                }
        }

        /**
         * Asserts parameterized SQL matches the provided parameters.
         */
        public void assertParameterized(String sql, List<Object> params) {
                long placeholderCount = sql.chars().filter(ch -> ch == '?').count();
                boolean hasParams = params != null && !params.isEmpty();

                if (placeholderCount > 0 && !hasParams) {
                        throw new SqlValidationException(
                                        "SQL contains ? placeholders but no parameters provided");
                }
                if (hasParams && placeholderCount != params.size()) {
                        throw new SqlValidationException(
                                        "Parameter count mismatch: %d placeholders, %d parameters"
                                                        .formatted(placeholderCount,
                                                                        params.size()));
                }
        }
}
