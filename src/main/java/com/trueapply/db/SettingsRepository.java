package com.trueapply.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Key/value store backing {@link com.trueapply.settings.AppSettings} and the profile document. */
public class SettingsRepository {
    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public Optional<String> get(String key) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("SELECT value FROM settings WHERE key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new DataException("Failed to read setting " + key, e);
            }
        }
    }

    public void put(String key, String value) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "INSERT INTO settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataException("Failed to write setting " + key, e);
            }
        }
    }
}
