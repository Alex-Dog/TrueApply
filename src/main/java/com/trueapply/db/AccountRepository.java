package com.trueapply.db;

import com.trueapply.model.SavedAccount;
import com.trueapply.security.Vault;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Job-site logins. Passwords are encrypted with the {@link Vault} before they touch the disk. */
public class AccountRepository {
    private final Database db;
    private final Vault vault;

    public AccountRepository(Database db, Vault vault) {
        this.db = db;
        this.vault = vault;
    }

    public SavedAccount insert(SavedAccount account) {
        if (account.createdAt == null) account.createdAt = Instant.now();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO accounts(company, site_url, username, password_enc, created_at, notes)
                    VALUES (?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, account.company);
                ps.setString(2, account.siteUrl);
                ps.setString(3, account.username);
                ps.setString(4, vault.encrypt(account.password));
                ps.setString(5, account.createdAt.toString());
                ps.setString(6, account.notes);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) account.id = keys.getLong(1);
                }
                return account;
            } catch (SQLException e) {
                throw new DataException("Failed to save account", e);
            }
        }
    }

    public List<SavedAccount> findAll() {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM accounts ORDER BY company COLLATE NOCASE, created_at DESC");
                 ResultSet rs = ps.executeQuery()) {
                List<SavedAccount> out = new ArrayList<>();
                while (rs.next()) {
                    SavedAccount a = new SavedAccount();
                    a.id = rs.getLong("id");
                    a.company = rs.getString("company");
                    a.siteUrl = rs.getString("site_url");
                    a.username = rs.getString("username");
                    a.password = vault.decrypt(rs.getString("password_enc"));
                    a.createdAt = Instant.parse(rs.getString("created_at"));
                    a.notes = rs.getString("notes");
                    out.add(a);
                }
                return out;
            } catch (SQLException e) {
                throw new DataException("Failed to load accounts", e);
            }
        }
    }

    public void delete(long id) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM accounts WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataException("Failed to delete account " + id, e);
            }
        }
    }
}
