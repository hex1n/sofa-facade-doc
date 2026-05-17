package com.hex1n.sofafacadedoc.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppConfigLoader {
    private final String path;
    private volatile AppConfig config;

    public AppConfigLoader(String path) {
        this.path = path;
        reload();
    }

    public synchronized AppConfig reload() {
        try {
            AppConfig loaded = load(Paths.get(path));
            this.config = loaded;
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("load config " + path + ": " + e.getMessage(), e);
        }
    }

    public String raw() throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    public synchronized AppConfig saveAndReload(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("config content is empty");
        }
        Path target = Paths.get(path);
        try {
            AppConfig loaded = parse(content);
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent == null ? Paths.get(".") : parent, target.getFileName().toString(), ".tmp");
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveFailed) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.config = loaded;
            return loaded;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("save config " + path + ": " + e.getMessage(), e);
        }
    }

    public synchronized AppConfig updateAndSave(ConfigEditor editor) {
        try {
            AppConfig working = parse(toYaml(current()));
            editor.edit(working);
            working.applyDefaults();
            working.validate();
            String content = toYaml(working);
            return saveAndReload(content);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("update config " + path + ": " + e.getMessage(), e);
        }
    }

    private AppConfig load(Path file) throws Exception {
        try (InputStream in = new FileInputStream(file.toFile())) {
            String content = new String(readAll(in), StandardCharsets.UTF_8);
            return parse(content);
        }
    }

    private AppConfig parse(String content) {
        Yaml yaml = new Yaml();
        AppConfig loaded = yaml.loadAs(content, AppConfig.class);
        if (loaded == null) {
            loaded = new AppConfig();
        }
        loaded.applyDefaults();
        loaded.validate();
        return loaded;
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    public AppConfig current() {
        return config;
    }

    public String path() {
        return path;
    }

    public String toYaml(AppConfig cfg) {
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);
        return new Yaml(options).dump(configMap(cfg));
    }

    public AuthScope authenticate(String authorization) {
        String token = authorization == null ? "" : authorization.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        }
        AuthScope scope = new AuthScope();
        if (token.isEmpty()) {
            return scope;
        }
        AppConfig cfg = current();
        if (cfg.auth.adminTokens.contains(token)) {
            scope.admin = true;
            scope.teams.addAll(cfg.teams.keySet());
            for (String project : cfg.projects.keySet()) {
                scope.projects.put(project, Boolean.TRUE);
            }
            return scope;
        }
        for (Map.Entry<String, AppConfig.TeamConfig> entry : cfg.teams.entrySet()) {
            if (entry.getValue().tokens.contains(token)) {
                scope.teams.add(entry.getKey());
            }
        }
        if (!scope.teams.isEmpty()) {
            for (Map.Entry<String, AppConfig.ProjectConfig> entry : cfg.projects.entrySet()) {
                if (scope.teams.contains(entry.getValue().team)) {
                    scope.projects.put(entry.getKey(), Boolean.TRUE);
                }
            }
        }
        for (Map.Entry<String, AppConfig.ProjectConfig> entry : cfg.projects.entrySet()) {
            if (entry.getValue().tokens.contains(token)) {
                scope.projects.put(entry.getKey(), Boolean.TRUE);
            }
        }
        return scope;
    }

    public interface ConfigEditor {
        void edit(AppConfig config) throws Exception;
    }

    private Map<String, Object> configMap(AppConfig cfg) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("listen", cfg.server.listen);
        server.put("dataDir", cfg.server.dataDir);
        out.put("server", server);

        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("adminTokens", new ArrayList<>(cfg.auth.adminTokens));
        out.put("auth", auth);

        Map<String, Object> git = new LinkedHashMap<>();
        putIfNotBlank(git, "sshKeyPath", cfg.git.sshKeyPath);
        putIfNotBlank(git, "tokenEnv", cfg.git.tokenEnv);
        if (!git.isEmpty()) out.put("git", git);

        Map<String, Object> teams = new LinkedHashMap<>();
        for (Map.Entry<String, AppConfig.TeamConfig> entry : cfg.teams.entrySet()) {
            Map<String, Object> team = new LinkedHashMap<>();
            putIfNotBlank(team, "displayName", entry.getValue().displayName);
            team.put("tokens", safeList(entry.getValue().tokens));
            teams.put(entry.getKey(), team);
        }
        if (!teams.isEmpty()) out.put("teams", teams);

        Map<String, Object> projects = new LinkedHashMap<>();
        for (Map.Entry<String, AppConfig.ProjectConfig> entry : cfg.projects.entrySet()) {
            projects.put(entry.getKey(), projectMap(entry.getValue()));
        }
        out.put("projects", projects);
        return out;
    }

    private Map<String, Object> projectMap(AppConfig.ProjectConfig project) {
        Map<String, Object> out = new LinkedHashMap<>();
        putIfNotBlank(out, "team", project.team);
        putIfNotBlank(out, "displayName", project.displayName);
        putIfNotBlank(out, "repo", project.repo);
        putIfNotBlank(out, "baselineBranch", project.baselineBranch);
        out.put("tokens", safeList(project.tokens));
        out.put("branchDefaults", branchRuntimeMap(project.branchDefaults));
        Map<String, Object> branches = new LinkedHashMap<>();
        branches.put("include", safeList(project.branches.include));
        branches.put("exclude", safeList(project.branches.exclude));
        branches.put("maxMatched", project.branches.maxMatched);
        out.put("branches", branches);
        out.put("sourceRoots", safeList(project.sourceRoots));
        out.put("resourceRoots", safeList(project.resourceRoots));
        out.put("facadePackages", safeList(project.facadePackages));
        out.put("dependencyJars", safeList(project.dependencyJars));
        Map<String, Object> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, AppConfig.BranchOverride> entry : project.branchOverrides.entrySet()) {
            overrides.put(entry.getKey(), branchOverrideMap(entry.getValue()));
        }
        out.put("branchOverrides", overrides);
        return out;
    }

    private Map<String, Object> branchRuntimeMap(AppConfig.BranchRuntime runtime) {
        Map<String, Object> out = new LinkedHashMap<>();
        putIfNotBlank(out, "directUrl", runtime.directUrl);
        putIfNotBlank(out, "uniqueId", runtime.uniqueId);
        putIfNotBlank(out, "version", runtime.version);
        putIfNotBlank(out, "targetAppName", runtime.targetAppName);
        out.put("springProfiles", safeList(runtime.springProfiles));
        return out;
    }

    private Map<String, Object> branchOverrideMap(AppConfig.BranchOverride override) {
        Map<String, Object> out = branchRuntimeMap(override);
        if (!override.sourceRoots.isEmpty()) out.put("sourceRoots", safeList(override.sourceRoots));
        if (!override.resourceRoots.isEmpty()) out.put("resourceRoots", safeList(override.resourceRoots));
        if (!override.facadePackages.isEmpty()) out.put("facadePackages", safeList(override.facadePackages));
        if (!override.dependencyJars.isEmpty()) out.put("dependencyJars", safeList(override.dependencyJars));
        return out;
    }

    private List<String> safeList(List<String> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    private void putIfNotBlank(Map<String, Object> out, String key, String value) {
        if (value != null && !value.trim().isEmpty()) out.put(key, value);
    }
}
