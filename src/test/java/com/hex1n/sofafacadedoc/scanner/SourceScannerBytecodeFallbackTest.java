package com.hex1n.sofafacadedoc.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SourceScannerBytecodeFallbackTest {

    @BeforeAll
    static void requiresJdk() {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "JavaCompiler unavailable, skipping (need JDK)");
    }

    @Test
    void facadeMethodWhoseParamLivesInJarIsExpandedNotSourceMissing(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("facade/src/main/java/com/sample/facade"));
        Files.write(repo.resolve("facade/src/main/java/com/sample/facade/OrderFacade.java"),
                ("package com.sample.facade;\n" +
                        "import com.external.dto.ExternalRequest;\n" +
                        "public interface OrderFacade {\n" +
                        "    String process(ExternalRequest req);\n" +
                        "}\n").getBytes(StandardCharsets.UTF_8));

        Path libDir = Files.createDirectories(repo.resolve("lib"));
        Path jar = compileToJar(libDir.resolve("external-dto.jar"), externalDtoSources());

        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots = Collections.singletonList("facade/src/main/java");
        cfg.facadePackages = Collections.singletonList("com.sample.facade");
        cfg.dependencyJars = Collections.singletonList("lib/" + jar.getFileName().toString());

        SourceScanner scanner = scanner();
        SourceScanner.ScanOutput out = scanner.scan("sample", "main", "abc123", repo, cfg);

        DocumentModel.ServiceDoc svc = out.document.services.stream()
                .filter(s -> "com.sample.facade.OrderFacade".equals(s.fqn)).findFirst().orElseThrow(AssertionError::new);
        DocumentModel.MethodDoc method = svc.methods.stream()
                .filter(m -> "process".equals(m.name)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, method.params.size());
        DocumentModel.FieldNode reqTree = method.params.get(0).tree;

        assertNull(reqTree.note, "ExternalRequest should be expanded from jar, not flagged sourceMissing");
        Map<String, DocumentModel.FieldNode> children = childrenByName(reqTree);
        assertTrue(children.containsKey("id"), "id field must come from bytecode");
        assertEquals("是", children.get("id").required);
        assertTrue(children.get("id").constraints.contains("NotNull"));
        assertTrue(children.containsKey("status"));
        DocumentModel.FieldNode statusNode = children.get("status");
        assertEquals("enum string", statusNode.jsonType);
        List<String> enumNames = statusNode.enumValues.stream().map(v -> v.name).collect(Collectors.toList());
        assertEquals(Arrays.asList("OPEN", "CLOSED"), enumNames);

        assertFalse(out.document.diagnostics.messages.stream().anyMatch(m -> m.startsWith("dependencyJars unresolved")));
    }

    @Test
    void facadeReferencingDtoNotInJarsStillMarksSourceMissing(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("facade/src/main/java/com/sample/facade"));
        Files.write(repo.resolve("facade/src/main/java/com/sample/facade/OrderFacade.java"),
                ("package com.sample.facade;\n" +
                        "import com.absent.dto.GhostRequest;\n" +
                        "public interface OrderFacade {\n" +
                        "    String process(GhostRequest req);\n" +
                        "}\n").getBytes(StandardCharsets.UTF_8));

        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots = Collections.singletonList("facade/src/main/java");
        cfg.facadePackages = Collections.singletonList("com.sample.facade");
        cfg.dependencyJars = Collections.singletonList("lib/nothing-here.jar");

        SourceScanner scanner = scanner();
        SourceScanner.ScanOutput out = scanner.scan("sample", "main", "abc123", repo, cfg);

        DocumentModel.MethodDoc method = out.document.services.get(0).methods.stream()
                .filter(m -> "process".equals(m.name)).findFirst().orElseThrow(AssertionError::new);
        DocumentModel.FieldNode reqTree = method.params.get(0).tree;
        assertNotNull(reqTree.note);
        assertTrue(reqTree.note.startsWith("sourceMissing:"));
        assertTrue(out.document.diagnostics.messages.stream().anyMatch(m -> m.startsWith("dependencyJars unresolved")));
    }

    private static Map<String, DocumentModel.FieldNode> childrenByName(DocumentModel.FieldNode parent) {
        Map<String, DocumentModel.FieldNode> out = new LinkedHashMap<>();
        for (DocumentModel.FieldNode c : parent.children) out.put(c.name, c);
        return out;
    }

    private static Map<String, String> externalDtoSources() {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("com.external.dto.NotNull",
                "package com.external.dto; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface NotNull {}");
        src.put("com.external.dto.Status",
                "package com.external.dto; public enum Status { OPEN, CLOSED }");
        src.put("com.external.dto.ExternalRequest",
                "package com.external.dto;" +
                        "public class ExternalRequest {" +
                        "  @NotNull public String id;" +
                        "  public Status status;" +
                        "}");
        return src;
    }

    private static Path compileToJar(Path target, Map<String, String> sources) throws Exception {
        Path classesDir = Files.createTempDirectory("scanner-jar-classes-");
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
            fm.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(classesDir.toFile()));
            List<JavaFileObject> units = new ArrayList<>();
            for (Map.Entry<String, String> e : sources.entrySet()) units.add(inMemory(e.getKey(), e.getValue()));
            if (!compiler.getTask(null, fm, null, null, null, units).call()) {
                throw new IllegalStateException("fixture compilation failed");
            }
            fm.close();
            Files.createDirectories(target.getParent());
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target));
                 Stream<Path> walk = Files.walk(classesDir)) {
                for (Path p : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    String rel = classesDir.relativize(p).toString().replace('\\', '/');
                    out.putNextEntry(new JarEntry(rel));
                    Files.copy(p, out);
                    out.closeEntry();
                }
            }
            return target;
        } finally {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    private static JavaFileObject inMemory(String fqn, String src) {
        return new SimpleJavaFileObject(URI.create("string:///" + fqn.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return src;
            }
        };
    }

    private static SourceScanner scanner() {
        JavaAnnotationReader annotations = new JavaAnnotationReader();
        JavaTypeResolver types = new JavaTypeResolver();
        PayloadFieldRules payloadRules = new PayloadFieldRules(annotations);
        JavaCommentReader comments = new JavaCommentReader(annotations);
        SofaAnnotationPublishParser sofaAnnotations = new SofaAnnotationPublishParser(annotations, types);
        JavaSourceIndexer sourceIndexer = new JavaSourceIndexer(payloadRules, comments, types, sofaAnnotations);
        FacadePayloadTreeBuilder payloadTreeBuilder = new FacadePayloadTreeBuilder(payloadRules, types, new DtoBytecodeFallback());
        FacadeDocumentAssembler assembler = new FacadeDocumentAssembler(payloadTreeBuilder);
        DocumentStructureHasher hasher = new DocumentStructureHasher(new ObjectMapper());
        return new SourceScanner(new SourceRootResolver(), sourceIndexer, new SofaXmlPublishParser(), assembler, hasher);
    }
}
