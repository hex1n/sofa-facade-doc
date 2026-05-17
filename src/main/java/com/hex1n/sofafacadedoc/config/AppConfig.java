package com.hex1n.sofafacadedoc.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class AppConfig {
    public ServerConfig server = new ServerConfig();
    public AuthConfig auth = new AuthConfig();
    public GitConfig git = new GitConfig();
    public Map<String, TeamConfig> teams = new LinkedHashMap<>();
    public Map<String, ProjectConfig> projects = new LinkedHashMap<>();

    public void applyDefaults() {
        if (server == null) server = new ServerConfig();
        if (auth == null) auth = new AuthConfig();
        if (git == null) git = new GitConfig();
        if (teams == null) teams = new LinkedHashMap<>();
        if (projects == null) projects = new LinkedHashMap<>();
        if (server.listen == null || server.listen.trim().isEmpty()) {
            server.listen = "0.0.0.0:8080";
        }
        if (server.dataDir == null || server.dataDir.trim().isEmpty()) {
            server.dataDir = "data";
        }
        if (auth.adminTokens == null) auth.adminTokens = new ArrayList<>();
        for (Map.Entry<String, TeamConfig> entry : teams.entrySet()) {
            TeamConfig team = entry.getValue();
            if (team == null) {
                team = new TeamConfig();
                entry.setValue(team);
            }
            if (team.displayName == null || team.displayName.trim().isEmpty()) {
                team.displayName = entry.getKey();
            }
            if (team.tokens == null) team.tokens = new ArrayList<>();
        }
        for (Map.Entry<String, ProjectConfig> entry : projects.entrySet()) {
            ProjectConfig project = entry.getValue();
            if (project == null) {
                project = new ProjectConfig();
                entry.setValue(project);
            }
            if (project.displayName == null || project.displayName.trim().isEmpty()) {
                project.displayName = entry.getKey();
            }
            if (project.tokens == null) project.tokens = new ArrayList<>();
            if (project.branchDefaults == null) project.branchDefaults = new BranchRuntime();
            if (project.branches == null) project.branches = new BranchSelector();
            if (project.sourceRoots == null) project.sourceRoots = new ArrayList<>();
            if (project.resourceRoots == null) project.resourceRoots = new ArrayList<>();
            if (project.facadePackages == null) project.facadePackages = new ArrayList<>();
            if (project.dependencyJars == null) project.dependencyJars = new ArrayList<>();
            if (project.branchOverrides == null) project.branchOverrides = new LinkedHashMap<>();
            if (project.branchDefaults.springProfiles == null) project.branchDefaults.springProfiles = new ArrayList<>();
            if (project.branches.include == null) project.branches.include = new ArrayList<>();
            if (project.branches.exclude == null) project.branches.exclude = new ArrayList<>();
            if (project.branches.maxMatched <= 0) {
                project.branches.maxMatched = 20;
            }
            if (project.branches.include.isEmpty() && project.baselineBranch != null && !project.baselineBranch.trim().isEmpty()) {
                project.branches.include.add(project.baselineBranch);
            }
            for (Map.Entry<String, BranchOverride> overrideEntry : project.branchOverrides.entrySet()) {
                BranchOverride override = overrideEntry.getValue();
                if (override == null) {
                    override = new BranchOverride();
                    overrideEntry.setValue(override);
                }
                if (override.springProfiles == null) override.springProfiles = new ArrayList<>();
                if (override.sourceRoots == null) override.sourceRoots = new ArrayList<>();
                if (override.resourceRoots == null) override.resourceRoots = new ArrayList<>();
                if (override.facadePackages == null) override.facadePackages = new ArrayList<>();
                if (override.dependencyJars == null) override.dependencyJars = new ArrayList<>();
            }
        }
    }

    public void validate() {
        if (auth.adminTokens.isEmpty()) {
            throw new IllegalArgumentException("auth.adminTokens must contain at least one token");
        }
        if (projects.isEmpty()) {
            throw new IllegalArgumentException("projects must contain at least one project");
        }
        for (Map.Entry<String, ProjectConfig> entry : projects.entrySet()) {
            String key = entry.getKey();
            ProjectConfig project = entry.getValue();
            if (project.team != null && !project.team.trim().isEmpty() && !teams.containsKey(project.team)) {
                throw new IllegalArgumentException("projects." + key + ".team does not exist: " + project.team);
            }
            if (project.repo == null || project.repo.trim().isEmpty()) {
                throw new IllegalArgumentException("projects." + key + ".repo is required");
            }
            if (project.tokens.isEmpty()) {
                throw new IllegalArgumentException("projects." + key + ".tokens must contain at least one token");
            }
        }
    }

    public static class ServerConfig {
        public String listen = "0.0.0.0:8080";
        public String dataDir = "data";
    }

    public static class AuthConfig {
        public List<String> adminTokens = new ArrayList<>();
    }

    public static class TeamConfig {
        public String displayName;
        public List<String> tokens = new ArrayList<>();
    }

    public static class GitConfig {
        public String sshKeyPath;
        public String tokenEnv;
    }

    public static class ProjectConfig {
        public String team;
        public String displayName;
        public String repo;
        public String baselineBranch;
        public List<String> tokens = new ArrayList<>();
        public BranchRuntime branchDefaults = new BranchRuntime();
        public BranchSelector branches = new BranchSelector();
        public List<String> sourceRoots = new ArrayList<>();
        public List<String> resourceRoots = new ArrayList<>();
        public List<String> facadePackages = new ArrayList<>();
        public List<String> dependencyJars = new ArrayList<>();
        public Map<String, BranchOverride> branchOverrides = new LinkedHashMap<>();

        public EffectiveBranch effective(String branch) {
            EffectiveBranch out = new EffectiveBranch();
            out.directUrl = branchDefaults.directUrl;
            out.uniqueId = branchDefaults.uniqueId;
            out.version = branchDefaults.version;
            out.targetAppName = branchDefaults.targetAppName;
            out.springProfiles = new ArrayList<>(branchDefaults.springProfiles);
            out.sourceRoots = new ArrayList<>(sourceRoots);
            out.resourceRoots = new ArrayList<>(resourceRoots);
            out.facadePackages = new ArrayList<>(facadePackages);
            out.dependencyJars = new ArrayList<>(dependencyJars);
            for (Map.Entry<String, BranchOverride> entry : branchOverrides.entrySet()) {
                String pattern = entry.getKey();
                if (isGlob(pattern) && branchMatches(pattern, branch)) {
                    applyOverride(out, entry.getValue());
                }
            }
            BranchOverride override = branchOverrides.get(branch);
            if (override != null) {
                applyOverride(out, override);
            }
            return out;
        }

        private void applyOverride(EffectiveBranch out, BranchOverride override) {
            if (notBlank(override.directUrl)) out.directUrl = override.directUrl;
            if (notBlank(override.uniqueId)) out.uniqueId = override.uniqueId;
            if (notBlank(override.version)) out.version = override.version;
            if (notBlank(override.targetAppName)) out.targetAppName = override.targetAppName;
            if (!override.springProfiles.isEmpty()) out.springProfiles = new ArrayList<>(override.springProfiles);
            if (!override.sourceRoots.isEmpty()) out.sourceRoots = new ArrayList<>(override.sourceRoots);
            if (!override.resourceRoots.isEmpty()) out.resourceRoots = new ArrayList<>(override.resourceRoots);
            if (!override.facadePackages.isEmpty()) out.facadePackages = new ArrayList<>(override.facadePackages);
            if (!override.dependencyJars.isEmpty()) out.dependencyJars = new ArrayList<>(override.dependencyJars);
        }
    }

    public static class BranchSelector {
        public List<String> include = new ArrayList<>();
        public List<String> exclude = new ArrayList<>();
        public int maxMatched = 20;
    }

    public static class BranchRuntime {
        public String directUrl;
        public String uniqueId;
        public String version;
        public String targetAppName;
        public List<String> springProfiles = new ArrayList<>();
    }

    public static class BranchOverride extends BranchRuntime {
        public List<String> sourceRoots = new ArrayList<>();
        public List<String> resourceRoots = new ArrayList<>();
        public List<String> facadePackages = new ArrayList<>();
        public List<String> dependencyJars = new ArrayList<>();
    }

    public static class EffectiveBranch extends BranchRuntime {
        public List<String> sourceRoots = new ArrayList<>();
        public List<String> resourceRoots = new ArrayList<>();
        public List<String> facadePackages = new ArrayList<>();
        public List<String> dependencyJars = new ArrayList<>();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isGlob(String value) {
        return value != null && (value.indexOf('*') >= 0 || value.indexOf('?') >= 0);
    }

    private static boolean branchMatches(String glob, String branch) {
        if (branch == null) return false;
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString()).matcher(branch).matches();
    }
}
