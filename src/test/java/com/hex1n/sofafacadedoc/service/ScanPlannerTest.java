package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.scanner.SourceScanner;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ScanPlannerTest {
    private final ScanPlanner planner = new ScanPlanner();

    @Test
    void commitUnchangedReturnsExistingSnapshotWithoutReport() {
        StoreService.Snapshot latest = snapshot("c1", "h1", 12L, document("old"));

        ScanPlanner.ScanDecision decision = planner.forCommit("c1", latest);

        assertTrue(decision.terminal);
        assertFalse(decision.saveReport);
        assertEquals("unchanged", decision.result.status);
        assertEquals("commit unchanged", decision.result.message);
        assertEquals(12L, decision.result.snapshotId);
        assertSame(latest.document, decision.result.document);
    }

    @Test
    void unrelatedChangedFilesSkipSourceScanWithReport() {
        StoreService.Snapshot latest = snapshot("c1", "h1", 12L, document("old"));

        ScanPlanner.ScanDecision decision = planner.forChangedFiles(
                "c2",
                latest,
                Collections.singletonList("README.md"),
                effective("facade/src/main/java", "service/src/main/resources"));

        assertTrue(decision.terminal);
        assertTrue(decision.saveReport);
        assertEquals("skipped", decision.reportStatus);
        assertEquals("no interface-related file changed", decision.result.message);
        assertEquals("h1", decision.result.structureHash);
    }

    @Test
    void interfaceRelatedFilesRequireSourceScan() {
        AppConfig.EffectiveBranch cfg = effective("facade/src/main/java", "service/src/main/resources");

        assertTrue(planner.interfaceRelated(Collections.singletonList("facade/src/main/java/com/acme/Foo.java"), cfg));
        assertTrue(planner.interfaceRelated(Collections.singletonList("service/src/main/resources/META-INF/spring/sofa-services.xml"), cfg));
        assertTrue(planner.interfaceRelated(Collections.singletonList("service/src/main/resources/application-test.yml"), cfg));
        assertFalse(planner.interfaceRelated(Collections.singletonList("facade/src/test/java/com/acme/FooTest.java"), cfg));
        assertFalse(planner.interfaceRelated(Collections.singletonList("docs/readme.md"), cfg));
    }

    @Test
    void unchangedStructureSkipsSnapshotButKeepsScannedDocument() {
        StoreService.Snapshot latest = snapshot("c1", "h1", 12L, document("old"));
        SourceScanner.ScanOutput output = output("h1", document("new"));

        ScanPlanner.ScanDecision decision = planner.forScanOutput("c2", latest, output);

        assertTrue(decision.terminal);
        assertTrue(decision.saveReport);
        assertFalse(decision.result.snapshotCreated);
        assertEquals("structure hash unchanged", decision.result.message);
        assertEquals(12L, decision.result.snapshotId);
        assertSame(output.document, decision.result.document);
    }

    @Test
    void changedStructureRequiresSnapshotThenReportsSuccess() {
        StoreService.Snapshot latest = snapshot("c1", "h1", 12L, document("old"));
        SourceScanner.ScanOutput output = output("h2", document("new"));

        ScanPlanner.ScanDecision beforeSave = planner.forScanOutput("c2", latest, output);
        ScanPlanner.ScanDecision afterSave = planner.snapshotCreated("c2", output, 13L);

        assertTrue(beforeSave.saveSnapshot);
        assertSame(output, beforeSave.output);
        assertTrue(afterSave.terminal);
        assertTrue(afterSave.saveReport);
        assertEquals("success", afterSave.reportStatus);
        assertEquals("snapshot created", afterSave.result.message);
        assertEquals(13L, afterSave.result.snapshotId);
    }

    @Test
    void failedResultUsesFailureStatus() {
        DocumentModel.ScanResult result = planner.failed("boom");

        assertEquals("failed", result.status);
        assertEquals("boom", result.message);
        assertFalse(result.snapshotCreated);
    }

    private AppConfig.EffectiveBranch effective(String sourceRoot, String resourceRoot) {
        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots.addAll(Arrays.asList(sourceRoot));
        cfg.resourceRoots.addAll(Arrays.asList(resourceRoot));
        return cfg;
    }

    private StoreService.Snapshot snapshot(String commit, String hash, long id, DocumentModel.Document doc) {
        StoreService.Snapshot snapshot = new StoreService.Snapshot();
        snapshot.id = id;
        snapshot.commit = commit;
        snapshot.structureHash = hash;
        snapshot.document = doc;
        return snapshot;
    }

    private SourceScanner.ScanOutput output(String hash, DocumentModel.Document doc) {
        SourceScanner.ScanOutput output = new SourceScanner.ScanOutput();
        output.structureHash = hash;
        output.document = doc;
        return output;
    }

    private DocumentModel.Document document(String commit) {
        DocumentModel.Document doc = new DocumentModel.Document();
        doc.project = "loan";
        doc.branch = "feature/apply-flow";
        doc.commit = commit;
        return doc;
    }
}
