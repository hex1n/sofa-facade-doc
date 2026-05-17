package com.hex1n.sofafacadedoc.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigDependencyJarsTest {

    @Test
    void applyDefaultsPadsMissingDependencyJarsToEmptyList() {
        AppConfig cfg = baseConfig();
        AppConfig.ProjectConfig loan = cfg.projects.get("loan");
        loan.dependencyJars = null;
        AppConfig.BranchOverride override = new AppConfig.BranchOverride();
        override.dependencyJars = null;
        loan.branchOverrides.put("feature/*", override);

        cfg.applyDefaults();

        assertNotNull(loan.dependencyJars);
        assertTrue(loan.dependencyJars.isEmpty());
        assertNotNull(loan.branchOverrides.get("feature/*").dependencyJars);
        assertTrue(loan.branchOverrides.get("feature/*").dependencyJars.isEmpty());
    }

    @Test
    void effectiveBranchInheritsProjectLevelDependencyJars() {
        AppConfig cfg = baseConfig();
        AppConfig.ProjectConfig loan = cfg.projects.get("loan");
        loan.dependencyJars = Arrays.asList("facade-api/target/*.jar", "lib/external-dto-*.jar");

        AppConfig.EffectiveBranch effective = loan.effective("develop");

        assertEquals(Arrays.asList("facade-api/target/*.jar", "lib/external-dto-*.jar"), effective.dependencyJars);
    }

    @Test
    void branchOverrideReplacesDependencyJarsWhenNonEmpty() {
        AppConfig cfg = baseConfig();
        AppConfig.ProjectConfig loan = cfg.projects.get("loan");
        loan.dependencyJars = Collections.singletonList("lib/main.jar");

        AppConfig.BranchOverride override = new AppConfig.BranchOverride();
        override.dependencyJars = Collections.singletonList("lib/feature.jar");
        loan.branchOverrides.put("feature/*", override);
        cfg.applyDefaults();

        AppConfig.EffectiveBranch effective = loan.effective("feature/payment");

        assertEquals(Collections.singletonList("lib/feature.jar"), effective.dependencyJars);
    }

    @Test
    void emptyBranchOverrideKeepsProjectLevelDependencyJars() {
        AppConfig cfg = baseConfig();
        AppConfig.ProjectConfig loan = cfg.projects.get("loan");
        loan.dependencyJars = Collections.singletonList("lib/main.jar");

        AppConfig.BranchOverride override = new AppConfig.BranchOverride();
        override.directUrl = "bolt://10.0.0.1:12200";
        loan.branchOverrides.put("feature/*", override);
        cfg.applyDefaults();

        AppConfig.EffectiveBranch effective = loan.effective("feature/payment");

        assertEquals(Collections.singletonList("lib/main.jar"), effective.dependencyJars);
        assertEquals("bolt://10.0.0.1:12200", effective.directUrl);
    }

    @Test
    void yamlRoundTripPreservesDependencyJars(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("config.yml");
        Files.write(file, ("server:\n" +
                "  listen: 127.0.0.1:8080\n" +
                "  dataDir: data\n" +
                "auth:\n" +
                "  adminTokens:\n" +
                "    - admin-token\n" +
                "projects:\n" +
                "  loan:\n" +
                "    repo: git://loan\n" +
                "    tokens:\n" +
                "      - loan-token\n" +
                "    dependencyJars:\n" +
                "      - facade-api/target/*.jar\n" +
                "      - lib/external-dto.jar\n" +
                "    branchOverrides:\n" +
                "      feature/*:\n" +
                "        dependencyJars:\n" +
                "          - lib/feature.jar\n").getBytes());

        AppConfigLoader loader = new AppConfigLoader(file.toString());
        AppConfig cfg = loader.current();
        AppConfig.ProjectConfig loan = cfg.projects.get("loan");

        assertEquals(Arrays.asList("facade-api/target/*.jar", "lib/external-dto.jar"), loan.dependencyJars);
        assertEquals(Collections.singletonList("lib/feature.jar"), loan.branchOverrides.get("feature/*").dependencyJars);

        String dumped = loader.toYaml(cfg);
        Yaml yaml = new Yaml();
        AppConfig roundTrip = yaml.loadAs(dumped, AppConfig.class);
        roundTrip.applyDefaults();
        AppConfig.ProjectConfig loanAgain = roundTrip.projects.get("loan");
        assertEquals(Arrays.asList("facade-api/target/*.jar", "lib/external-dto.jar"), loanAgain.dependencyJars);
        assertEquals(Collections.singletonList("lib/feature.jar"), loanAgain.branchOverrides.get("feature/*").dependencyJars);
    }

    private AppConfig baseConfig() {
        AppConfig cfg = new AppConfig();
        cfg.auth.adminTokens.add("admin-token");
        AppConfig.ProjectConfig loan = new AppConfig.ProjectConfig();
        loan.repo = "git://loan";
        loan.tokens = new java.util.ArrayList<>(Collections.singletonList("loan-token"));
        loan.baselineBranch = "develop";
        loan.sourceRoots = new java.util.ArrayList<>(Collections.singletonList("facade/src/main/java"));
        cfg.projects.put("loan", loan);
        cfg.applyDefaults();
        return cfg;
    }
}
