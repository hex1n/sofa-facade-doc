package com.hex1n.sofafacadedoc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ConfigurationAuthorization {
    private final ObjectMapper objectMapper;

    public ConfigurationAuthorization(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> editableProjects(AppConfig cfg, AuthScope scope) {
        requireConfigManagement(scope);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admin", scope.admin);
        out.put("teams", editableTeams(cfg, scope));

        List<Map<String, Object>> projects = new ArrayList<>();
        for (Map.Entry<String, AppConfig.ProjectConfig> entry : cfg.projects.entrySet()) {
            if (scope.admin || scope.canTeam(entry.getValue().team)) {
                projects.add(projectView(entry.getKey(), entry.getValue()));
            }
        }
        out.put("projects", projects);
        return out;
    }

    public void applyProjectEdits(AppConfig cfg, AuthScope scope, List<?> projectItems) {
        requireConfigManagement(scope);
        for (Object item : projectItems) {
            if (!(item instanceof Map)) throw new IllegalArgumentException("project item must be object");
            @SuppressWarnings("unchecked")
            Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) item);
            String id = stringValue(source.remove("id"));
            requireProjectId(id);
            AppConfig.ProjectConfig existing = cfg.projects.get(id);
            AppConfig.ProjectConfig project = objectMapper.convertValue(source, AppConfig.ProjectConfig.class);
            if (scope.admin) {
                applyAdminEdit(cfg, id, existing, project);
            } else {
                applyTeamEdit(cfg, scope, id, existing, project, source);
            }
        }
    }

    public ValidationResult validateProjectEdits(AppConfig cfg, AuthScope scope, List<?> projectItems) {
        requireConfigManagement(scope);
        ValidationResult result = new ValidationResult();
        if (projectItems == null || projectItems.isEmpty()) {
            result.add("", "projects", "至少需要提交一个项目配置");
            return result;
        }
        for (int i = 0; i < projectItems.size(); i++) {
            Object item = projectItems.get(i);
            if (!(item instanceof Map)) {
                result.add("", "projects[" + i + "]", "项目配置必须是对象");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) item);
            String id = stringValue(source.get("id"));
            validateProjectId(result, id, i);
            validateProjectPayload(result, cfg, scope, id, source);
        }
        result.ok = result.errors.isEmpty();
        return result;
    }

    private void applyAdminEdit(AppConfig cfg, String id, AppConfig.ProjectConfig existing, AppConfig.ProjectConfig project) {
        if (!notBlank(project.team) && existing != null) project.team = existing.team;
        cfg.projects.put(id, project);
    }

    private void applyTeamEdit(AppConfig cfg, AuthScope scope, String id, AppConfig.ProjectConfig existing, AppConfig.ProjectConfig project, Map<String, Object> source) {
        if (existing == null) {
            if (!notBlank(project.team)) project.team = singleTeam(scope);
            if (!scope.canTeam(project.team)) throw new Forbidden();
            cfg.projects.put(id, project);
            return;
        }

        if (!scope.canTeam(existing.team)) throw new Forbidden();
        if (notBlank(project.team) && !Objects.equals(existing.team, project.team)) throw new Forbidden();
        if (source.containsKey("repo") && !sameString(project.repo, existing.repo)) throw new Forbidden();
        if (source.containsKey("tokens") && !sameList(project.tokens, existing.tokens)) throw new Forbidden();

        project.team = existing.team;
        project.repo = existing.repo;
        project.tokens = copyList(existing.tokens);
        cfg.projects.put(id, project);
    }

    private List<Map<String, Object>> editableTeams(AppConfig cfg, AuthScope scope) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, AppConfig.TeamConfig> entry : cfg.teams.entrySet()) {
            if (!scope.admin && !scope.canTeam(entry.getKey())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.getKey());
            item.put("displayName", entry.getValue().displayName);
            if (scope.admin) item.put("tokens", copyList(entry.getValue().tokens));
            out.add(item);
        }
        return out;
    }

    private Map<String, Object> projectView(String id, AppConfig.ProjectConfig project) {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = objectMapper.convertValue(project, LinkedHashMap.class);
        out.put("id", id);
        return out;
    }

    private void requireConfigManagement(AuthScope scope) {
        if (scope == null || !scope.canManageConfig()) throw new Forbidden();
    }

    private void requireProjectId(String id) {
        if (!notBlank(id)) throw new IllegalArgumentException("project id is required");
        if (!id.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("project id only supports letters, numbers, dot, dash and underscore");
        }
    }

    private void validateProjectId(ValidationResult result, String id, int index) {
        if (!notBlank(id)) {
            result.add("", "projects[" + index + "].id", "项目 id 必填");
        } else if (!id.matches("[A-Za-z0-9_.-]+")) {
            result.add(id, "id", "项目 id 只支持字母、数字、点、横线和下划线");
        }
    }

    private void validateProjectPayload(ValidationResult result, AppConfig cfg, AuthScope scope, String id, Map<String, Object> source) {
        AppConfig.ProjectConfig existing = notBlank(id) ? cfg.projects.get(id) : null;
        AppConfig.ProjectConfig project;
        try {
            project = objectMapper.convertValue(source, AppConfig.ProjectConfig.class);
        } catch (IllegalArgumentException e) {
            result.add(id, "project", "项目配置结构不合法: " + e.getMessage());
            return;
        }
        if (scope.admin) {
            if (notBlank(project.team) && !cfg.teams.containsKey(project.team)) {
                result.add(id, "team", "团队不存在: " + project.team);
            }
        } else {
            if (existing == null) {
                if (notBlank(project.team) && !scope.canTeam(project.team)) result.add(id, "team", "只能创建本团队项目");
                if (!notBlank(project.team) && scope.teams.size() != 1) result.add(id, "team", "多团队 token 创建项目时必须指定团队");
            } else {
                if (!scope.canTeam(existing.team)) result.add(id, "team", "不能编辑其他团队项目");
                if (notBlank(project.team) && !Objects.equals(existing.team, project.team)) result.add(id, "team", "团队归属创建后不能修改");
                if (source.containsKey("repo") && !sameString(project.repo, existing.repo)) result.add(id, "repo", "团队 token 不能修改已创建项目的 Git 仓库");
                if (source.containsKey("tokens") && !sameList(project.tokens, existing.tokens)) result.add(id, "tokens", "团队 token 不能修改已创建项目的项目 token");
            }
        }
        if (!notBlank(project.repo)) result.add(id, "repo", "Git 仓库必填");
        if (project.tokens == null || project.tokens.isEmpty()) result.add(id, "tokens", "项目 token 至少需要一个");
        if (project.branches == null) {
            result.add(id, "branches", "分支规则必填");
        } else {
            if (project.branches.include == null || project.branches.include.isEmpty()) result.add(id, "branches.include", "至少配置一个分支 include 规则");
            if (project.branches.maxMatched <= 0) result.add(id, "branches.maxMatched", "maxMatched 必须大于 0");
        }
        if (project.branchDefaults == null) result.add(id, "branchDefaults", "默认运行配置必填");
        if (project.facadePackages == null || project.facadePackages.isEmpty()) result.add(id, "facadePackages", "建议至少配置一个 facade 包名前缀");
    }

    private String singleTeam(AuthScope scope) {
        if (scope.teams.size() != 1) throw new IllegalArgumentException("team is required");
        return scope.teams.iterator().next();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean sameString(String left, String right) {
        return Objects.equals(left, right);
    }

    private boolean sameList(List<String> left, List<String> right) {
        return copyList(left).equals(copyList(right));
    }

    private List<String> copyList(List<String> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class Forbidden extends RuntimeException {
    }

    public static class ValidationResult {
        public boolean ok = true;
        public List<ValidationError> errors = new ArrayList<>();

        void add(String project, String field, String message) {
            ok = false;
            ValidationError error = new ValidationError();
            error.project = project;
            error.field = field;
            error.message = message;
            errors.add(error);
        }
    }

    public static class ValidationError {
        public String project;
        public String field;
        public String message;
    }
}
