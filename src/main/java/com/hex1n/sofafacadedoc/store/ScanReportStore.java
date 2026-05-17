package com.hex1n.sofafacadedoc.store;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScanReportStore {
    private final StoreDatabase database;

    public ScanReportStore(StoreDatabase database) {
        this.database = database;
    }

    public void saveReport(String project, String branch, String commit, String status, String message) throws Exception {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO scan_reports(project, branch, commit_hash, status, message, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, project);
                    ps.setString(2, branch);
                    ps.setString(3, commit);
                    ps.setString(4, status);
                    ps.setString(5, message);
                    ps.setString(6, Instant.now().toString());
                    ps.executeUpdate();
                }
                if ("failed".equals(status)) {
                    pruneReports(c, project, branch, status);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<StoreService.ScanReport> listReports(String project, String branch, int limit) throws Exception {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        List<StoreService.ScanReport> out = new ArrayList<>();
        String sql;
        if (branch == null || branch.trim().isEmpty()) {
            sql = "SELECT id, project, branch, commit_hash, status, message, created_at FROM scan_reports WHERE project = ? ORDER BY id DESC LIMIT ?";
        } else {
            sql = "SELECT id, project, branch, commit_hash, status, message, created_at FROM scan_reports WHERE project = ? AND branch = ? ORDER BY id DESC LIMIT ?";
        }
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, project);
            if (branch == null || branch.trim().isEmpty()) {
                ps.setInt(2, safeLimit);
            } else {
                ps.setString(2, branch.trim());
                ps.setInt(3, safeLimit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readReport(rs));
            }
        }
        return out;
    }

    private StoreService.ScanReport readReport(ResultSet rs) throws SQLException {
        StoreService.ScanReport r = new StoreService.ScanReport();
        r.id = rs.getLong(1);
        r.project = rs.getString(2);
        r.branch = rs.getString(3);
        r.commit = rs.getString(4);
        r.status = rs.getString(5);
        r.message = rs.getString(6);
        r.createdAt = rs.getString(7);
        return r;
    }

    private void pruneReports(Connection c, String project, String branch, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM scan_reports WHERE project = ? AND branch = ? AND status = ? AND id NOT IN (SELECT id FROM scan_reports WHERE project = ? AND branch = ? AND status = ? ORDER BY id DESC LIMIT 20)")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            ps.setString(3, status);
            ps.setString(4, project);
            ps.setString(5, branch);
            ps.setString(6, status);
            ps.executeUpdate();
        }
    }
}
