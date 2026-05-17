package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import com.hex1n.sofafacadedoc.service.DocumentQueryService;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{project}")
public class DocumentApiController {
    private final StoreService store;
    private final DocumentQueryService documentQuery;
    private final ApiRequestContext requestContext;

    public DocumentApiController(StoreService store, DocumentQueryService documentQuery, ApiRequestContext requestContext) {
        this.store = store;
        this.documentQuery = documentQuery;
        this.requestContext = requestContext;
    }

    @GetMapping("/branches/{branch}/services")
    public List<DocumentModel.ServiceDoc> services(@PathVariable String project, @PathVariable String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.services(project, requestContext.requiredBranch(branch), base);
    }

    @GetMapping("/branches/services")
    public List<DocumentModel.ServiceDoc> servicesByQuery(@PathVariable String project, @RequestParam String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.services(project, requestContext.requiredBranch(branch), base);
    }

    @GetMapping("/branches/{branch}/methods/{methodId}")
    public Map<String, Object> method(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.method(project, requestContext.requiredBranch(branch), methodId, base);
    }

    @GetMapping("/methods/{methodId}")
    public Map<String, Object> methodByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.method(project, requestContext.requiredBranch(branch), methodId, base);
    }

    @GetMapping(value = "/branches/{branch}/methods/{methodId}/markdown", produces = "text/markdown;charset=UTF-8")
    public String methodMarkdown(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.methodMarkdown(project, requestContext.requiredBranch(branch), methodId, base);
    }

    @GetMapping(value = "/methods/{methodId}/markdown", produces = "text/markdown;charset=UTF-8")
    public String methodMarkdownByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.methodMarkdown(project, requestContext.requiredBranch(branch), methodId, base);
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StoreService.SearchHit> search(@PathVariable String project, @RequestParam String q, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return store.search(project, q);
    }

    @GetMapping("/branches/{branch}/diff")
    public List<DocumentModel.DiffChange> diff(@PathVariable String project, @PathVariable String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.diff(project, requestContext.requiredBranch(branch), base);
    }

    @GetMapping("/diff")
    public List<DocumentModel.DiffChange> diffByQuery(@PathVariable String project, @RequestParam String branch, @RequestParam(required = false) String base, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.diff(project, requestContext.requiredBranch(branch), base);
    }
}
