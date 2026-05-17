package com.hex1n.sofafacadedoc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAuthorizationTest {
    private final ConfigurationAuthorization authorization = new ConfigurationAuthorization(new ObjectMapper());

    @Test
    void teamTokenCreatesOwnedProjectWithRepoAndTokens() {
        AppConfig cfg = config();
        AuthScope scope = teamScope("loan-team");

        authorization.applyProjectEdits(cfg, scope, Collections.singletonList(projectPayload("loan-extra", "loan-team", "git://loan-extra", "loan-extra-token")));

        AppConfig.ProjectConfig created = cfg.projects.get("loan-extra");
        assertEquals("loan-team", created.team);
        assertEquals("git://loan-extra", created.repo);
        assertEquals(Collections.singletonList("loan-extra-token"), created.tokens);
    }

    @Test
    void teamTokenCannotChangeRepoOrTokensAfterCreation() {
        AppConfig cfg = config();
        AuthScope scope = teamScope("loan-team");

        Map<String, Object> changedRepo = projectPayload("loan", "loan-team", "git://other", "loan-token");
        assertThrows(ConfigurationAuthorization.Forbidden.class, () -> authorization.applyProjectEdits(cfg, scope, Collections.singletonList(changedRepo)));
        assertEquals("git://loan", cfg.projects.get("loan").repo);

        Map<String, Object> changedTokens = projectPayload("loan", "loan-team", "git://loan", "other-token");
        assertThrows(ConfigurationAuthorization.Forbidden.class, () -> authorization.applyProjectEdits(cfg, scope, Collections.singletonList(changedTokens)));
        assertEquals(Collections.singletonList("loan-token"), cfg.projects.get("loan").tokens);
    }

    @Test
    void teamTokenUpdatesNonPermissionProjectConfigurationAfterCreation() {
        AppConfig cfg = config();
        AuthScope scope = teamScope("loan-team");
        Map<String, Object> changed = projectPayload("loan", "loan-team", "git://loan", "loan-token");
        changed.put("displayName", "贷款查询");
        changed.put("baselineBranch", "develop");
        changed.put("sourceRoots", Collections.singletonList("api/src/main/java"));

        authorization.applyProjectEdits(cfg, scope, Collections.singletonList(changed));

        AppConfig.ProjectConfig project = cfg.projects.get("loan");
        assertEquals("贷款查询", project.displayName);
        assertEquals("develop", project.baselineBranch);
        assertEquals(Collections.singletonList("api/src/main/java"), project.sourceRoots);
        assertEquals("git://loan", project.repo);
        assertEquals(Collections.singletonList("loan-token"), project.tokens);
    }

    @Test
    void adminTokenCanChangeOwnershipRepoAndTokens() {
        AppConfig cfg = config();
        AuthScope scope = new AuthScope();
        scope.admin = true;
        Map<String, Object> changed = projectPayload("loan", "card-team", "git://loan-v2", "loan-v2-token");

        authorization.applyProjectEdits(cfg, scope, Collections.singletonList(changed));

        AppConfig.ProjectConfig project = cfg.projects.get("loan");
        assertEquals("card-team", project.team);
        assertEquals("git://loan-v2", project.repo);
        assertEquals(Collections.singletonList("loan-v2-token"), project.tokens);
    }

    private AppConfig config() {
        AppConfig cfg = new AppConfig();
        cfg.auth.adminTokens.add("admin-token");
        cfg.teams.put("loan-team", team("贷款团队", "loan-team-token"));
        cfg.teams.put("card-team", team("卡团队", "card-team-token"));

        AppConfig.ProjectConfig loan = new AppConfig.ProjectConfig();
        loan.team = "loan-team";
        loan.displayName = "贷款服务";
        loan.repo = "git://loan";
        loan.baselineBranch = "main";
        loan.tokens = Collections.singletonList("loan-token");
        loan.sourceRoots = Collections.singletonList("facade/src/main/java");
        loan.resourceRoots = Collections.singletonList("service/src/main/resources");
        loan.facadePackages = Collections.singletonList("com.company.loan.facade");
        cfg.projects.put("loan", loan);
        cfg.applyDefaults();
        return cfg;
    }

    private AppConfig.TeamConfig team(String displayName, String token) {
        AppConfig.TeamConfig team = new AppConfig.TeamConfig();
        team.displayName = displayName;
        team.tokens = Collections.singletonList(token);
        return team;
    }

    private AuthScope teamScope(String team) {
        AuthScope scope = new AuthScope();
        scope.teams.add(team);
        return scope;
    }

    private Map<String, Object> projectPayload(String id, String team, String repo, String token) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("team", team);
        item.put("displayName", id);
        item.put("repo", repo);
        item.put("baselineBranch", "main");
        item.put("tokens", Collections.singletonList(token));

        Map<String, Object> branchDefaults = new LinkedHashMap<>();
        branchDefaults.put("directUrl", "bolt://127.0.0.1:12200");
        branchDefaults.put("springProfiles", Collections.singletonList("test"));
        item.put("branchDefaults", branchDefaults);

        Map<String, Object> branches = new LinkedHashMap<>();
        branches.put("include", Arrays.asList("main", "feature/*"));
        branches.put("exclude", Collections.emptyList());
        branches.put("maxMatched", 20);
        item.put("branches", branches);

        item.put("sourceRoots", Collections.singletonList("facade/src/main/java"));
        item.put("resourceRoots", Collections.singletonList("service/src/main/resources"));
        item.put("facadePackages", Collections.singletonList("com.company.loan.facade"));
        item.put("branchOverrides", Collections.emptyMap());
        return item;
    }
}
