package com.hex1n.sofafacadedoc.store;

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
public class SavedCaseStore {
    private final StoreDatabase database;

    public SavedCaseStore(StoreDatabase database) {
        this.database = database;
    }

    public List<DocumentModel.SavedCase> listCases(String project, String branch, String service, String methodId) throws Exception {
        List<DocumentModel.SavedCase> out = new ArrayList<>();
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("SELECT id, project, branch, service, method_id, name, note, args_json, created_at, updated_at FROM cases WHERE project = ? AND branch = ? AND service = ? AND method_id = ? ORDER BY updated_at DESC")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            ps.setString(3, service);
            ps.setString(4, methodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readCase(rs));
            }
        }
        return out;
    }

    public long saveCase(DocumentModel.SavedCase item) throws Exception {
        String now = Instant.now().toString();
        if (item.id > 0) {
            try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("UPDATE cases SET branch = ?, service = ?, method_id = ?, name = ?, note = ?, args_json = ?, updated_at = ? WHERE id = ? AND project = ?")) {
                ps.setString(1, item.branch);
                ps.setString(2, item.service);
                ps.setString(3, item.methodId);
                ps.setString(4, item.name);
                ps.setString(5, item.note);
                ps.setString(6, item.argsJson);
                ps.setString(7, now);
                ps.setLong(8, item.id);
                ps.setString(9, item.project);
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalArgumentException("case not found: " + item.id);
                return item.id;
            }
        }
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO cases(project, branch, service, method_id, name, note, args_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.project);
            ps.setString(2, item.branch);
            ps.setString(3, item.service);
            ps.setString(4, item.methodId);
            ps.setString(5, item.name);
            ps.setString(6, item.note);
            ps.setString(7, item.argsJson);
            ps.setString(8, now);
            ps.setString(9, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public void deleteCase(String project, long id) throws Exception {
        try (Connection c = database.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM cases WHERE project = ? AND id = ?")) {
            ps.setString(1, project);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private DocumentModel.SavedCase readCase(ResultSet rs) throws SQLException {
        DocumentModel.SavedCase c = new DocumentModel.SavedCase();
        c.id = rs.getLong(1);
        c.project = rs.getString(2);
        c.branch = rs.getString(3);
        c.service = rs.getString(4);
        c.methodId = rs.getString(5);
        c.name = rs.getString(6);
        c.note = rs.getString(7);
        c.argsJson = rs.getString(8);
        c.createdAt = rs.getString(9);
        c.updatedAt = rs.getString(10);
        return c;
    }
}
