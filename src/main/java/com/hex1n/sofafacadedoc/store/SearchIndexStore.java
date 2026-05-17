package com.hex1n.sofafacadedoc.store;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SearchIndexStore {
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{L}\\p{N}_$]+");
    private final StoreDatabase database;

    public SearchIndexStore(StoreDatabase database) {
        this.database = database;
    }

    public void replaceBranchIndex(Connection c, String project, String branch, long snapshotId, DocumentModel.Document doc) throws Exception {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM search_index WHERE project = ? AND branch = ?")) {
            del.setString(1, project);
            del.setString(2, branch);
            del.executeUpdate();
        }
        for (DocumentModel.ServiceDoc service : doc.services) {
            for (DocumentModel.MethodDoc method : service.methods) {
                String content = service.fqn + " " + safe(service.comment) + " " + searchableName(method.name) + " " + safe(method.comment) + " " + safe(method.returnType);
                for (DocumentModel.ParamDoc p : method.params) {
                    content += " " + searchableName(p.name) + " " + p.javaType + " " + safe(p.comment) + " " + flatten(p.tree);
                }
                content += " " + flatten(method.returnTree);
                try (PreparedStatement ins = c.prepareStatement("INSERT INTO search_index(project, branch, snapshot_id, service, method_id, method, content) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, project);
                    ins.setString(2, branch);
                    ins.setLong(3, snapshotId);
                    ins.setString(4, service.fqn);
                    ins.setString(5, method.id);
                    ins.setString(6, method.name);
                    ins.setString(7, content);
                    ins.executeUpdate();
                }
            }
        }
    }

    public List<StoreService.SearchHit> search(String project, String query) throws Exception {
        List<StoreService.SearchHit> out = new ArrayList<>();
        String ftsQuery = toFtsQuery(query);
        String likeQuery = toLikeQuery(query);
        if (ftsQuery.isEmpty() && likeQuery.isEmpty()) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        try (Connection c = database.connect()) {
            if (!ftsQuery.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("SELECT project, branch, snapshot_id, service, method_id, method, snippet(search_index, 6, '<mark>', '</mark>', ' ... ', 16) FROM search_index WHERE project = ? AND search_index MATCH ? LIMIT 50")) {
                    ps.setString(1, project);
                    ps.setString(2, ftsQuery);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            addSearchHit(out, seen, rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                        }
                    }
                }
            }
            if (!likeQuery.isEmpty() && out.size() < 50) {
                try (PreparedStatement ps = c.prepareStatement("SELECT project, branch, snapshot_id, service, method_id, method FROM search_index WHERE project = ? AND (lower(method) LIKE ? OR lower(service) LIKE ?) LIMIT 50")) {
                    ps.setString(1, project);
                    ps.setString(2, likeQuery);
                    ps.setString(3, likeQuery);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && out.size() < 50) {
                            String service = rs.getString(4);
                            String method = rs.getString(6);
                            addSearchHit(out, seen, rs.getString(1), rs.getString(2), rs.getLong(3), service, rs.getString(5), method, service + " " + method);
                        }
                    }
                }
            }
        }
        return out;
    }

    private void addSearchHit(List<StoreService.SearchHit> out, Set<String> seen, String project, String branch, long snapshotId, String service, String methodId, String method, String snippet) {
        String key = branch + "\n" + snapshotId + "\n" + methodId;
        if (!seen.add(key)) return;
        StoreService.SearchHit h = new StoreService.SearchHit();
        h.project = project;
        h.branch = branch;
        h.snapshotId = snapshotId;
        h.service = service;
        h.methodId = methodId;
        h.method = method;
        h.snippet = snippet;
        out.add(h);
    }

    private String flatten(DocumentModel.FieldNode node) {
        if (node == null) return "";
        StringBuilder b = new StringBuilder();
        b.append(" ").append(safe(node.path))
                .append(" ").append(searchableName(node.name))
                .append(" ").append(safe(node.jsonName))
                .append(" ").append(safe(node.javaType))
                .append(" ").append(safe(node.comment));
        if (node.constraints != null) {
            for (String constraint : node.constraints) b.append(" ").append(safe(constraint));
        }
        for (DocumentModel.FieldNode child : node.children) b.append(flatten(child));
        return b.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String searchableName(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String spaced = value
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
        return value + " " + spaced;
    }

    private String toFtsQuery(String query) {
        if (query == null) return "";
        Matcher matcher = SEARCH_TOKEN.matcher(query);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find() && count < 12) {
            if (out.length() > 0) out.append(' ');
            out.append('"').append(matcher.group()).append("\"*");
            count++;
        }
        return out.toString();
    }

    private String toLikeQuery(String query) {
        if (query == null) return "";
        Matcher matcher = SEARCH_TOKEN.matcher(query);
        StringBuilder out = new StringBuilder("%");
        int count = 0;
        while (matcher.find() && count < 12) {
            out.append(matcher.group().toLowerCase()).append('%');
            count++;
        }
        return count == 0 ? "" : out.toString();
    }
}
