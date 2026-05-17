package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.config.AppConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Component
public class SourceRootResolver {
    public ResolvedRoots resolve(Path root, AppConfig.EffectiveBranch cfg) throws Exception {
        List<Path> sourceRoots = resolveRoots(root, cfg.sourceRoots, "src/main/java");
        List<Path> resourceRoots = resolveRoots(root, cfg.resourceRoots, "src/main/resources");
        return new ResolvedRoots(
                sourceRoots,
                resourceRoots,
                relativize(root, sourceRoots),
                relativize(root, resourceRoots)
        );
    }

    private List<Path> resolveRoots(Path root, List<String> configured, String suffix) throws Exception {
        List<Path> roots = new ArrayList<>();
        if (configured != null && !configured.isEmpty()) {
            for (String item : configured) {
                Path p = root.resolve(item).normalize();
                if (!Files.isDirectory(p)) throw new IllegalArgumentException("source root does not exist: " + item);
                roots.add(p);
            }
            return roots;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                String s = slash(root.relativize(p).toString());
                if (s.endsWith(suffix)) roots.add(p);
            });
        }
        return roots;
    }

    private List<String> relativize(Path root, List<Path> paths) {
        List<String> out = new ArrayList<>();
        for (Path p : paths) out.add(slash(root.relativize(p).toString()));
        return out;
    }

    private String slash(String s) {
        return s.replace(File.separatorChar, '/');
    }

    public static class ResolvedRoots {
        public final List<Path> sourceRoots;
        public final List<Path> resourceRoots;
        public final List<String> sourceRootNames;
        public final List<String> resourceRootNames;

        ResolvedRoots(List<Path> sourceRoots, List<Path> resourceRoots, List<String> sourceRootNames, List<String> resourceRootNames) {
            this.sourceRoots = Collections.unmodifiableList(new ArrayList<>(sourceRoots));
            this.resourceRoots = Collections.unmodifiableList(new ArrayList<>(resourceRoots));
            this.sourceRootNames = Collections.unmodifiableList(new ArrayList<>(sourceRootNames));
            this.resourceRootNames = Collections.unmodifiableList(new ArrayList<>(resourceRootNames));
        }
    }
}
