package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.git.GitService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectCatalogService {
    private final AppConfigLoader configLoader;
    private final GitService gitService;

    public ProjectCatalogService(AppConfigLoader configLoader, GitService gitService) {
        this.configLoader = configLoader;
        this.gitService = gitService;
    }

    public List<Map<String, Object>> visibleProjects(AuthScope scope) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, AppConfig.ProjectConfig> entry : configLoader.current().projects.entrySet()) {
            if (!scope.canProject(entry.getKey())) continue;
            out.add(projectView(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    public List<String> branches(String project) {
        AppConfig.ProjectConfig cfg = configLoader.current().projects.get(project);
        if (cfg == null) throw new NotFound("project not found: " + project);
        return resolvedBranches(project, cfg);
    }

    private Map<String, Object> projectView(String id, AppConfig.ProjectConfig cfg) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", id);
        project.put("displayName", cfg.displayName);
        project.put("baselineBranch", cfg.baselineBranch);
        project.put("branches", resolvedBranches(id, cfg));
        return project;
    }

    private List<String> resolvedBranches(String project, AppConfig.ProjectConfig cfg) {
        try {
            List<String> branches = gitService.resolveBranches(project, cfg);
            return branches.isEmpty() ? configuredBranches(cfg) : branches;
        } catch (Exception e) {
            return configuredBranches(cfg);
        }
    }

    private List<String> configuredBranches(AppConfig.ProjectConfig cfg) {
        return cfg.branches == null || cfg.branches.include == null ? new ArrayList<>() : new ArrayList<>(cfg.branches.include);
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}
