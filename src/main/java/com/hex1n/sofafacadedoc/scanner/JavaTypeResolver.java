package com.hex1n.sofafacadedoc.scanner;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JavaTypeResolver {
    public String resolve(String raw, String pkg, Map<String, String> imports, JavaSourceIndex index, Set<String> methodTypeParams) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.endsWith("[]")) return "java.util.List<" + resolve(raw.substring(0, raw.length() - 2), pkg, imports, index, methodTypeParams) + ">";
        int angle = raw.indexOf('<');
        if (angle > 0 && raw.endsWith(">")) {
            String base = resolve(raw.substring(0, angle), pkg, imports, index, methodTypeParams);
            String inner = raw.substring(angle + 1, raw.length() - 1);
            List<String> args = new ArrayList<>();
            for (String part : splitTopLevel(inner)) args.add(resolve(part, pkg, imports, index, methodTypeParams));
            return base + "<" + String.join(", ", args) + ">";
        }
        if (methodTypeParams != null && methodTypeParams.contains(raw)) return raw;
        if (raw.contains(".") || primitive(raw)) return raw;
        if (imports.containsKey(raw)) return imports.get(raw);
        if (javaLang(raw)) return "java.lang." + raw;
        if ("BigDecimal".equals(raw) || "BigInteger".equals(raw)) return "java.math." + raw;
        if ("List".equals(raw) || "Set".equals(raw) || "Map".equals(raw) || "Collection".equals(raw)) return "java.util." + raw;
        if ("Date".equals(raw)) return "java.util.Date";
        if ("LocalDate".equals(raw) || "LocalDateTime".equals(raw) || "LocalTime".equals(raw)) return "java.time." + raw;
        if (index.simpleNames.containsKey(raw)) return index.simpleNames.get(raw);
        String wildcard = wildcardImport(raw, imports);
        if (wildcard != null) return wildcard;
        return pkg == null || pkg.isEmpty() ? raw : pkg + "." + raw;
    }

    private String wildcardImport(String raw, Map<String, String> imports) {
        for (String value : imports.values()) {
            if (value != null && value.endsWith(".*")) {
                return value.substring(0, value.length() - 1) + raw;
            }
        }
        return null;
    }

    public String substituteType(String raw, Map<String, String> bindings) {
        if (raw == null || bindings.isEmpty()) return raw;
        return substitute(JavaTypeSpec.parse(raw), bindings).toString();
    }

    public String jsonType(JavaSourceIndex index, String type) {
        JavaTypeSpec spec = JavaTypeSpec.parse(type);
        if ("boolean".equals(spec.base) || "java.lang.Boolean".equals(spec.base)) return "boolean";
        if (primitive(spec.base) || spec.base.matches("java\\.lang\\.(Long|Integer|Double|Float|Short|Byte)")) return "number";
        if ("java.math.BigDecimal".equals(spec.base) || "java.math.BigInteger".equals(spec.base)) return "string decimal";
        if (spec.base.startsWith("java.time.") || "java.util.Date".equals(spec.base) || "java.sql.Date".equals(spec.base)) return "string date/time";
        if ("java.util.List".equals(spec.base) || "java.util.Set".equals(spec.base)) return "array";
        if ("java.util.Map".equals(spec.base)) return "object";
        JavaSourceIndex.JavaClassInfo cls = index.classes.get(spec.base);
        if (cls != null && "enum".equals(cls.kind)) return "enum string";
        if ("java.lang.String".equals(spec.base) || "java.lang.Object".equals(spec.base)) return "string";
        return "object";
    }

    public boolean primitive(String s) {
        return Arrays.asList("byte", "short", "int", "long", "float", "double", "boolean", "char", "void").contains(s);
    }

    private JavaTypeSpec substitute(JavaTypeSpec spec, Map<String, String> bindings) {
        if (bindings.containsKey(spec.base) && spec.args.isEmpty()) return JavaTypeSpec.parse(bindings.get(spec.base));
        List<JavaTypeSpec> next = new ArrayList<>();
        for (JavaTypeSpec arg : spec.args) next.add(substitute(arg, bindings));
        spec.args = next;
        return spec;
    }

    private boolean javaLang(String s) {
        return Arrays.asList(
                "String", "Object", "Long", "Integer", "Boolean", "Double", "Float", "Short", "Byte", "Character", "Void",
                "Throwable", "Exception", "RuntimeException", "IllegalArgumentException", "IllegalStateException",
                "NullPointerException", "UnsupportedOperationException", "Enum"
        ).contains(s);
    }

    private List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(s.substring(start).trim());
        return out;
    }
}
