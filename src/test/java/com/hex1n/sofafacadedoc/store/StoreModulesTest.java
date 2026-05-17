package com.hex1n.sofafacadedoc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreModulesTest {
    @TempDir
    Path temp;

    private SnapshotStore snapshots;
    private SearchIndexStore searchIndex;
    private ScanReportStore scanReports;
    private SavedCaseStore savedCases;

    @BeforeEach
    void setUp() throws Exception {
        Path config = temp.resolve("config.yml");
        Files.write(config, configYaml().getBytes(StandardCharsets.UTF_8));
        AppConfigLoader loader = new AppConfigLoader(config.toString());
        StoreDatabase database = new StoreDatabase(loader);
        database.init();
        searchIndex = new SearchIndexStore(database);
        snapshots = new SnapshotStore(database, new ObjectMapper(), searchIndex);
        scanReports = new ScanReportStore(database);
        savedCases = new SavedCaseStore(database);
    }

    @Test
    void snapshotStorePersistsLatestSnapshotAndRefreshesSearchIndex() throws Exception {
        long first = snapshots.saveSnapshot("loan", "main", "c1", "h1", document("queryStatus", "订单号"));
        long second = snapshots.saveSnapshot("loan", "main", "c2", "h2", document("queryStatus", "客户编号"));

        StoreService.Snapshot latest = snapshots.latestSnapshot("loan", "main");
        assertNotNull(latest);
        assertEquals(second, latest.id);
        assertEquals("c2", latest.commit);
        assertEquals("h2", latest.structureHash);

        List<StoreService.Snapshot> recent = snapshots.recentSnapshots("loan", "main", 10);
        assertEquals(2, recent.size());
        assertEquals(second, recent.get(0).id);
        assertEquals(first, recent.get(1).id);

        List<StoreService.SearchHit> hits = searchIndex.search("loan", "客户编号");
        assertEquals(1, hits.size());
        assertEquals(second, hits.get(0).snapshotId);
    }

    @Test
    void scanReportStorePrunesFailuresPerProjectBranchAndStatus() throws Exception {
        for (int i = 0; i < 25; i++) {
            scanReports.saveReport("loan", "feature/apply", "c" + i, "failed", "failure-" + i);
        }
        scanReports.saveReport("loan", "feature/apply", "c-ok", "success", "snapshot created");

        List<StoreService.ScanReport> failed = scanReports.listReports("loan", "feature/apply", 30);
        int failedCount = 0;
        for (StoreService.ScanReport report : failed) {
            if ("failed".equals(report.status)) failedCount++;
        }
        assertEquals(20, failedCount);
        assertEquals("success", failed.get(0).status);
        assertTrue(failed.get(1).message.startsWith("failure-"));
    }

    @Test
    void savedCaseStoreCreatesUpdatesListsAndDeletesProjectScopedCases() throws Exception {
        DocumentModel.SavedCase item = new DocumentModel.SavedCase();
        item.project = "loan";
        item.branch = "main";
        item.service = "com.company.loan.facade.LoanFacade";
        item.methodId = "query-abc";
        item.name = "正常查询";
        item.argsJson = "{\"orderNo\":\"A1\"}";

        long id = savedCases.saveCase(item);
        List<DocumentModel.SavedCase> cases = savedCases.listCases("loan", "main", item.service, item.methodId);
        assertEquals(1, cases.size());
        assertEquals("正常查询", cases.get(0).name);

        item.id = id;
        item.name = "更新后查询";
        item.argsJson = "{\"orderNo\":\"A2\"}";
        long updated = savedCases.saveCase(item);
        assertEquals(id, updated);
        assertEquals("更新后查询", savedCases.listCases("loan", "main", item.service, item.methodId).get(0).name);

        savedCases.deleteCase("other-project", id);
        assertEquals(1, savedCases.listCases("loan", "main", item.service, item.methodId).size());
        savedCases.deleteCase("loan", id);
        assertEquals(0, savedCases.listCases("loan", "main", item.service, item.methodId).size());
    }

    private DocumentModel.Document document(String methodName, String fieldComment) {
        DocumentModel.Document doc = new DocumentModel.Document();
        doc.project = "loan";
        doc.branch = "main";
        doc.commit = "commit";
        DocumentModel.ServiceDoc service = new DocumentModel.ServiceDoc();
        service.fqn = "com.company.loan.facade.LoanFacade";
        service.status = "published";
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.id = methodName + "-abc";
        method.name = methodName;
        method.comment = "查询订单状态";
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = "request";
        param.javaType = "com.company.loan.facade.dto.QueryRequest";
        param.jsonType = "object";
        DocumentModel.FieldNode root = new DocumentModel.FieldNode();
        root.path = "request";
        root.name = "request";
        root.javaType = param.javaType;
        root.jsonType = "object";
        DocumentModel.FieldNode orderNo = new DocumentModel.FieldNode();
        orderNo.path = "request.orderNo";
        orderNo.name = "orderNo";
        orderNo.javaType = "java.lang.String";
        orderNo.jsonType = "string";
        orderNo.comment = fieldComment;
        root.children.add(orderNo);
        param.tree = root;
        method.params.add(param);
        service.methods.add(method);
        doc.services.add(service);
        return doc;
    }

    private String configYaml() {
        return "" +
                "server:\n" +
                "  listen: \"127.0.0.1:0\"\n" +
                "  dataDir: \"" + temp.resolve("data").toString().replace("\\", "\\\\") + "\"\n" +
                "auth:\n" +
                "  adminTokens: [\"admin-token\"]\n" +
                "projects:\n" +
                "  loan:\n" +
                "    displayName: \"贷款服务\"\n" +
                "    repo: \"" + temp.resolve("repo").toString().replace("\\", "\\\\") + "\"\n" +
                "    tokens: [\"loan-token\"]\n";
    }
}
