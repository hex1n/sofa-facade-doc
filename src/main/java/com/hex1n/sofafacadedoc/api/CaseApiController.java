package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.service.CaseLibraryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{project}")
public class CaseApiController {
    private final CaseLibraryService caseLibrary;
    private final ApiRequestContext requestContext;

    public CaseApiController(CaseLibraryService caseLibrary, ApiRequestContext requestContext) {
        this.caseLibrary = caseLibrary;
        this.requestContext = requestContext;
    }

    @GetMapping("/branches/{branch}/methods/{methodId}/cases")
    public List<DocumentModel.SavedCase> cases(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestParam String service, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return caseLibrary.list(project, requestContext.requiredBranch(branch), service, methodId);
    }

    @GetMapping("/methods/{methodId}/cases")
    public List<DocumentModel.SavedCase> casesByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestParam String service, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return caseLibrary.list(project, requestContext.requiredBranch(branch), service, methodId);
    }

    @PostMapping("/branches/{branch}/methods/{methodId}/cases")
    public Map<String, Object> saveCase(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestParam String service, @RequestBody DocumentModel.SavedCase item, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        long id = caseLibrary.create(project, requestContext.requiredBranch(branch), service, methodId, item);
        return Collections.singletonMap("id", id);
    }

    @PostMapping("/methods/{methodId}/cases")
    public Map<String, Object> saveCaseByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestParam String service, @RequestBody DocumentModel.SavedCase item, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        long id = caseLibrary.create(project, requestContext.requiredBranch(branch), service, methodId, item);
        return Collections.singletonMap("id", id);
    }

    @PutMapping("/cases/{id}")
    public Map<String, Object> updateCase(@PathVariable String project, @PathVariable long id, @RequestBody DocumentModel.SavedCase item, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        long saved = caseLibrary.update(project, id, item);
        return Collections.singletonMap("id", saved);
    }

    @DeleteMapping("/cases/{id}")
    public Map<String, Object> deleteCase(@PathVariable String project, @PathVariable long id, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        caseLibrary.delete(project, id);
        return Collections.singletonMap("ok", true);
    }
}
