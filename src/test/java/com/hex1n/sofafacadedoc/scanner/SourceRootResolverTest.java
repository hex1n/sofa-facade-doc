package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRootResolverTest {
    @TempDir
    Path root;

    @Test
    void resolvesConfiguredRootsAndNames() throws Exception {
        Files.createDirectories(root.resolve("facade/src/main/java"));
        Files.createDirectories(root.resolve("service/src/main/resources"));

        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots = Arrays.asList("facade/src/main/java");
        cfg.resourceRoots = Arrays.asList("service/src/main/resources");

        SourceRootResolver.ResolvedRoots roots = new SourceRootResolver().resolve(root, cfg);

        assertEquals(Arrays.asList(root.resolve("facade/src/main/java").normalize()), roots.sourceRoots);
        assertEquals(Arrays.asList(root.resolve("service/src/main/resources").normalize()), roots.resourceRoots);
        assertEquals(Arrays.asList("facade/src/main/java"), roots.sourceRootNames);
        assertEquals(Arrays.asList("service/src/main/resources"), roots.resourceRootNames);
    }

    @Test
    void discoversConventionRootsWhenUnconfigured() throws Exception {
        Files.createDirectories(root.resolve("facade/src/main/java"));
        Files.createDirectories(root.resolve("facade/src/test/java"));
        Files.createDirectories(root.resolve("service/src/main/resources"));
        Files.createDirectories(root.resolve("target/generated-sources"));

        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();

        SourceRootResolver.ResolvedRoots roots = new SourceRootResolver().resolve(root, cfg);

        assertTrue(roots.sourceRootNames.contains("facade/src/main/java"));
        assertTrue(roots.resourceRootNames.contains("service/src/main/resources"));
        assertEquals(1, roots.sourceRootNames.size());
        assertEquals(1, roots.resourceRootNames.size());
    }

    @Test
    void rejectsMissingConfiguredRoot() {
        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots = Arrays.asList("missing/src/main/java");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SourceRootResolver().resolve(root, cfg));

        assertEquals("source root does not exist: missing/src/main/java", error.getMessage());
    }
}
