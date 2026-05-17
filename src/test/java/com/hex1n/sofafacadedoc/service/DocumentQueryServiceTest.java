package com.hex1n.sofafacadedoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.diff.BranchDiffAnnotator;
import com.hex1n.sofafacadedoc.diff.DiffService;
import com.hex1n.sofafacadedoc.doc.MarkdownRenderer;
import com.hex1n.sofafacadedoc.invoke.InvokeService;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentQueryServiceTest {
    @Test
    void invokeUsesSelectedPublishedRuntimeRecord() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        InvokeService invoke = mock(InvokeService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), mock(DiffService.class), new BranchDiffAnnotator(), invoke);
        AppConfig cfg = config();
        DocumentModel.MethodDoc method = method("submitApply-1", "submitApply");
        DocumentModel.ServiceDoc service = service("com.company.loan.facade.LoanApplyFacade", method);
        service.publishRecords.add(publish("dubbo", "ignored", "ignored-v"));
        service.publishRecords.add(publish("bolt", "xml-test", "feature-v1"));
        StoreService.Snapshot snap = snapshot("loan", "feature/apply-flow", document(service));
        InvokeService.InvokeResult expected = new InvokeService.InvokeResult();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", Collections.emptyMap());

        when(loader.current()).thenReturn(cfg);
        when(store.latestSnapshot("loan", "feature/apply-flow")).thenReturn(snap);
        when(invoke.invoke(eq(service.fqn), same(method), any(AppConfig.EffectiveBranch.class), same(body))).thenReturn(expected);

        InvokeService.InvokeResult result = query.invoke("loan", "feature/apply-flow", method.id, null, body);

        assertSame(expected, result);
        ArgumentCaptor<AppConfig.EffectiveBranch> runtime = ArgumentCaptor.forClass(AppConfig.EffectiveBranch.class);
        verify(invoke).invoke(eq(service.fqn), same(method), runtime.capture(), same(body));
        assertEquals("bolt://127.0.0.1:12201", runtime.getValue().directUrl);
        assertEquals("xml-test", runtime.getValue().uniqueId);
        assertEquals("feature-v1", runtime.getValue().version);
        assertEquals("apply-flow-app", runtime.getValue().targetAppName);
    }

    @Test
    void diffUsesPreviousSnapshotWhenComparingBaselineBranch() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DiffService diff = mock(DiffService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), diff, new BranchDiffAnnotator(), mock(InvokeService.class));
        AppConfig cfg = config();
        StoreService.Snapshot right = snapshot("loan", "main", document(service("Right", method("m1", "query"))));
        StoreService.Snapshot left = snapshot("loan", "main", document(service("Left", method("m1", "query"))));
        List<DocumentModel.DiffChange> changes = Collections.singletonList(new DocumentModel.DiffChange("Info", "x", "changed"));

        when(loader.current()).thenReturn(cfg);
        when(store.latestSnapshot("loan", "main")).thenReturn(right);
        when(store.recentSnapshots("loan", "main", 2)).thenReturn(java.util.Arrays.asList(right, left));
        when(diff.compare(left.document, right.document)).thenReturn(changes);

        assertSame(changes, query.diff("loan", "main", null));
    }

    @Test
    void missingSnapshotIsModuleNotFound() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), mock(DiffService.class), new BranchDiffAnnotator(), mock(InvokeService.class));
        when(store.latestSnapshot("loan", "main")).thenReturn(null);

        assertThrows(DocumentQueryService.NotFound.class, () -> query.services("loan", "main"));
    }

    @Test
    void servicesWithBaseAnnotatesAndSortsAddedFirstThenModifiedThenRemoved() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), mock(DiffService.class), new BranchDiffAnnotator(), mock(InvokeService.class));
        DocumentModel.Document baseDoc = new DocumentModel.Document();
        baseDoc.services.add(serviceWithField("com.example.UnchangedFacade", "m1", "query", "id", "java.lang.String"));
        baseDoc.services.add(serviceWithField("com.example.ModifiedFacade", "m2", "query", "id", "java.lang.String"));
        baseDoc.services.add(serviceWithField("com.example.RemovedFacade", "m3", "query", "id", "java.lang.String"));

        DocumentModel.Document currentDoc = new DocumentModel.Document();
        currentDoc.services.add(serviceWithField("com.example.UnchangedFacade", "m1", "query", "id", "java.lang.String"));
        currentDoc.services.add(serviceWithFields("com.example.ModifiedFacade", "m2", "query", new String[][]{
                {"id", "java.lang.String"},
                {"newField", "java.lang.Integer"}
        }));
        currentDoc.services.add(serviceWithField("com.example.AddedFacade", "m4", "query", "id", "java.lang.String"));

        when(store.latestSnapshot("loan", "feature/x")).thenReturn(snapshot("loan", "feature/x", currentDoc));
        when(store.latestSnapshot("loan", "main")).thenReturn(snapshot("loan", "main", baseDoc));

        List<DocumentModel.ServiceDoc> out = query.services("loan", "feature/x", "main");

        assertEquals(4, out.size());
        assertEquals("com.example.AddedFacade", out.get(0).fqn);
        assertEquals(BranchDiffAnnotator.ADDED, out.get(0).changeKind);
        assertEquals("com.example.ModifiedFacade", out.get(1).fqn);
        assertEquals(BranchDiffAnnotator.MODIFIED, out.get(1).changeKind);
        assertEquals("com.example.RemovedFacade", out.get(2).fqn);
        assertEquals(BranchDiffAnnotator.REMOVED, out.get(2).changeKind);
        assertEquals("com.example.UnchangedFacade", out.get(3).fqn);
        assertEquals(BranchDiffAnnotator.UNCHANGED, out.get(3).changeKind);

        DocumentModel.ServiceDoc modified = out.get(1);
        assertEquals(1, modified.methods.size());
        DocumentModel.MethodDoc modMethod = modified.methods.get(0);
        assertEquals(BranchDiffAnnotator.MODIFIED, modMethod.changeKind);
        DocumentModel.FieldNode addedField = modMethod.params.get(0).tree.children.stream()
                .filter(c -> "newField".equals(c.name)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(BranchDiffAnnotator.ADDED, addedField.changeKind);
    }

    @Test
    void servicesWithBaseMissingFallsBackToCurrentUnchanged() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), mock(DiffService.class), new BranchDiffAnnotator(), mock(InvokeService.class));
        DocumentModel.Document currentDoc = new DocumentModel.Document();
        currentDoc.services.add(serviceWithField("com.example.FooFacade", "m1", "query", "id", "java.lang.String"));
        when(store.latestSnapshot("loan", "feature/x")).thenReturn(snapshot("loan", "feature/x", currentDoc));
        when(store.latestSnapshot("loan", "missing-base")).thenReturn(null);

        List<DocumentModel.ServiceDoc> out = query.services("loan", "feature/x", "missing-base");

        assertEquals(1, out.size());
        assertNull(out.get(0).changeKind);
    }

    @Test
    void servicesWithBaseRemovedFieldAppearsAsGhostInModifiedMethod() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, mock(MarkdownRenderer.class), mock(DiffService.class), new BranchDiffAnnotator(), mock(InvokeService.class));
        DocumentModel.Document baseDoc = new DocumentModel.Document();
        baseDoc.services.add(serviceWithFields("com.example.OrderFacade", "m1", "query", new String[][]{
                {"id", "java.lang.String"},
                {"obsolete", "java.lang.Long"}
        }));
        DocumentModel.Document currentDoc = new DocumentModel.Document();
        currentDoc.services.add(serviceWithField("com.example.OrderFacade", "m1", "query", "id", "java.lang.String"));

        when(store.latestSnapshot("loan", "feature/x")).thenReturn(snapshot("loan", "feature/x", currentDoc));
        when(store.latestSnapshot("loan", "main")).thenReturn(snapshot("loan", "main", baseDoc));

        List<DocumentModel.ServiceDoc> out = query.services("loan", "feature/x", "main");

        assertEquals(1, out.size());
        DocumentModel.MethodDoc method = out.get(0).methods.get(0);
        assertEquals(BranchDiffAnnotator.MODIFIED, method.changeKind);
        DocumentModel.FieldNode ghost = method.params.get(0).tree.children.stream()
                .filter(c -> "obsolete".equals(c.name)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(BranchDiffAnnotator.REMOVED, ghost.changeKind);
    }

    @Test
    void methodMarkdownWithBaseIncludesFieldChangeColumnAndGhostRows() throws Exception {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        StoreService store = mock(StoreService.class);
        DocumentQueryService query = new DocumentQueryService(loader, store, new MarkdownRenderer(new ObjectMapper()), mock(DiffService.class), new BranchDiffAnnotator(), mock(InvokeService.class));
        DocumentModel.Document baseDoc = new DocumentModel.Document();
        baseDoc.services.add(serviceWithFields("com.example.OrderFacade", "m1", "query", new String[][]{
                {"id", "java.lang.String"},
                {"obsolete", "java.lang.Long"}
        }));
        DocumentModel.Document currentDoc = new DocumentModel.Document();
        currentDoc.services.add(serviceWithFields("com.example.OrderFacade", "m1", "query", new String[][]{
                {"id", "java.lang.String"},
                {"newField", "java.lang.Integer"}
        }));

        when(store.latestSnapshot("loan", "feature/x")).thenReturn(snapshot("loan", "feature/x", currentDoc));
        when(store.latestSnapshot("loan", "main")).thenReturn(snapshot("loan", "main", baseDoc));

        String markdown = query.methodMarkdown("loan", "feature/x", "m1", "main");

        assertTrue(markdown.contains("| 变更 | 字段路径 | Java 类型 | JSON 输入 | 必填 | 说明 |"));
        assertTrue(markdown.contains("| 新增 | `request.newField`"));
        assertTrue(markdown.contains("| 删除 | `request.obsolete`"));
    }

    private DocumentModel.ServiceDoc serviceWithField(String fqn, String methodId, String methodName, String fieldName, String fieldType) {
        return serviceWithFields(fqn, methodId, methodName, new String[][]{{fieldName, fieldType}});
    }

    private DocumentModel.ServiceDoc serviceWithFields(String fqn, String methodId, String methodName, String[][] fields) {
        DocumentModel.MethodDoc method = method(methodId, methodName);
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = "request";
        param.javaType = "com.example.Request";
        param.tree = new DocumentModel.FieldNode();
        param.tree.path = "request";
        param.tree.name = "request";
        param.tree.javaType = "com.example.Request";
        param.tree.jsonType = "object";
        for (String[] field : fields) {
            DocumentModel.FieldNode child = new DocumentModel.FieldNode();
            child.path = "request." + field[0];
            child.name = field[0];
            child.javaType = field[1];
            child.jsonType = "string";
            param.tree.children.add(child);
        }
        method.params.add(param);
        return service(fqn, method);
    }

    private AppConfig config() {
        AppConfig cfg = new AppConfig();
        cfg.auth.adminTokens.add("admin-token");
        AppConfig.ProjectConfig project = new AppConfig.ProjectConfig();
        project.displayName = "贷款服务";
        project.repo = "git://loan";
        project.baselineBranch = "main";
        project.tokens = Collections.singletonList("loan-token");
        project.branchDefaults.directUrl = "bolt://127.0.0.1:12200";
        project.branchDefaults.uniqueId = "default-id";
        project.branchDefaults.version = "default-v";
        project.branchDefaults.targetAppName = "default-app";
        AppConfig.BranchOverride feature = new AppConfig.BranchOverride();
        feature.directUrl = "bolt://127.0.0.1:12201";
        feature.targetAppName = "apply-flow-app";
        project.branchOverrides.put("feature/*", feature);
        cfg.projects.put("loan", project);
        cfg.applyDefaults();
        return cfg;
    }

    private DocumentModel.Document document(DocumentModel.ServiceDoc service) {
        DocumentModel.Document doc = new DocumentModel.Document();
        doc.project = "loan";
        doc.branch = "feature/apply-flow";
        doc.commit = "abc123";
        doc.services.add(service);
        return doc;
    }

    private DocumentModel.ServiceDoc service(String fqn, DocumentModel.MethodDoc method) {
        DocumentModel.ServiceDoc service = new DocumentModel.ServiceDoc();
        service.fqn = fqn;
        service.status = "published";
        service.methods.add(method);
        return service;
    }

    private DocumentModel.MethodDoc method(String id, String name) {
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.id = id;
        method.name = name;
        method.returnType = "java.lang.String";
        return method;
    }

    private DocumentModel.PublishRecord publish(String binding, String uniqueId, String version) {
        DocumentModel.PublishRecord publish = new DocumentModel.PublishRecord();
        publish.binding = binding;
        publish.uniqueId = uniqueId;
        publish.version = version;
        return publish;
    }

    private StoreService.Snapshot snapshot(String project, String branch, DocumentModel.Document document) {
        StoreService.Snapshot snapshot = new StoreService.Snapshot();
        snapshot.id = 1;
        snapshot.project = project;
        snapshot.branch = branch;
        snapshot.commit = "abc123";
        snapshot.structureHash = "hash";
        snapshot.document = document;
        return snapshot;
    }
}
