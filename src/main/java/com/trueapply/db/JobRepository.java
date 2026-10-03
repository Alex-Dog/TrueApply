package com.trueapply.db;

import com.trueapply.model.AtsType;
import com.trueapply.model.Job;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobRepository {
    private static final String COLUMNS =
            "id, dedupe_key, source, ats, ats_board, ats_job_id, company, title, location, url, snippet, posted_at, discovered_at, status, job_type";

    private final Database db;

    public JobRepository(Database db) {
        this.db = db;
    }

    /** Inserts the job unless one with the same dedupe key exists. Returns true if inserted. */
    public boolean insertIfNew(Job job) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT OR IGNORE INTO jobs(dedupe_key, source, ats, ats_board, ats_job_id, company, title, location,
                                               url, snippet, posted_at, discovered_at, status, job_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, job.dedupeKey);
                ps.setString(2, job.source);
                ps.setString(3, job.ats == null ? null : job.ats.name());
                ps.setString(4, job.atsBoard);
                ps.setString(5, job.atsJobId);
                ps.setString(6, job.company);
                ps.setString(7, job.title);
                ps.setString(8, job.location);
                ps.setString(9, job.url);
                ps.setString(10, job.snippet);
                ps.setString(11, job.postedAt == null ? null : job.postedAt.toString());
                ps.setString(12, (job.discoveredAt == null ? Instant.now() : job.discoveredAt).toString());
                ps.setString(13, job.status.name());
                ps.setString(14, job.jobType);
                if (ps.executeUpdate() == 0) return false;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) job.id = keys.getLong(1);
                }
                return true;
            } catch (SQLException e) {
                throw new DataException("Failed to save job " + job.dedupeKey, e);
            }
        }
    }

    public Optional<Job> find(long id) {
        List<Job> jobs = query("SELECT " + COLUMNS + " FROM jobs WHERE id = ?", id);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.getFirst());
    }

    public List<Job> findByStatus(Job.JobStatus status) {
        return query("SELECT " + COLUMNS + " FROM jobs WHERE status = ? ORDER BY discovered_at DESC, id DESC", status.name());
    }

    public void updateStatus(long id, Job.JobStatus status) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement("UPDATE jobs SET status = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new DataException("Failed to update job " + id, e);
            }
        }
    }

    private List<Job> query(String sql, Object... params) {
        synchronized (db) {
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Job> out = new ArrayList<>();
                    while (rs.next()) out.add(map(rs));
                    return out;
                }
            } catch (SQLException e) {
                throw new DataException("Failed to load jobs", e);
            }
        }
    }

    private static Job map(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.id = rs.getLong("id");
        job.dedupeKey = rs.getString("dedupe_key");
        job.source = rs.getString("source");
        String ats = rs.getString("ats");
        job.ats = ats == null ? null : AtsType.valueOf(ats);
        job.atsBoard = rs.getString("ats_board");
        job.atsJobId = rs.getString("ats_job_id");
        job.company = rs.getString("company");
        job.title = rs.getString("title");
        job.location = rs.getString("location");
        job.url = rs.getString("url");
        job.snippet = rs.getString("snippet");
        String posted = rs.getString("posted_at");
        job.postedAt = posted == null ? null : Instant.parse(posted);
        job.discoveredAt = Instant.parse(rs.getString("discovered_at"));
        job.status = Job.JobStatus.valueOf(rs.getString("status"));
        job.jobType = rs.getString("job_type");
        return job;
    }
}
