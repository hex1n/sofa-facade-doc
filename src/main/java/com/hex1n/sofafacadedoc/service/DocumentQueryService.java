package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.diff.BranchDiffAnnotator;
import com.hex1n.sofafacadedoc.diff.DiffService;
import com.hex1n.sofafacadedoc.doc.MarkdownRenderer;
import com.hex1n.sofafacadedoc.invoke.InvokeService;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentQueryService {
    private final AppConfigLoader configLoader;
    private final StoreService store;
    private final MarkdownRenderer markdown;
    private final DiffService diffService;
    private final BranchDiffAnnotator branchDiffAnnotator;
    private final InvokeService invokeService;

    public DocumentQueryService(AppConfigLoader configLoader, StoreService store, MarkdownRenderer markdown, DiffService diffService, BranchDiffAnnotator branchDiffAnnotator, InvokeService invokeService) {
        this.configLoader = configLoader;
        this.store = store;
        this.markdown = markdown;
        this.diffService = diffService;
        this.branchDiffAnnotator = branchDiffAnnotator;
        this.invokeService = invokeService;
    }

    public List<DocumentModel.ServiceDoc> services(String project, String branch) throws Exception {
        return services(project, branch, null);
    }

    public List<DocumentModel.ServiceDoc> services(String project, String branch, String base) throws Exception {
        DocumentModel.Document current = latest(project, branch).document;
        String trimmed = base == null ? "" : base.trim();
        if (trimmed.isEmpty() || trimmed.equals(branch)) return current.services;
        StoreService.Snapshot baseSnap = store.latestSnapshot(project, trimmed);
        if (baseSnap == null) return current.services;
        return branchDiffAnnotator.annotate(current, baseSnap.document);
    }

    public Map<String, Object> method(String project, String branch, String methodId) throws Exception {
        return method(project, branch, methodId, null);
    }

    public Map<String, Object> method(String project, String branch, String methodId, String base) throws Exception {
        StoreService.Snapshot snap = latest(project, branch);
        DocumentModel.Document document = documentForDetail(project, branch, snap.document, base);
        FoundMethod found = find(document, methodId);
        AppConfig.ProjectConfig cfg = projectConfig(project);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("snapshot", snap);
        out.put("service", found.service);
        out.put("method", found.method);
        out.put("runtime", runtimeView(cfg.effective(branch)));
        String trimmedBase = base == null ? "" : base.trim();
        if (!trimmedBase.isEmpty() && !trimmedBase.equals(branch)) out.put("baseBranch", trimmedBase);
        return out;
    }

    public String methodMarkdown(String project, String branch, String methodId) throws Exception {
        return methodMarkdown(project, branch, methodId, null);
    }

    public String methodMarkdown(String project, String branch, String methodId, String base) throws Exception {
        StoreService.Snapshot snap = latest(project, branch);
        DocumentModel.Document document = documentForDetail(project, branch, snap.document, base);
        FoundMethod found = find(document, methodId);
        return markdown.render(document, found.service, found.method);
    }

    public List<DocumentModel.DiffChange> diff(String project, String branch, String base) throws Exception {
        AppConfig.ProjectConfig cfg = projectConfig(project);
        StoreService.Snapshot right = latest(project, branch);
        StoreService.Snapshot left;
        if (base != null && !base.trim().isEmpty()) {
            left = latest(project, base.trim());
        } else if (branch.equals(cfg.baselineBranch)) {
            List<StoreService.Snapshot> snaps = store.recentSnapshots(project, branch, 2);
            if (snaps.size() < 2) return new ArrayList<>();
            left = snaps.get(1);
        } else {
            left = latest(project, cfg.baselineBranch);
        }
        return diffService.compare(left.document, right.document);
    }

    public InvokeService.ProbeResult probe(String project, String branch) {
        AppConfig.ProjectConfig cfg = projectConfig(project);
        return invokeService.probe(cfg.effective(branch).directUrl);
    }

    public InvokeService.ValidateResult validate(String project, String branch, String methodId, Map<String, Object> body) throws Exception {
        StoreService.Snapshot snap = latest(project, branch);
        FoundMethod found = find(snap.document, methodId);
        return invokeService.validate(found.method, body);
    }

    public InvokeService.InvokeResult invoke(String project, String branch, String methodId, Integer publishIndex, Map<String, Object> body) throws Exception {
        StoreService.Snapshot snap = latest(project, branch);
        FoundMethod found = find(snap.document, methodId);
        AppConfig.ProjectConfig cfg = projectConfig(project);
        AppConfig.EffectiveBranch runtime = runtimeForPublish(cfg.effective(branch), found.service, publishIndex);
        return invokeService.invoke(found.service.fqn, found.method, runtime, body);
    }

    private StoreService.Snapshot latest(String project, String branch) throws Exception {
        StoreService.Snapshot snap = store.latestSnapshot(project, branch);
        if (snap == null) throw new NotFound("no snapshot for " + project + "/" + branch + "; scan first");
        return snap;
    }

    private DocumentModel.Document documentForDetail(String project, String branch, DocumentModel.Document current, String base) throws Exception {
        String trimmed = base == null ? "" : base.trim();
        if (trimmed.isEmpty() || trimmed.equals(branch)) return current;
        StoreService.Snapshot baseSnap = store.latestSnapshot(project, trimmed);
        if (baseSnap == null) return current;
        return documentWithServices(current, branchDiffAnnotator.annotate(current, baseSnap.document));
    }

    private DocumentModel.Document documentWithServices(DocumentModel.Document current, List<DocumentModel.ServiceDoc> services) {
        DocumentModel.Document out = current;
        out.services = services;
        return out;
    }

    private AppConfig.ProjectConfig projectConfig(String project) {
        AppConfig.ProjectConfig cfg = configLoader.current().projects.get(project);
        if (cfg == null) throw new NotFound("project not found: " + project);
        return cfg;
    }

    private FoundMethod find(DocumentModel.Document doc, String methodId) {
        for (DocumentModel.ServiceDoc service : doc.services) {
            for (DocumentModel.MethodDoc method : service.methods) {
                if (method.id.equals(methodId)) {
                    FoundMethod found = new FoundMethod();
                    found.service = service;
                    found.method = method;
                    return found;
                }
            }
        }
        throw new NotFound("method not found: " + methodId);
    }

    private AppConfig.EffectiveBranch runtimeForPublish(AppConfig.EffectiveBranch base, DocumentModel.ServiceDoc service, Integer publishIndex) {
        AppConfig.EffectiveBranch out = copyRuntime(base);
        DocumentModel.PublishRecord record = selectedPublishRecord(service, publishIndex);
        if (record == null) return out;
        if (notBlank(record.uniqueId)) out.uniqueId = record.uniqueId;
        if (notBlank(record.version)) out.version = record.version;
        return out;
    }

    private Map<String, Object> runtimeView(AppConfig.EffectiveBranch runtime) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("directUrl", runtime.directUrl);
        out.put("uniqueId", runtime.uniqueId);
        out.put("version", runtime.version);
        out.put("targetAppName", runtime.targetAppName);
        return out;
    }

    private DocumentModel.PublishRecord selectedPublishRecord(DocumentModel.ServiceDoc service, Integer publishIndex) {
        if (service.publishRecords == null || service.publishRecords.isEmpty()) return null;
        if (publishIndex != null) {
            if (publishIndex < 0 || publishIndex >= service.publishRecords.size()) {
                throw new IllegalArgumentException("publish index out of range: " + publishIndex);
            }
            return service.publishRecords.get(publishIndex);
        }
        for (DocumentModel.PublishRecord record : service.publishRecords) {
            if (!record.incomplete && (!notBlank(record.binding) || "bolt".equalsIgnoreCase(record.binding))) {
                return record;
            }
        }
        return service.publishRecords.get(0);
    }

    private AppConfig.EffectiveBranch copyRuntime(AppConfig.EffectiveBranch base) {
        AppConfig.EffectiveBranch out = new AppConfig.EffectiveBranch();
        out.directUrl = base.directUrl;
        out.uniqueId = base.uniqueId;
        out.version = base.version;
        out.targetAppName = base.targetAppName;
        out.springProfiles = new ArrayList<>(base.springProfiles);
        out.sourceRoots = new ArrayList<>(base.sourceRoots);
        out.resourceRoots = new ArrayList<>(base.resourceRoots);
        out.facadePackages = new ArrayList<>(base.facadePackages);
        return out;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class FoundMethod {
        DocumentModel.ServiceDoc service;
        DocumentModel.MethodDoc method;
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}
