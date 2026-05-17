package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.service.ProjectAccessService;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Component
public class ApiRequestContext {
    private final ProjectAccessService projectAccess;

    public ApiRequestContext(ProjectAccessService projectAccess) {
        this.projectAccess = projectAccess;
    }

    public AuthScope scope(HttpServletRequest request) {
        return (AuthScope) request.getAttribute(AuthFilter.ATTR);
    }

    public AuthScope requireProject(HttpServletRequest request, String project) {
        AuthScope scope = scope(request);
        projectAccess.require(scope, project);
        return scope;
    }

    public String requiredBranch(String branch) {
        if (branch == null || branch.trim().isEmpty()) {
            throw new IllegalArgumentException("branch is required");
        }
        return branch.trim();
    }
}
