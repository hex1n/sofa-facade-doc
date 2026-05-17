package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DtoBytecodeFallbackTest {

    private final DtoBytecodeFallback fallback = new DtoBytecodeFallback();

    @BeforeAll
    static void requiresJdk() {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "JavaCompiler unavailable, skipping (need JDK)");
    }

    @Test
    void extractsScalarFieldsAndCommonAnnotations(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);

            Optional<JavaSourceIndex.JavaClassInfo> resolved = fallback.resolve("com.fixture.OrderRequest", index, jars);

            assertTrue(resolved.isPresent());
            JavaSourceIndex.JavaClassInfo cls = resolved.get();
            assertEquals("class", cls.kind);
            assertEquals("com.fixture.OrderRequest", cls.fqn);
            assertEquals("OrderRequest", cls.name);
            assertTrue(cls.sourcePath.startsWith("jar:") && cls.sourcePath.endsWith("!com/fixture/OrderRequest.class"));
            assertSame(cls, index.classes.get("com.fixture.OrderRequest"));

            Map<String, JavaSourceIndex.JavaFieldInfo> byName = fieldsByName(cls);
            assertEquals(Arrays.asList("id", "name", "items", "extra", "renamed", "amount"), new ArrayList<>(byName.keySet()));

            assertEquals("java.lang.String", byName.get("id").type);
            assertEquals("是", byName.get("id").required);
            assertTrue(byName.get("id").constraints.contains("NotBlank"));

            assertEquals("是", byName.get("name").required);
            assertTrue(byName.get("name").constraints.contains("NotNull"));
            assertTrue(byName.get("name").constraints.stream().anyMatch(s -> s.startsWith("Size(") && s.contains("min=1")));

            assertEquals("java.util.List<java.lang.String>", byName.get("items").type);
            assertTrue(byName.get("items").constraints.contains("NotEmpty"));

            assertEquals("java.util.Map<java.lang.String,java.lang.Integer>", byName.get("extra").type);

            assertEquals("biz_name", byName.get("renamed").jsonName);
            assertTrue(byName.get("renamed").constraints.stream().anyMatch(s -> s.equals("JsonProperty(value=biz_name)")));

            assertEquals("java.math.BigDecimal", byName.get("amount").type);
            assertTrue(byName.get("amount").constraints.stream().anyMatch(s -> s.startsWith("DecimalMin(value=")));
        }
    }

    @Test
    void skipsStaticTransientSerialAndJsonIgnored(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);
            JavaSourceIndex.JavaClassInfo cls = fallback.resolve("com.fixture.OrderRequest", index, jars).orElseThrow(AssertionError::new);
            Map<String, JavaSourceIndex.JavaFieldInfo> byName = fieldsByName(cls);
            assertFalse(byName.containsKey("CONST"), "static field must be skipped");
            assertFalse(byName.containsKey("scratch"), "transient field must be skipped");
            assertFalse(byName.containsKey("serialVersionUID"), "serialVersionUID must be skipped");
            assertFalse(byName.containsKey("secret"), "@JsonIgnore field must be skipped");
        }
    }

    @Test
    void extractsEnumConstantsAndMarksKindEnum(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);
            JavaSourceIndex.JavaClassInfo cls = fallback.resolve("com.fixture.Status", index, jars).orElseThrow(AssertionError::new);
            assertEquals("enum", cls.kind);
            List<String> names = cls.enumValues.stream().map(v -> v.name).collect(Collectors.toList());
            assertEquals(Arrays.asList("ACTIVE", "INACTIVE"), names);
            for (DocumentModel.EnumValue v : cls.enumValues) assertNull(v.comment);
        }
    }

    @Test
    void capturesTypeParametersAndSuperclass(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);
            JavaSourceIndex.JavaClassInfo cls = fallback.resolve("com.fixture.PagedRequest", index, jars).orElseThrow(AssertionError::new);
            assertEquals(Collections.singletonList("T"), cls.typeParams);
            assertEquals("com.fixture.BaseRequest", cls.superclass);

            Map<String, JavaSourceIndex.JavaFieldInfo> byName = fieldsByName(cls);
            assertEquals("T", byName.get("payload").type);
        }
    }

    @Test
    void missingClassReturnsEmpty(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);
            assertEquals(Optional.empty(), fallback.resolve("com.fixture.NotThere", index, jars));
            assertFalse(index.classes.containsKey("com.fixture.NotThere"));
        }
    }

    @Test
    void platformTypesAreNotResolved(@TempDir Path tmp) throws Exception {
        Path jar = compileToJar(tmp, fixtureSource());
        try (BytecodeJarSet jars = new BytecodeJarSet(tmp, Collections.singletonList(jar.getFileName().toString()))) {
            JavaSourceIndex index = new JavaSourceIndex(tmp);
            assertEquals(Optional.empty(), fallback.resolve("java.lang.String", index, jars));
            assertEquals(Optional.empty(), fallback.resolve("javax.servlet.ServletRequest", index, jars));
        }
    }

    private static Map<String, JavaSourceIndex.JavaFieldInfo> fieldsByName(JavaSourceIndex.JavaClassInfo cls) {
        Map<String, JavaSourceIndex.JavaFieldInfo> out = new LinkedHashMap<>();
        for (JavaSourceIndex.JavaFieldInfo f : cls.fields) out.put(f.name, f);
        return out;
    }

    private static Map<String, String> fixtureSource() {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("com.fixture.NotNull",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface NotNull {}");
        src.put("com.fixture.NotBlank",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface NotBlank {}");
        src.put("com.fixture.NotEmpty",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface NotEmpty {}");
        src.put("com.fixture.Size",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface Size { int min() default 0; int max() default Integer.MAX_VALUE; }");
        src.put("com.fixture.DecimalMin",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface DecimalMin { String value(); }");
        src.put("com.fixture.JsonProperty",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface JsonProperty { String value() default \"\"; boolean required() default false; }");
        src.put("com.fixture.JsonIgnore",
                "package com.fixture; import java.lang.annotation.*;" +
                        "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) public @interface JsonIgnore { boolean value() default true; }");
        src.put("com.fixture.Status",
                "package com.fixture; public enum Status { ACTIVE, INACTIVE }");
        src.put("com.fixture.BaseRequest",
                "package com.fixture; public class BaseRequest { protected String operator; }");
        src.put("com.fixture.PagedRequest",
                "package com.fixture; public class PagedRequest<T> extends BaseRequest { public T payload; public int pageNo; }");
        src.put("com.fixture.OrderRequest",
                "package com.fixture;" +
                        "import java.io.Serializable;" +
                        "import java.math.BigDecimal;" +
                        "import java.util.List;" +
                        "import java.util.Map;" +
                        "public class OrderRequest implements Serializable {" +
                        "  private static final long serialVersionUID = 1L;" +
                        "  public static final String CONST = \"x\";" +
                        "  @NotBlank public String id;" +
                        "  @NotNull @Size(min = 1, max = 32) public String name;" +
                        "  @NotEmpty public List<String> items;" +
                        "  public Map<String, Integer> extra;" +
                        "  @JsonProperty(value = \"biz_name\") public String renamed;" +
                        "  @DecimalMin(value = \"0.01\") public BigDecimal amount;" +
                        "  @JsonIgnore public String secret;" +
                        "  public transient String scratch;" +
                        "}");
        return src;
    }

    private static Path compileToJar(Path workDir, Map<String, String> sources) throws Exception {
        Path classesDir = Files.createDirectories(workDir.resolve("classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, Collections.singleton(classesDir.toFile()));
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> e : sources.entrySet()) units.add(inMemory(e.getKey(), e.getValue()));
        boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
        if (!ok) throw new IllegalStateException("fixture compilation failed");
        fm.close();
        Path jar = workDir.resolve("fixture.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> walk = Files.walk(classesDir)) {
            for (Path p : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String rel = classesDir.relativize(p).toString().replace('\\', '/');
                out.putNextEntry(new JarEntry(rel));
                Files.copy(p, out);
                out.closeEntry();
            }
        }
        return jar;
    }

    private static JavaFileObject inMemory(String fqn, String src) {
        return new SimpleJavaFileObject(URI.create("string:///" + fqn.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return src;
            }
        };
    }
}
