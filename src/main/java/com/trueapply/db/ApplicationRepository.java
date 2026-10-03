package com.trueapply.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.trueapply.model.ApplicationStatus;
import com.trueapply.model.FormField;
import com.trueapply.model.JobApplication;
import com.trueapply.util.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ApplicationRepository {
    private static final TypeReference<List<FormField>> FIELDS = new TypeReference<>() {
    };

    private final Database db;
    private final JobRepository jobs;

    public ApplicationRepository(Database db, JobRepository jobs) {
        this.db = db;
        this.jobs = jobs;
    }

    public JobApplication create(long jobId) {
        JobApplication app = new JobApplication();
        app.jobId = jobId;
        app.createdAt = Instant.now();
        app.updatedAt = app.createdAt;
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO applications(job_id, status, fields_json, created_at, updated_at)
                    VALUES (?, ?, '[]', ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, jobId);
                ps.setString(2, app.status.name());
                ps.setString(3, app.createdAt.toString());
                ps.setString(4, app.updatedAt.toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) app.id = keys.getLong(1);
                }
            } catch (SQLException e) {
                throw new DataException("Failed to create application", e);
            }
        }
        app.job = jobs.find(jobId).orElse(null);
        return app;
    }

    public void save(JobApplication app) {
        app.updatedAt = Instant.now();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    UPDATE applications SET status = ?, status_message = ?, fields_json = ?, overview_json = ?,
                        job_description_html = ?, company_description_html = ?, updated_at = ?, submitted_at = ?
                    WHERE id = ?""")) {
                ps.setString(1, app.status.name());
                ps.setString(2, app.statusMessage);
                ps.setString(3, Json.write(app.fields));
                ps.setString(4, app.overviewJson);
                ps.setString(5, app.jobDescriptionHtml);
                ps.setString(6, app.companyDescriptionHtml);
                ps.setString(7, app.updatedAt.toString());
                ps.setString(8, app.submittedAt == null ? null : app.submittedAt.toString());
                ps.setLong(9, app.id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataException("Failed to save application " + app.id, e);
            }
        }
    }

    public Optional<JobApplication> find(long id) {
        List<JobApplication> list = query("SELECT * FROM applications WHERE id = ?", id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<JobApplication> findByStatus(Collection<ApplicationStatus> statuses) {
        if (statuses.isEmpty()) return List.of();
        String placeholders = statuses.stream().map(s -> "?").collect(Collectors.joining(","));
        return query("SELECT * FROM applications WHERE status IN (" + placeholders + ") ORDER BY updated_at DESC",
                statuses.stream().map(Enum::name).toArray());
    }

    public int countByStatus(ApplicationStatus status) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*) FROM applications WHERE status = ?")) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new DataException("Failed to count applications", e);
            }
        }
    }

    public void delete(long id) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM applications WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataException("Failed to delete application " + id, e);
            }
        }
    }

    private List<JobApplication> query(String sql, Object... params) {
        List<JobApplication> out = new ArrayList<>();
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            } catch (SQLException e) {
                throw new DataException("Failed to load applications", e);
            }
        }
        for (JobApplication app : out) app.job = jobs.find(app.jobId).orElse(null);
        return out;
    }

    private static JobApplication map(ResultSet rs) throws SQLException {
        JobApplication app = new JobApplication();
        app.id = rs.getLong("id");
        app.jobId = rs.getLong("job_id");
        app.status = ApplicationStatus.valueOf(rs.getString("status"));
        app.statusMessage = rs.getString("status_message");
        app.fields = new ArrayList<>(Json.read(rs.getString("fields_json"), FIELDS));
        app.overviewJson = rs.getString("overview_json");
        app.jobDescriptionHtml = rs.getString("job_description_html");
        app.companyDescriptionHtml = rs.getString("company_description_html");
        app.createdAt = Instant.parse(rs.getString("created_at"));
        app.updatedAt = Instant.parse(rs.getString("updated_at"));
        String submitted = rs.getString("submitted_at");
        app.submittedAt = submitted == null ? null : Instant.parse(submitted);
        return app;
    }
}
