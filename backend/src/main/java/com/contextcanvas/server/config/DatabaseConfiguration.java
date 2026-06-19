package com.contextcanvas.server.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configures the SQLite datasource with HikariCP connection pooling.
 * <p>
 * Database path comes from the {@code CONTEXTCANVAS_DB_PATH} environment variable (default:
 * {@code ./data/contextcanvas.db}). Flyway handles schema migrations from
 * {@code classpath:db/migration}.
 */
@Configuration
public class DatabaseConfiguration {

    /**
     * Creates the HikariCP-backed SQLite datasource. Connection pool is limited to 4 connections
     * with a 5-second timeout, suitable for single-user or low-concurrency use.
     */
    @Bean
    public DataSource dataSource() {
        var config = new HikariConfig();
        var dbPath =
                System.getenv().getOrDefault("CONTEXTCANVAS_DB_PATH", "./data/contextcanvas.db");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(5000);
        config.setPoolName("ContextCanvas-SQLite");
        return new HikariDataSource(config);
    }

    /**
     * Creates a JdbcTemplate for the datasource, used by all database-access services.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
