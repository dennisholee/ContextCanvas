package com.contextcanvas.server.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * CRUD service for the {@code user_preferences} table.
 * <p>
 * Enables cross-session preference learning (PRD FR-3.3). Preferences are stored as key-value pairs
 * with a timestamp, allowing the LLM to remember user preferences (chart type, date range, etc.)
 * across sessions.
 */
@Service
public class PreferenceService {

    private final JdbcTemplate jdbc;

    public PreferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns all stored preferences as key-value pairs. Called at session start by the
     * {@code get_preferences} tool so the LLM can recall user preferences from previous sessions.
     */
    public Map<String, String> getAll() {
        var result = new HashMap<String, String>();
        jdbc.query("SELECT key, value FROM user_preferences", (rs) -> {
            result.put(rs.getString("key"), rs.getString("value"));
        });
        return result;
    }

    /**
     * Stores or updates a single preference. Uses INSERT OR REPLACE so calling this multiple times
     * with the same key will update the existing value.
     */
    public void set(String key, String value) {
        jdbc.update(
                "INSERT OR REPLACE INTO user_preferences (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                key, value);
    }

    /** Removes a specific preference by key. */
    public void remove(String key) {
        jdbc.update("DELETE FROM user_preferences WHERE key = ?", key);
    }

    /** Clears all stored preferences. */
    public void clear() {
        jdbc.update("DELETE FROM user_preferences");
    }
}
