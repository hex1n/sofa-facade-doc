package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanPresentationServiceTest {
    private final ScanPresentationService presentation = new ScanPresentationService();

    @Test
    void hidesFailedScanResultDetailsForNonAdmin() {
        DocumentModel.ScanResult result = scanResult("failed", "no facade service found at /private/source");
        Map<String, DocumentModel.ScanResult> results = new LinkedHashMap<>();
        results.put("feature/no-facade", result);

        presentation.presentResults(results, false);

        assertEquals("scan failed; ask an admin to check details", result.message);
    }

    @Test
    void keepsAndSanitizesScanResultDetailsForAdmin() {
        DocumentModel.ScanResult result = scanResult("failed", "Authorization: Bearer secret https://user:pass@git.example.com/repo.git");

        presentation.present(result, true);

        assertEquals("Authorization: Bearer *** https://***@git.example.com/repo.git", result.message);
    }

    @Test
    void keepsSanitizedNonFailureDetailsForNonAdmin() {
        DocumentModel.ScanResult result = scanResult("skipped", "https://user:pass@git.example.com/repo.git unchanged");

        presentation.present(result, false);

        assertEquals("https://***@git.example.com/repo.git unchanged", result.message);
    }

    @Test
    void hidesFailedReportDetailsForNonAdmin() {
        StoreService.ScanReport report = new StoreService.ScanReport();
        report.status = "failed";
        report.message = "failure from /private/source";

        presentation.presentReports(Collections.singletonList(report), false);

        assertEquals("scan failed; ask an admin to check details", report.message);
    }

    private DocumentModel.ScanResult scanResult(String status, String message) {
        DocumentModel.ScanResult result = new DocumentModel.ScanResult();
        result.status = status;
        result.message = message;
        return result;
    }
}
