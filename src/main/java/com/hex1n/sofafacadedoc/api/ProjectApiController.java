package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.service.ProjectCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectApiController {
    private final ProjectCatalogService projectCatalog;
    private final ApiRequestContext requestContext;

    public ProjectApiController(ProjectCatalogService projectCatalog, ApiRequestContext requestContext) {
        this.projectCatalog = projectCatalog;
        this.requestContext = requestContext;
    }

    @GetMapping
    public List<Map<String, Object>> projects(HttpServletRequest request) {
        return projectCatalog.visibleProjects(requestContext.scope(request));
    }

    @GetMapping("/{project}/branches")
    public List<String> branches(@PathVariable String project, HttpServletRequest request) {
        requestContext.requireProject(request, project);
        return projectCatalog.branches(project);
    }
}
