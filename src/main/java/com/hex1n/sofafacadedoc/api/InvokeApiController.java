package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.invoke.InvokeService;
import com.hex1n.sofafacadedoc.service.DocumentQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{project}")
public class InvokeApiController {
    private final DocumentQueryService documentQuery;
    private final ApiRequestContext requestContext;

    public InvokeApiController(DocumentQueryService documentQuery, ApiRequestContext requestContext) {
        this.documentQuery = documentQuery;
        this.requestContext = requestContext;
    }

    @GetMapping("/branches/{branch}/methods/{methodId}/probe")
    public InvokeService.ProbeResult probe(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, HttpServletRequest request) {
        requestContext.requireProject(request, project);
        return documentQuery.probe(project, requestContext.requiredBranch(branch));
    }

    @GetMapping("/methods/{methodId}/probe")
    public InvokeService.ProbeResult probeByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, HttpServletRequest request) {
        requestContext.requireProject(request, project);
        return documentQuery.probe(project, requestContext.requiredBranch(branch));
    }

    @PostMapping("/branches/{branch}/methods/{methodId}/validate")
    public InvokeService.ValidateResult validate(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.validate(project, requestContext.requiredBranch(branch), methodId, body);
    }

    @PostMapping("/methods/{methodId}/validate")
    public InvokeService.ValidateResult validateByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.validate(project, requestContext.requiredBranch(branch), methodId, body);
    }

    @PostMapping("/branches/{branch}/methods/{methodId}/invoke")
    public InvokeService.InvokeResult invoke(@PathVariable String project, @PathVariable String branch, @PathVariable String methodId, @RequestParam(required = false, name = "publish") Integer publishIndex, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.invoke(project, requestContext.requiredBranch(branch), methodId, publishIndex, body);
    }

    @PostMapping("/methods/{methodId}/invoke")
    public InvokeService.InvokeResult invokeByQuery(@PathVariable String project, @PathVariable String methodId, @RequestParam String branch, @RequestParam(required = false, name = "publish") Integer publishIndex, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        requestContext.requireProject(request, project);
        return documentQuery.invoke(project, requestContext.requiredBranch(branch), methodId, publishIndex, body);
    }
}
