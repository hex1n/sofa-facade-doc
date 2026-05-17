package com.hex1n.sofafacadedoc.service;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    private final AppConfigLoader configLoader;

    public ProjectAccessService(AppConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void require(AuthScope scope, String project) {
        if (scope == null || !scope.canProject(project)) throw new Forbidden();
        if (!configLoader.current().projects.containsKey(project)) throw new NotFound("project not found: " + project);
    }

    public static class Forbidden extends RuntimeException {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}
