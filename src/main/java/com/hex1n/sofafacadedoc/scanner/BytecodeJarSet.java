package com.hex1n.sofafacadedoc.scanner;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 给 DtoBytecodeFallback 用的轻量 jar 索引。
 *
 * 只索引"类名 → jar 路径"，按需打开 ZipFile.getInputStream 读 class 字节。
 * 同名类在多个 jar 中存在时，取首次出现的一份（按 glob 解析顺序）。
 *
 * 不是线程安全：构造期间 build 索引，使用期间只读；用完调 close。
 */
public class BytecodeJarSet implements Closeable {

    private final Path repoRoot;
    private final List<String> rawPatterns;
    private final List<Path> resolvedJars = new ArrayList<>();
    private final Map<String, Path> entryToJar = new LinkedHashMap<>();
    private final Map<Path, ZipFile> openJars = new LinkedHashMap<>();
    private final List<String> unresolvedPatterns = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();

    public BytecodeJarSet(Path repoRoot, List<String> patterns) throws IOException {
        this.repoRoot = repoRoot;
        this.rawPatterns = patterns == null ? new ArrayList<>() : new ArrayList<>(patterns);
        build();
    }

    private void build() throws IOException {
        for (String pattern : rawPatterns) {
            if (pattern == null || pattern.trim().isEmpty()) continue;
            List<Path> matches = resolveGlob(pattern.trim());
            if (matches.isEmpty()) {
                unresolvedPatterns.add(pattern.trim());
                continue;
            }
            for (Path jar : matches) {
                indexJar(jar);
            }
        }
    }

    private List<Path> resolveGlob(String pattern) throws IOException {
        Path patternPath = Paths.get(pattern);
        Path baseDir;
        String relGlob;
        if (patternPath.isAbsolute()) {
            if (!pattern.contains("*") && !pattern.contains("?")) {
                Path resolved = patternPath.normalize();
                return Files.isRegularFile(resolved) ? new ArrayList<>(java.util.Collections.singletonList(resolved)) : new ArrayList<>();
            }
            baseDir = patternPath.getRoot();
            relGlob = patternPath.subpath(0, patternPath.getNameCount()).toString();
        } else {
            if (!pattern.contains("*") && !pattern.contains("?")) {
                Path resolved = repoRoot.resolve(pattern).normalize();
                return Files.isRegularFile(resolved) ? new ArrayList<>(java.util.Collections.singletonList(resolved)) : new ArrayList<>();
            }
            baseDir = repoRoot;
            relGlob = pattern;
        }
        if (!Files.isDirectory(baseDir)) return new ArrayList<>();
        String matcherGlob = "glob:" + baseDir.toString().replace('\\', '/').replaceAll("/+$", "") + "/" + relGlob.replace('\\', '/');
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(matcherGlob);
        List<Path> hits = new ArrayList<>();
        Files.walkFileTree(baseDir, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (matcher.matches(file) && file.getFileName().toString().endsWith(".jar")) hits.add(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        hits.sort(java.util.Comparator.comparing(Path::toString));
        return hits;
    }

    private void indexJar(Path jar) {
        if (openJars.containsKey(jar)) return;
        ZipFile zip;
        try {
            zip = new ZipFile(jar.toFile());
        } catch (IOException e) {
            unresolvedPatterns.add(jar.toString() + " (open failed: " + e.getMessage() + ")");
            return;
        }
        openJars.put(jar, zip);
        resolvedJars.add(jar);
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String name = e.getName();
            if (!name.endsWith(".class")) continue;
            if (name.startsWith("META-INF/")) continue;
            if (name.equals("module-info.class")) continue;
            String existing = entryToJar.containsKey(name) ? entryToJar.get(name).toString() : null;
            if (existing != null) {
                conflicts.add(name + " in [" + existing + ", " + jar + "] (kept first)");
                continue;
            }
            entryToJar.put(name, jar);
        }
    }

    /** internal name 形如 "com/company/loan/dto/OrderRequest.class"。 */
    public Optional<byte[]> readClass(String internalName) {
        Path jar = entryToJar.get(internalName);
        if (jar == null) return Optional.empty();
        ZipFile zip = openJars.get(jar);
        if (zip == null) return Optional.empty();
        ZipEntry entry = zip.getEntry(internalName);
        if (entry == null) return Optional.empty();
        try (InputStream in = zip.getInputStream(entry)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max((int) entry.getSize(), 256));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return Optional.of(out.toByteArray());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** 命中类时给字段树打的 sourcePath：jar:<repo-rel-jar>!<internalName>。 */
    public String locate(String internalName) {
        Path jar = entryToJar.get(internalName);
        if (jar == null) return null;
        return "jar:" + relativize(jar) + "!" + internalName;
    }

    public boolean contains(String internalName) {
        return entryToJar.containsKey(internalName);
    }

    public List<Path> jars() {
        return new ArrayList<>(resolvedJars);
    }

    public List<String> unresolvedPatterns() {
        return new ArrayList<>(unresolvedPatterns);
    }

    public List<String> conflicts() {
        return new ArrayList<>(conflicts);
    }

    public int indexedClassCount() {
        return entryToJar.size();
    }

    private String relativize(Path jar) {
        try {
            return repoRoot.relativize(jar).toString().replace('\\', '/');
        } catch (Exception e) {
            return jar.toString().replace('\\', '/');
        }
    }

    @Override
    public void close() {
        for (ZipFile zip : openJars.values()) {
            try {
                zip.close();
            } catch (IOException ignored) {
            }
        }
        openJars.clear();
        entryToJar.clear();
        resolvedJars.clear();
    }
}
