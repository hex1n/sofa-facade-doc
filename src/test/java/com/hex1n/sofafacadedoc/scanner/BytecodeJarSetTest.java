package com.hex1n.sofafacadedoc.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeJarSetTest {

    @Test
    void resolvesSimpleGlobAndIndexesEntries(@TempDir Path repo) throws Exception {
        Path libDir = Files.createDirectories(repo.resolve("lib"));
        writeJar(libDir.resolve("dto.jar"),
                entry("com/foo/A.class", "AAA"),
                entry("com/foo/B.class", "BBB"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList("lib/*.jar"))) {
            assertEquals(2, set.indexedClassCount());
            assertTrue(set.contains("com/foo/A.class"));
            assertEquals(Arrays.asList("AAA"), bytesAsList(set.readClass("com/foo/A.class")));
            assertTrue(set.unresolvedPatterns().isEmpty());
            assertTrue(set.conflicts().isEmpty());
        }
    }

    @Test
    void recursiveGlobWalksSubdirectories(@TempDir Path repo) throws Exception {
        Path nested = Files.createDirectories(repo.resolve("facade-api/target/sub"));
        writeJar(nested.resolve("api.jar"), entry("com/x/Y.class", "ZZZ"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList("facade-api/target/**/*.jar"))) {
            assertEquals(1, set.indexedClassCount());
            assertEquals(Arrays.asList("ZZZ"), bytesAsList(set.readClass("com/x/Y.class")));
            assertTrue(set.locate("com/x/Y.class").startsWith("jar:facade-api/target/sub/api.jar!"));
        }
    }

    @Test
    void missingPatternIsRecordedNotThrown(@TempDir Path repo) throws Exception {
        Path libDir = Files.createDirectories(repo.resolve("lib"));
        writeJar(libDir.resolve("present.jar"), entry("ok/Cls.class", "X"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Arrays.asList("lib/*.jar", "missing/**/*.jar", "nothere.jar"))) {
            assertEquals(1, set.indexedClassCount());
            assertEquals(2, set.unresolvedPatterns().size());
            assertTrue(set.unresolvedPatterns().contains("missing/**/*.jar"));
            assertTrue(set.unresolvedPatterns().contains("nothere.jar"));
        }
    }

    @Test
    void firstJarWinsOnDuplicateAndConflictRecorded(@TempDir Path repo) throws Exception {
        Path libDir = Files.createDirectories(repo.resolve("lib"));
        writeJar(libDir.resolve("a.jar"), entry("dup/Same.class", "FROM_A"));
        writeJar(libDir.resolve("b.jar"), entry("dup/Same.class", "FROM_B"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList("lib/*.jar"))) {
            assertEquals(1, set.indexedClassCount());
            assertEquals(Arrays.asList("FROM_A"), bytesAsList(set.readClass("dup/Same.class")));
            assertEquals(1, set.conflicts().size());
            assertTrue(set.conflicts().get(0).startsWith("dup/Same.class"));
        }
    }

    @Test
    void readMissingClassReturnsEmpty(@TempDir Path repo) throws Exception {
        Path libDir = Files.createDirectories(repo.resolve("lib"));
        writeJar(libDir.resolve("dto.jar"), entry("present/X.class", "ok"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList("lib/*.jar"))) {
            assertEquals(Optional.empty(), set.readClass("absent/Y.class"));
            assertNull(set.locate("absent/Y.class"));
        }
    }

    @Test
    void skipsMetaInfAndModuleInfo(@TempDir Path repo) throws Exception {
        Path libDir = Files.createDirectories(repo.resolve("lib"));
        writeJar(libDir.resolve("dto.jar"),
                entry("META-INF/services/foo.Bar", "svc"),
                entry("META-INF/versions/9/com/foo/A.class", "v9"),
                entry("module-info.class", "module"),
                entry("real/Dto.class", "real"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList("lib/*.jar"))) {
            assertEquals(1, set.indexedClassCount());
            assertTrue(set.contains("real/Dto.class"));
            assertFalse(set.contains("module-info.class"));
        }
    }

    @Test
    void absolutePathWithoutGlobIsAccepted(@TempDir Path repo, @TempDir Path other) throws Exception {
        Path jar = other.resolve("absolute.jar");
        writeJar(jar, entry("abs/Cls.class", "ABS"));

        try (BytecodeJarSet set = new BytecodeJarSet(repo, Collections.singletonList(jar.toString()))) {
            assertEquals(1, set.indexedClassCount());
            assertEquals(Arrays.asList("ABS"), bytesAsList(set.readClass("abs/Cls.class")));
        }
    }

    @Test
    void emptyOrNullPatternsBuildEmptySet(@TempDir Path repo) throws Exception {
        try (BytecodeJarSet set = new BytecodeJarSet(repo, Arrays.asList("", null, "   "))) {
            assertEquals(0, set.indexedClassCount());
            assertTrue(set.unresolvedPatterns().isEmpty());
        }
        try (BytecodeJarSet set = new BytecodeJarSet(repo, null)) {
            assertEquals(0, set.indexedClassCount());
        }
    }

    private static Entry entry(String name, String content) {
        return new Entry(name, content.getBytes());
    }

    private static void writeJar(Path target, Entry... entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
            for (Entry e : entries) {
                JarEntry je = new JarEntry(e.name);
                out.putNextEntry(je);
                out.write(e.bytes);
                out.closeEntry();
            }
        }
    }

    private static List<String> bytesAsList(Optional<byte[]> opt) {
        return opt.map(bytes -> Collections.singletonList(new String(bytes))).orElse(Collections.emptyList());
    }

    private static class Entry {
        final String name;
        final byte[] bytes;

        Entry(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
