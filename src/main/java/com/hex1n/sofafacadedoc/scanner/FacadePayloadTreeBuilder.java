package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FacadePayloadTreeBuilder {
    private static final int MAX_DEPTH = 5;

    private final PayloadFieldRules payloadRules;
    private final JavaTypeResolver types;
    private final DtoBytecodeFallback bytecodeFallback;

    public FacadePayloadTreeBuilder(PayloadFieldRules payloadRules, JavaTypeResolver types, DtoBytecodeFallback bytecodeFallback) {
        this.payloadRules = payloadRules;
        this.types = types;
        this.bytecodeFallback = bytecodeFallback;
    }

    public List<DocumentModel.MethodDoc> buildMethods(JavaSourceIndex index, JavaSourceIndex.JavaClassInfo cls, BytecodeJarSet jars) {
        List<DocumentModel.MethodDoc> out = new ArrayList<>();
        List<JavaSourceIndex.JavaMethodInfo> methods = collectMethods(index, cls, new LinkedHashMap<>(), new LinkedHashSet<>());
        for (JavaSourceIndex.JavaMethodInfo method : methods) out.add(buildMethod(index, method, jars));
        out.sort(Comparator.comparing((DocumentModel.MethodDoc m) -> m.name).thenComparing(m -> m.id));
        return out;
    }

    private List<JavaSourceIndex.JavaMethodInfo> collectMethods(JavaSourceIndex index, JavaSourceIndex.JavaClassInfo cls, Map<String, String> bindings, Set<String> seen) {
        if (cls == null || seen.contains(cls.fqn)) return Collections.emptyList();
        seen.add(cls.fqn);
        List<JavaSourceIndex.JavaMethodInfo> out = new ArrayList<>();
        for (JavaSourceIndex.JavaMethodInfo method : cls.methods) out.add(substitute(method, bindings));
        List<String> parents = new ArrayList<>();
        if (cls.superclass != null) parents.add(cls.superclass);
        parents.addAll(cls.interfaces);
        for (String parent : parents) {
            JavaTypeSpec spec = JavaTypeSpec.parse(parent);
            JavaSourceIndex.JavaClassInfo parentClass = index.classes.get(spec.base);
            if (parentClass == null) continue;
            Map<String, String> next = new LinkedHashMap<>(bindings);
            for (int i = 0; i < parentClass.typeParams.size() && i < spec.args.size(); i++) {
                next.put(parentClass.typeParams.get(i), spec.args.get(i).toString());
            }
            out.addAll(collectMethods(index, parentClass, next, seen));
        }
        return out;
    }

    private JavaSourceIndex.JavaMethodInfo substitute(JavaSourceIndex.JavaMethodInfo method, Map<String, String> bindings) {
        JavaSourceIndex.JavaMethodInfo out = method.copy();
        out.returnType = types.substituteType(out.returnType, bindings);
        for (JavaSourceIndex.JavaParamInfo p : out.params) p.type = types.substituteType(p.type, bindings);
        return out;
    }

    private DocumentModel.MethodDoc buildMethod(JavaSourceIndex index, JavaSourceIndex.JavaMethodInfo jm, BytecodeJarSet jars) {
        DocumentModel.MethodDoc out = new DocumentModel.MethodDoc();
        out.name = jm.name;
        out.comment = jm.comment;
        out.paramComments = jm.paramComments;
        out.returnComment = jm.returnComment;
        out.deprecated = jm.deprecated;
        out.returnType = jm.returnType;
        out.throwsTypes = jm.throwsTypes;
        out.sourcePath = jm.sourcePath;
        out.sourceLine = jm.sourceLine;
        List<String> paramTypes = new ArrayList<>();
        List<Object> examples = new ArrayList<>();
        for (int i = 0; i < jm.params.size(); i++) {
            JavaSourceIndex.JavaParamInfo p = jm.params.get(i);
            paramTypes.add(p.type);
            DocumentModel.ParamDoc pd = new DocumentModel.ParamDoc();
            pd.name = p.name == null || p.name.isEmpty() ? "arg" + i : p.name;
            pd.javaType = p.type;
            pd.jsonType = types.jsonType(index, p.type);
            pd.comment = jm.paramComments.get(pd.name);
            pd.required = payloadRules.required(pd.comment);
            pd.tree = fieldTree(index, pd.name, pd.name, p.type, pd.comment, pd.required, 0, new LinkedHashSet<>(), new LinkedHashMap<>(), jars);
            pd.example = example(pd.tree);
            out.params.add(pd);
            examples.add(pd.example);
        }
        out.id = methodId(jm.name, paramTypes);
        out.requestExample = examples.size() == 1 ? examples.get(0) : examples;
        if (jm.returnType != null && !"void".equals(jm.returnType)) {
            out.returnTree = fieldTree(index, "return", "return", jm.returnType, jm.returnComment, "未知", 0, new LinkedHashSet<>(), new LinkedHashMap<>(), jars);
            out.returnExample = example(out.returnTree);
        }
        return out;
    }

    public DocumentModel.FieldNode fieldTree(JavaSourceIndex index, String path, String name, String type, String comment, String required) {
        return fieldTree(index, path, name, type, comment, required, 0, new LinkedHashSet<>(), new LinkedHashMap<>(), null);
    }

    public DocumentModel.FieldNode fieldTree(JavaSourceIndex index, String path, String name, String type, String comment, String required, BytecodeJarSet jars) {
        return fieldTree(index, path, name, type, comment, required, 0, new LinkedHashSet<>(), new LinkedHashMap<>(), jars);
    }

    private DocumentModel.FieldNode fieldTree(JavaSourceIndex index, String path, String name, String type, String comment, String required, int depth, Set<String> seen, Map<String, String> bindings, BytecodeJarSet jars) {
        type = types.substituteType(type, bindings);
        JavaTypeSpec spec = JavaTypeSpec.parse(type);
        if (jars != null && bytecodeFallback != null && !index.classes.containsKey(spec.base) && !spec.base.startsWith("java.")) {
            bytecodeFallback.resolve(spec.base, index, jars);
        }
        DocumentModel.FieldNode node = new DocumentModel.FieldNode();
        node.path = path;
        node.name = name;
        node.javaType = type;
        node.jsonType = types.jsonType(index, type);
        node.comment = comment;
        node.required = required == null ? "未知" : required;
        if (depth >= MAX_DEPTH) {
            node.truncated = true;
            node.note = "maxDepthReached: " + type;
            return node;
        }
        if ("java.util.List".equals(spec.base) || "java.util.Set".equals(spec.base)) {
            String itemType = spec.args.isEmpty() ? "java.lang.Object" : spec.args.get(0).toString();
            node.children.add(fieldTree(index, path + ".items", "items", itemType, "", "未知", depth + 1, seen, bindings, jars));
            return node;
        }
        if ("java.util.Map".equals(spec.base)) {
            String valueType = spec.args.size() > 1 ? spec.args.get(1).toString() : "java.lang.Object";
            node.children.add(fieldTree(index, path + ".<key>", "<key>", valueType, "", "未知", depth + 1, seen, bindings, jars));
            return node;
        }
        JavaSourceIndex.JavaClassInfo cls = index.classes.get(spec.base);
        if (cls == null) {
            if ("object".equals(node.jsonType) && !spec.base.startsWith("java.")) {
                node.note = "sourceMissing: " + spec.base;
            }
            return node;
        }
        if ("enum".equals(cls.kind)) {
            node.enumValues = cls.enumValues;
            return node;
        }
        if (seen.contains(cls.fqn)) {
            node.truncated = true;
            node.note = "circularReference: " + cls.fqn;
            return node;
        }
        seen.add(cls.fqn);
        Map<String, String> next = new LinkedHashMap<>(bindings);
        for (int i = 0; i < cls.typeParams.size() && i < spec.args.size(); i++) next.put(cls.typeParams.get(i), spec.args.get(i).toString());
        for (JavaSourceIndex.JavaFieldInfo field : collectFields(index, cls, next, new LinkedHashSet<>())) {
            DocumentModel.FieldNode child = fieldTree(index, path + "." + field.name, field.name, field.type, field.comment, field.required, depth + 1, seen, next, jars);
            child.jsonName = field.jsonName;
            child.constraints = new ArrayList<>(field.constraints);
            child.sourcePath = field.sourcePath;
            child.sourceLine = field.sourceLine;
            node.children.add(child);
        }
        seen.remove(cls.fqn);
        return node;
    }

    private List<JavaSourceIndex.JavaFieldInfo> collectFields(JavaSourceIndex index, JavaSourceIndex.JavaClassInfo cls, Map<String, String> bindings, Set<String> seen) {
        if (cls == null || seen.contains(cls.fqn)) return Collections.emptyList();
        seen.add(cls.fqn);
        List<JavaSourceIndex.JavaFieldInfo> out = new ArrayList<>();
        if (cls.superclass != null) {
            JavaTypeSpec spec = JavaTypeSpec.parse(cls.superclass);
            JavaSourceIndex.JavaClassInfo parent = index.classes.get(spec.base);
            Map<String, String> next = new LinkedHashMap<>(bindings);
            if (parent != null) {
                for (int i = 0; i < parent.typeParams.size() && i < spec.args.size(); i++) next.put(parent.typeParams.get(i), spec.args.get(i).toString());
                out.addAll(collectFields(index, parent, next, seen));
            }
        }
        for (JavaSourceIndex.JavaFieldInfo f : cls.fields) {
            JavaSourceIndex.JavaFieldInfo copy = f.copy();
            copy.type = types.substituteType(copy.type, bindings);
            out.add(copy);
        }
        return out;
    }

    public Object example(DocumentModel.FieldNode node) {
        if (node == null) return null;
        if (node.truncated) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("_note", node.note);
            return m;
        }
        if ("boolean".equals(node.jsonType)) return false;
        if ("number".equals(node.jsonType)) return 1;
        if ("string decimal".equals(node.jsonType)) return "0.00";
        if ("string date/time".equals(node.jsonType)) return "2026-05-16T10:30:00";
        if ("enum string".equals(node.jsonType)) return node.enumValues.isEmpty() ? "ENUM_VALUE" : node.enumValues.get(0).name;
        if ("array".equals(node.jsonType)) {
            List<Object> arr = new ArrayList<>();
            if (!node.children.isEmpty()) arr.add(example(node.children.get(0)));
            return arr;
        }
        if (!node.children.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (DocumentModel.FieldNode child : node.children) m.put(child.name, example(child));
            return m;
        }
        if ("object".equals(node.jsonType)) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (node.note != null && !node.note.trim().isEmpty()) m.put("_note", node.note);
            return m;
        }
        return "string";
    }

    private String methodId(String name, List<String> paramTypes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((name + "(" + String.join(",", paramTypes) + ")").getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(name).append("-");
            for (int i = 0; i < 6; i++) b.append(String.format("%02x", digest[i]));
            return b.toString();
        } catch (Exception e) {
            return name + "-" + Math.abs(paramTypes.hashCode());
        }
    }
}
