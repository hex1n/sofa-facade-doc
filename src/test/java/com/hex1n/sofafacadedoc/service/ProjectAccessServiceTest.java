package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAccessServiceTest {
    @Test
    void allowsScopedProject() {
        ProjectAccessService access = new ProjectAccessService(loader(config()));
        AuthScope scope = new AuthScope();
        scope.projects.put("loan", Boolean.TRUE);

        access.require(scope, "loan");
    }

    @Test
    void rejectsProjectOutsideScopeBeforeCheckingExistence() {
        ProjectAccessService access = new ProjectAccessService(loader(config()));
        AuthScope scope = new AuthScope();
        scope.projects.put("loan", Boolean.TRUE);

        assertThrows(ProjectAccessService.Forbidden.class, () -> access.require(scope, "card"));
        assertThrows(ProjectAccessService.Forbidden.class, () -> access.require(scope, "missing"));
    }

    @Test
    void adminGetsNotFoundForMissingProject() {
        ProjectAccessService access = new ProjectAccessService(loader(config()));
        AuthScope scope = new AuthScope();
        scope.admin = true;

        assertThrows(ProjectAccessService.NotFound.class, () -> access.require(scope, "missing"));
    }

    @Test
    void nullScopeIsForbidden() {
        ProjectAccessService access = new ProjectAccessService(loader(config()));

        assertThrows(ProjectAccessService.Forbidden.class, () -> access.require(null, "loan"));
    }

    private AppConfig config() {
        AppConfig cfg = new AppConfig();
        cfg.auth.adminTokens.add("admin-token");
        cfg.projects.put("loan", project("loan-token"));
        cfg.applyDefaults();
        return cfg;
    }

    private AppConfig.ProjectConfig project(String token) {
        AppConfig.ProjectConfig project = new AppConfig.ProjectConfig();
        project.repo = "git://repo";
        project.baselineBranch = "main";
        project.tokens = Collections.singletonList(token);
        return project;
    }

    private AppConfigLoader loader(AppConfig cfg) {
        AppConfigLoader loader = mock(AppConfigLoader.class);
        when(loader.current()).thenReturn(cfg);
        return loader;
    }
}
