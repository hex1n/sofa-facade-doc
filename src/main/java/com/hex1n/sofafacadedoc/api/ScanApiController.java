package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.service.ScanPresentationService;
import com.hex1n.sofafacadedoc.service.ScanService;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{project}")
public class ScanApiController {
    private final StoreService store;
    private final ScanService scanService;
    private final ScanPresentationService scanPresentation;
    private final ApiRequestContext requestContext;

    public ScanApiController(StoreService store, ScanService scanService, ScanPresentationService scanPresentation, ApiRequestContext requestContext) {
        this.store = store;
        this.scanService = scanService;
        this.scanPresentation = scanPresentation;
        this.requestContext = requestContext;
    }

    @PostMapping("/scan")
    public Map<String, DocumentModel.ScanResult> scanProject(@PathVariable String project, HttpServletRequest request) throws Exception {
        AuthScope scope = requestContext.requireProject(request, project);
        Map<String, DocumentModel.ScanResult> results = scanService.scanProject(project);
        return scanPresentation.presentResults(results, scope.admin);
    }

    @PostMapping("/branches/{branch}/scan")
    public DocumentModel.ScanResult scan(@PathVariable String project, @PathVariable String branch, HttpServletRequest request) throws Exception {
        AuthScope scope = requestContext.requireProject(request, project);
        return scanPresentation.present(scanService.scanBranch(project, requestContext.requiredBranch(branch)), scope.admin);
    }

    @PostMapping("/branches/scan")
    public DocumentModel.ScanResult scanByQuery(@PathVariable String project, @RequestParam String branch, HttpServletRequest request) throws Exception {
        AuthScope scope = requestContext.requireProject(request, project);
        return scanPresentation.present(scanService.scanBranch(project, requestContext.requiredBranch(branch)), scope.admin);
    }

    @GetMapping("/scan-reports")
    public List<StoreService.ScanReport> scanReports(@PathVariable String project, @RequestParam(required = false) String branch, @RequestParam(required = false, defaultValue = "20") int limit, HttpServletRequest request) throws Exception {
        AuthScope scope = requestContext.requireProject(request, project);
        String normalizedBranch = branch == null || branch.trim().isEmpty() ? null : branch.trim();
        List<StoreService.ScanReport> reports = store.listReports(project, normalizedBranch, limit);
        return scanPresentation.presentReports(reports, scope.admin);
    }
}
