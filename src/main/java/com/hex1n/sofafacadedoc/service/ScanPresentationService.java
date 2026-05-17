package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ScanPresentationService {
    private static final String REDACTED_SCAN_FAILURE = "scan failed; ask an admin to check details";

    public Map<String, DocumentModel.ScanResult> presentResults(Map<String, DocumentModel.ScanResult> results, boolean detailed) {
        if (results == null) return null;
        for (DocumentModel.ScanResult result : results.values()) {
            present(result, detailed);
        }
        return results;
    }

    public List<StoreService.ScanReport> presentReports(List<StoreService.ScanReport> reports, boolean detailed) {
        if (reports == null) return null;
        for (StoreService.ScanReport report : reports) {
            if (report == null) continue;
            report.message = presentMessage(report.status, report.message, detailed);
        }
        return reports;
    }

    public DocumentModel.ScanResult present(DocumentModel.ScanResult result, boolean detailed) {
        if (result == null) return null;
        result.message = presentMessage(result.status, result.message, detailed);
        return result;
    }

    private String presentMessage(String status, String message, boolean detailed) {
        if ("failed".equals(status) && !detailed) return REDACTED_SCAN_FAILURE;
        return MessageSanitizer.sanitize(message);
    }
}
