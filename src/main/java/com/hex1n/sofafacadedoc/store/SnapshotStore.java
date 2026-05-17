package com.hex1n.sofafacadedoc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class SnapshotStore {
    private final StoreDatabase database;
    private final ObjectMapper mapper;
    private final SearchIndexStore searchIndex;

    public SnapshotStore(StoreDatabase database, ObjectMapper mapper, SearchIndexStore searchIndex) {
        this.database = database;
        this.mapper = mapper;
        this.searchIndex = searchIndex;
    }

    public Long saveSnapshot(String project, String branch, String commit, String hash, DocumentModel.Document doc) throws Exception {
        String body = mapper.writeValueAsString(doc);
        String now = Instant.now().toString();
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO snapshots(project, branch, commit_hash, structure_hash, created_at, document_json) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, project);
                ps.setString(2, branch);
                ps.setString(3, commit);
                ps.setString(4, hash);
                ps.setString(5, now);
                ps.setString(6, body);
                ps.executeUpdate();
                long id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getLong(1);
                }
                searchIndex.replaceBranchIndex(c, project, branch, id, doc);
                prune(c, project, branch);
                c.commit();
                return id;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public StoreService.Snapshot latestSnapshot(String project, String branch) throws Exception {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT id, project, branch, commit_hash, structure_hash, created_at, document_json FROM snapshots WHERE project = ? AND branch = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readSnapshot(rs);
            }
        }
    }

    public List<StoreService.Snapshot> recentSnapshots(String project, String branch, int limit) throws Exception {
        List<StoreService.Snapshot> out = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT id, project, branch, commit_hash, structure_hash, created_at, document_json FROM snapshots WHERE project = ? AND branch = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readSnapshot(rs));
            }
        }
        return out;
    }

    public StoreService.Snapshot snapshot(long id) throws Exception {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT id, project, branch, commit_hash, structure_hash, created_at, document_json FROM snapshots WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readSnapshot(rs);
            }
        }
    }

    private StoreService.Snapshot readSnapshot(ResultSet rs) throws Exception {
        StoreService.Snapshot s = new StoreService.Snapshot();
        s.id = rs.getLong(1);
        s.project = rs.getString(2);
        s.branch = rs.getString(3);
        s.commit = rs.getString(4);
        s.structureHash = rs.getString(5);
        s.createdAt = rs.getString(6);
        s.document = mapper.readValue(rs.getString(7), DocumentModel.Document.class);
        return s;
    }

    private void prune(Connection c, String project, String branch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM snapshots WHERE project = ? AND branch = ? AND id NOT IN (SELECT id FROM snapshots WHERE project = ? AND branch = ? ORDER BY id DESC LIMIT 20)")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            ps.setString(3, project);
            ps.setString(4, branch);
            ps.executeUpdate();
        }
    }
}
