package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.git.GitService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCatalogServiceTest {
    @Test
    void visibleProjectsAreFilteredByScopeAndIncludeResolvedBranches() throws Exception {
        AppConfig cfg = config();
        AppConfigLoader loader = mock(AppConfigLoader.class);
        GitService git = mock(GitService.class);
        ProjectCatalogService catalog = new ProjectCatalogService(loader, git);
        AuthScope scope = new AuthScope();
        scope.projects.put("loan", Boolean.TRUE);

        when(loader.current()).thenReturn(cfg);
        when(git.resolveBranches("loan", cfg.projects.get("loan"))).thenReturn(Arrays.asList("main", "feature/apply-flow"));

        List<Map<String, Object>> projects = catalog.visibleProjects(scope);

        assertEquals(1, projects.size());
        assertEquals("loan", projects.get(0).get("id"));
        assertEquals("贷款服务", projects.get(0).get("displayName"));
        assertEquals("main", projects.get(0).get("baselineBranch"));
        assertEquals(Arrays.asList("main", "feature/apply-flow"), projects.get(0).get("branches"));
        verify(git, never()).resolveBranches(eq("card"), any(AppConfig.ProjectConfig.class));
    }

    @Test
    void branchesFallBackToConfiguredIncludesWhenGitResolutionFails() throws Exception {
        AppConfig cfg = config();
        AppConfigLoader loader = mock(AppConfigLoader.class);
        GitService git = mock(GitService.class);
        ProjectCatalogService catalog = new ProjectCatalogService(loader, git);

        when(loader.current()).thenReturn(cfg);
        when(git.resolveBranches("loan", cfg.projects.get("loan"))).thenThrow(new IllegalStateException("fetch failed"));

        assertEquals(Arrays.asList("main", "feature/*"), catalog.branches("loan"));
    }

    @Test
    void branchesFallBackToConfiguredIncludesWhenGitReturnsEmpty() throws Exception {
        AppConfig cfg = config();
        AppConfigLoader loader = mock(AppConfigLoader.class);
        GitService git = mock(GitService.class);
        ProjectCatalogService catalog = new ProjectCatalogService(loader, git);

        when(loader.current()).thenReturn(cfg);
        when(git.resolveBranches("loan", cfg.projects.get("loan"))).thenReturn(Collections.emptyList());

        assertEquals(Arrays.asList("main", "feature/*"), catalog.branches("loan"));
    }

    @Test
    void missingProjectIsModuleNotFound() {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        GitService git = mock(GitService.class);
        ProjectCatalogService catalog = new ProjectCatalogService(loader, git);

        when(loader.current()).thenReturn(config());

        assertThrows(ProjectCatalogService.NotFound.class, () -> catalog.branches("missing"));
    }

    private AppConfig config() {
        AppConfig cfg = new AppConfig();
        cfg.auth.adminTokens.add("admin-token");
        cfg.projects.put("loan", project("贷款服务", "loan-token", "main", "main", "feature/*"));
        cfg.projects.put("card", project("卡服务", "card-token", "main", "main"));
        cfg.applyDefaults();
        return cfg;
    }

    private AppConfig.ProjectConfig project(String displayName, String token, String baselineBranch, String... branches) {
        AppConfig.ProjectConfig project = new AppConfig.ProjectConfig();
        project.displayName = displayName;
        project.repo = "git://" + displayName;
        project.baselineBranch = baselineBranch;
        project.tokens = Collections.singletonList(token);
        project.branches.include.addAll(Arrays.asList(branches));
        return project;
    }
}
