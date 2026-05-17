package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.config.ConfigurationAuthorization;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigApiController {
    private final AppConfigLoader configLoader;
    private final ConfigurationAuthorization configurationAuthorization;
    private final ApiRequestContext requestContext;

    public ConfigApiController(AppConfigLoader configLoader, ConfigurationAuthorization configurationAuthorization, ApiRequestContext requestContext) {
        this.configLoader = configLoader;
        this.configurationAuthorization = configurationAuthorization;
        this.requestContext = requestContext;
    }

    @PostMapping("/admin/reload-config")
    public Map<String, Object> reload(HttpServletRequest request) {
        AuthScope scope = requestContext.scope(request);
        if (!scope.admin) throw new ApiController.ForbiddenException();
        configLoader.reload();
        return Collections.singletonMap("ok", true);
    }

    @GetMapping("/admin/config")
    public Map<String, Object> config(HttpServletRequest request) throws Exception {
        AuthScope scope = requestContext.scope(request);
        if (!scope.admin) throw new ApiController.ForbiddenException();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", configLoader.path());
        out.put("content", configLoader.raw());
        return out;
    }

    @PostMapping(value = "/admin/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthScope scope = requestContext.scope(request);
        if (!scope.admin) throw new ApiController.ForbiddenException();
        Object content = body == null ? null : body.get("content");
        if (!(content instanceof String)) throw new IllegalArgumentException("content is required");
        configLoader.saveAndReload((String) content);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("path", configLoader.path());
        return out;
    }

    @GetMapping("/config/projects")
    public Map<String, Object> editableProjects(HttpServletRequest request) {
        return configurationAuthorization.editableProjects(configLoader.current(), requestContext.scope(request));
    }

    @PutMapping(value = "/config/projects", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> saveEditableProjects(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthScope scope = requestContext.scope(request);
        if (!scope.canManageConfig()) throw new ApiController.ForbiddenException();
        Object rawProjects = body == null ? null : body.get("projects");
        if (!(rawProjects instanceof List)) throw new IllegalArgumentException("projects is required");
        List<?> projectItems = (List<?>) rawProjects;
        configLoader.updateAndSave(cfg -> configurationAuthorization.applyProjectEdits(cfg, scope, projectItems));
        return editableProjects(request);
    }

    @PostMapping(value = "/config/projects/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConfigurationAuthorization.ValidationResult validateEditableProjects(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        AuthScope scope = requestContext.scope(request);
        if (!scope.canManageConfig()) throw new ApiController.ForbiddenException();
        Object rawProjects = body == null ? null : body.get("projects");
        if (!(rawProjects instanceof List)) throw new IllegalArgumentException("projects is required");
        return configurationAuthorization.validateProjectEdits(configLoader.current(), scope, (List<?>) rawProjects);
    }
}
