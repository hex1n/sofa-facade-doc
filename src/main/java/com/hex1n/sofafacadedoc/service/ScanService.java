package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.git.GitService;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.scanner.SourceScanner;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScanService {
    private final AppConfigLoader configLoader;
    private final GitService gitService;
    private final SourceScanner scanner;
    private final StoreService store;
    private final ScanPlanner planner;

    public ScanService(AppConfigLoader configLoader, GitService gitService, SourceScanner scanner, StoreService store, ScanPlanner planner) {
        this.configLoader = configLoader;
        this.gitService = gitService;
        this.scanner = scanner;
        this.store = store;
        this.planner = planner;
    }

    public DocumentModel.ScanResult scanBranch(String projectName, String branch) throws Exception {
        AppConfig.ProjectConfig project = configLoader.current().projects.get(projectName);
        if (project == null) throw new IllegalArgumentException("project not found: " + projectName);
        String commit = null;
        try {
            AppConfig.EffectiveBranch effective = project.effective(branch);
            GitService.Worktree wt = gitService.ensureWorktree(projectName, project.repo, branch);
            commit = wt.commit;
            StoreService.Snapshot latest = store.latestSnapshot(projectName, branch);
            ScanPlanner.ScanDecision commitDecision = planner.forCommit(wt.commit, latest);
            if (commitDecision.terminal) return commitDecision.result;
            if (latest != null) {
                List<String> changed = gitService.changedFiles(wt.path, latest.commit, wt.commit);
                ScanPlanner.ScanDecision pathDecision = planner.forChangedFiles(wt.commit, latest, changed, effective);
                if (pathDecision.terminal) return finish(projectName, branch, pathDecision);
            }
            SourceScanner.ScanOutput output = scanner.scan(projectName, branch, wt.commit, wt.path, effective);
            ScanPlanner.ScanDecision scanDecision = planner.forScanOutput(wt.commit, latest, output);
            if (scanDecision.saveSnapshot) {
                long id = store.saveSnapshot(projectName, branch, wt.commit, output.structureHash, output.document);
                return finish(projectName, branch, planner.snapshotCreated(wt.commit, output, id));
            }
            return finish(projectName, branch, scanDecision);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            try {
                store.saveReport(projectName, branch, commit, "failed", message);
            } catch (Exception ignored) {
                // Preserve the original scan failure when report persistence also fails.
            }
            throw e;
        }
    }

    public Map<String, DocumentModel.ScanResult> scanProject(String projectName) throws Exception {
        AppConfig.ProjectConfig project = configLoader.current().projects.get(projectName);
        if (project == null) throw new IllegalArgumentException("project not found: " + projectName);
        Map<String, DocumentModel.ScanResult> out = new LinkedHashMap<>();
        List<String> branches = gitService.resolveBranches(projectName, project);
        for (String branch : branches) {
            try {
                out.put(branch, scanBranch(projectName, branch));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                out.put(branch, planner.failed(message));
            }
        }
        return out;
    }

    private DocumentModel.ScanResult finish(String projectName, String branch, ScanPlanner.ScanDecision decision) throws Exception {
        if (decision.saveReport) {
            store.saveReport(projectName, branch, decision.result.commit, decision.reportStatus, decision.result.message);
        }
        return decision.result;
    }
}
