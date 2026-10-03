package com.trueapply.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Single SQLite connection shared by the repositories. Repositories synchronize on this
 * object, which is plenty for a desktop app.
 */
public final class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path file) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
            }
            migrate();
        } catch (SQLException e) {
            throw new DataException("Could not open database " + file, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key   TEXT PRIMARY KEY,
                        value TEXT
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS jobs (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        dedupe_key    TEXT NOT NULL UNIQUE,
                        source        TEXT NOT NULL,
                        ats           TEXT,
                        ats_board     TEXT,
                        ats_job_id    TEXT,
                        company       TEXT,
                        title         TEXT,
                        location      TEXT,
                        url           TEXT,
                        snippet       TEXT,
                        posted_at     TEXT,
                        discovered_at TEXT NOT NULL,
                        status        TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS applications (
                        id                       INTEGER PRIMARY KEY AUTOINCREMENT,
                        job_id                   INTEGER NOT NULL REFERENCES jobs(id),
                        status                   TEXT NOT NULL,
                        status_message           TEXT,
                        fields_json              TEXT NOT NULL,
                        overview_json            TEXT,
                        job_description_html     TEXT,
                        company_description_html TEXT,
                        created_at               TEXT NOT NULL,
                        updated_at               TEXT NOT NULL,
                        submitted_at             TEXT
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        company      TEXT NOT NULL,
                        site_url     TEXT,
                        username     TEXT NOT NULL,
                        password_enc TEXT NOT NULL,
                        created_at   TEXT NOT NULL,
                        notes        TEXT
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_apps_status ON applications(status)");
        }
        addColumnIfMissing("jobs", "job_type", "TEXT");
    }

    /** Lightweight schema evolution for databases created by older versions. */
    private void addColumnIfMissing(String table, String column, String type) throws SQLException {
        try (Statement st = connection.createStatement();
             java.sql.ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) return;
            }
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // shutting down anyway
        }
    }
}
