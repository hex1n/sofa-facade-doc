package com.hex1n.sofafacadedoc.store;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Service
public class StoreService {
    private final StoreDatabase database;
    private final SnapshotStore snapshots;
    private final SearchIndexStore searchIndex;
    private final ScanReportStore scanReports;
    private final SavedCaseStore savedCases;

    public StoreService(StoreDatabase database, SnapshotStore snapshots, SearchIndexStore searchIndex, ScanReportStore scanReports, SavedCaseStore savedCases) {
        this.database = database;
        this.snapshots = snapshots;
        this.searchIndex = searchIndex;
        this.scanReports = scanReports;
        this.savedCases = savedCases;
    }

    public Connection connect() throws SQLException {
        return database.connect();
    }

    public Long saveSnapshot(String project, String branch, String commit, String hash, DocumentModel.Document doc) throws Exception {
        return snapshots.saveSnapshot(project, branch, commit, hash, doc);
    }

    public Snapshot latestSnapshot(String project, String branch) throws Exception {
        return snapshots.latestSnapshot(project, branch);
    }

    public List<Snapshot> recentSnapshots(String project, String branch, int limit) throws Exception {
        return snapshots.recentSnapshots(project, branch, limit);
    }

    public Snapshot snapshot(long id) throws Exception {
        return snapshots.snapshot(id);
    }

    public List<SearchHit> search(String project, String query) throws Exception {
        return searchIndex.search(project, query);
    }

    public void saveReport(String project, String branch, String commit, String status, String message) throws Exception {
        scanReports.saveReport(project, branch, commit, status, message);
    }

    public List<ScanReport> listReports(String project, String branch, int limit) throws Exception {
        return scanReports.listReports(project, branch, limit);
    }

    public List<DocumentModel.SavedCase> listCases(String project, String branch, String service, String methodId) throws Exception {
        return savedCases.listCases(project, branch, service, methodId);
    }

    public long saveCase(DocumentModel.SavedCase item) throws Exception {
        return savedCases.saveCase(item);
    }

    public void deleteCase(String project, long id) throws Exception {
        savedCases.deleteCase(project, id);
    }

    public static class Snapshot {
        public long id;
        public String project;
        public String branch;
        public String commit;
        public String structureHash;
        public String createdAt;
        public DocumentModel.Document document;
    }

    public static class SearchHit {
        public String project;
        public String branch;
        public long snapshotId;
        public String service;
        public String methodId;
        public String method;
        public String snippet;
    }

    public static class ScanReport {
        public long id;
        public String project;
        public String branch;
        public String commit;
        public String status;
        public String message;
        public String createdAt;
    }
}
