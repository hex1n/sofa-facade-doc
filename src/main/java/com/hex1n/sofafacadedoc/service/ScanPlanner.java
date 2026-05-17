package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.scanner.SourceScanner;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScanPlanner {
    public ScanDecision forCommit(String commit, StoreService.Snapshot latest) {
        if (latest != null && commit != null && commit.equals(latest.commit)) {
            return terminal(commit, latest.structureHash, "unchanged", "commit unchanged", false, latest.id, latest.document, false);
        }
        return ScanDecision.sourceScanRequired();
    }

    public ScanDecision forChangedFiles(String commit, StoreService.Snapshot latest, List<String> changedFiles, AppConfig.EffectiveBranch cfg) {
        if (latest != null && !interfaceRelated(changedFiles, cfg)) {
            return terminal(commit, latest.structureHash, "skipped", "no interface-related file changed", false, latest.id, latest.document, true);
        }
        return ScanDecision.sourceScanRequired();
    }

    public ScanDecision forScanOutput(String commit, StoreService.Snapshot latest, SourceScanner.ScanOutput output) {
        if (latest != null && output.structureHash.equals(latest.structureHash)) {
            return terminal(commit, output.structureHash, "skipped", "structure hash unchanged", false, latest.id, output.document, true);
        }
        return ScanDecision.snapshotRequired(commit, output);
    }

    public ScanDecision snapshotCreated(String commit, SourceScanner.ScanOutput output, long snapshotId) {
        return terminal(commit, output.structureHash, "success", "snapshot created", true, snapshotId, output.document, true);
    }

    public DocumentModel.ScanResult failed(String message) {
        return result(null, null, "failed", message, false, 0, null);
    }

    public boolean interfaceRelated(List<String> files, AppConfig.EffectiveBranch cfg) {
        if (files == null || files.isEmpty()) return false;
        List<String> roots = new ArrayList<>();
        if (cfg != null) {
            roots.addAll(cfg.sourceRoots);
            roots.addAll(cfg.resourceRoots);
        }
        if (roots.isEmpty()) roots.add("");
        for (String file : files) {
            if (file == null) continue;
            String normalized = file.replace('\\', '/');
            if (normalized.contains("/src/test/")) continue;
            for (String root : roots) {
                String r = root == null ? "" : root.replace('\\', '/');
                boolean under = r.isEmpty() || normalized.startsWith(r + "/") || normalized.equals(r);
                if (under && interfaceRelatedName(normalized)) return true;
            }
        }
        return false;
    }

    private boolean interfaceRelatedName(String file) {
        String name = fileName(file);
        return file.endsWith(".java") || file.endsWith(".xml") || name.startsWith("application");
    }

    private String fileName(String file) {
        int i = file.lastIndexOf('/');
        return i >= 0 ? file.substring(i + 1) : file;
    }

    private ScanDecision terminal(String commit, String hash, String status, String message, boolean created, long id, DocumentModel.Document doc, boolean saveReport) {
        ScanDecision decision = new ScanDecision();
        decision.terminal = true;
        decision.saveReport = saveReport;
        decision.reportStatus = status;
        decision.result = result(commit, hash, status, message, created, id, doc);
        return decision;
    }

    private DocumentModel.ScanResult result(String commit, String hash, String status, String message, boolean created, long id, DocumentModel.Document doc) {
        DocumentModel.ScanResult r = new DocumentModel.ScanResult();
        r.commit = commit;
        r.structureHash = hash;
        r.status = status;
        r.message = message;
        r.snapshotCreated = created;
        r.snapshotId = id;
        r.document = doc;
        return r;
    }

    public static class ScanDecision {
        public boolean terminal;
        public boolean saveReport;
        public boolean saveSnapshot;
        public String reportStatus;
        public SourceScanner.ScanOutput output;
        public DocumentModel.ScanResult result;

        static ScanDecision sourceScanRequired() {
            return new ScanDecision();
        }

        static ScanDecision snapshotRequired(String commit, SourceScanner.ScanOutput output) {
            ScanDecision decision = new ScanDecision();
            decision.saveSnapshot = true;
            decision.output = output;
            return decision;
        }
    }
}
