package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 当源码索引中找不到 DTO 时，从配置的 dependency jar 里按 bytecode 还原字段结构。
 *
 * 还原范围：字段名 / 字段类型（含泛型）/ 继承链 / 接口 / 类型参数 / 枚举常量 /
 * Bean Validation 与 Jackson 注解（required / constraints / jsonName / shouldSkip）。
 *
 * 不还原：JavaDoc（bytecode 没有）、方法体、Lombok 生成的内容。
 * 命中后 JavaClassInfo.sourcePath 形如 "jar:lib/foo.jar!com/x/Y.class"。
 */
@Component
public class DtoBytecodeFallback {

    private static final int ASM_API = Opcodes.ASM9;

    private static final List<String> REQUIRED_ANNOTATIONS = Arrays.asList("NotNull", "NotBlank", "NotEmpty");

    public Optional<JavaSourceIndex.JavaClassInfo> resolve(String fqn, JavaSourceIndex index, BytecodeJarSet jars) {
        if (fqn == null || fqn.isEmpty() || jars == null) return Optional.empty();
        if (fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("jakarta.")) return Optional.empty();
        String internal = toInternalName(fqn);
        Optional<byte[]> bytes = jars.readClass(internal);
        if (!bytes.isPresent()) return Optional.empty();

        JavaSourceIndex.JavaClassInfo cls = new JavaSourceIndex.JavaClassInfo();
        cls.fqn = fqn;
        cls.name = simpleName(fqn);
        cls.pkg = packageOf(fqn);
        cls.sourcePath = jars.locate(internal);
        cls.sourceLine = 0;
        cls.imports = new LinkedHashMap<>();
        try {
            new ClassReader(bytes.get()).accept(new BytecodeClassVisitor(cls), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            return Optional.empty();
        }
        index.classes.put(cls.fqn, cls);
        if (cls.name != null && !cls.name.isEmpty()) index.simpleNames.putIfAbsent(cls.name, cls.fqn);
        return Optional.of(cls);
    }

    private static String toInternalName(String fqn) {
        return fqn.replace('.', '/') + ".class";
    }

    private static String simpleName(String fqn) {
        int i = fqn.lastIndexOf('.');
        String last = i < 0 ? fqn : fqn.substring(i + 1);
        int dollar = last.lastIndexOf('$');
        return dollar < 0 ? last : last.substring(dollar + 1);
    }

    private static String packageOf(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? "" : fqn.substring(0, i);
    }

    /** ClassVisitor：填充 typeParams / superclass / interfaces / kind / deprecated / fields / enumValues。 */
    private static final class BytecodeClassVisitor extends ClassVisitor {
        private final JavaSourceIndex.JavaClassInfo cls;

        BytecodeClassVisitor(JavaSourceIndex.JavaClassInfo cls) {
            super(ASM_API);
            this.cls = cls;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if ((access & Opcodes.ACC_ENUM) != 0) cls.kind = "enum";
            else if ((access & Opcodes.ACC_INTERFACE) != 0) cls.kind = "interface";
            else cls.kind = "class";
            cls.deprecated = (access & Opcodes.ACC_DEPRECATED) != 0;
            if (signature != null) {
                ClassSignature parsed = SignatureParser.parseClassSignature(signature);
                cls.typeParams = parsed.typeParameters;
                if (parsed.superclass != null) cls.superclass = parsed.superclass;
                if (!parsed.interfaces.isEmpty()) cls.interfaces = parsed.interfaces;
            }
            if (cls.superclass == null && superName != null && !"java/lang/Object".equals(superName)) {
                cls.superclass = internalToFqn(superName);
            }
            if (cls.interfaces == null || cls.interfaces.isEmpty()) {
                cls.interfaces = new ArrayList<>();
                if (interfaces != null) for (String it : interfaces) cls.interfaces.add(internalToFqn(it));
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & Opcodes.ACC_ENUM) != 0) {
                DocumentModel.EnumValue ev = new DocumentModel.EnumValue();
                ev.name = name;
                cls.enumValues.add(ev);
                return null;
            }
            if ((access & Opcodes.ACC_STATIC) != 0) return null;
            if ((access & Opcodes.ACC_TRANSIENT) != 0) return null;
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) return null;
            if ("serialVersionUID".equals(name)) return null;

            JavaSourceIndex.JavaFieldInfo field = new JavaSourceIndex.JavaFieldInfo();
            field.name = name;
            field.type = signature != null
                    ? SignatureParser.parseFieldSignature(signature)
                    : SignatureParser.parseDescriptor(descriptor);
            field.required = "未知";
            field.sourcePath = cls.sourcePath;
            field.constraints = new ArrayList<>();
            field.jsonName = "";

            return new BytecodeFieldVisitor(field, cls);
        }
    }

    /** FieldVisitor：聚合字段上的注解，按规则填 required / constraints / jsonName / skip。 */
    private static final class BytecodeFieldVisitor extends FieldVisitor {
        private final JavaSourceIndex.JavaFieldInfo field;
        private final JavaSourceIndex.JavaClassInfo owner;
        private boolean ignored;
        private final List<String> presentRequired = new ArrayList<>();

        BytecodeFieldVisitor(JavaSourceIndex.JavaFieldInfo field, JavaSourceIndex.JavaClassInfo owner) {
            super(ASM_API);
            this.field = field;
            this.owner = owner;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String simple = annotationSimpleName(descriptor);
            if (simple == null) return null;
            return new BytecodeAnnotationVisitor(simple, attrs -> applyAnnotation(simple, attrs));
        }

        @Override
        public void visitEnd() {
            if (ignored) return;
            owner.fields.add(field);
        }

        private void applyAnnotation(String simple, Map<String, Object> attrs) {
            if ("JsonIgnore".equals(simple)) {
                Object v = attrs.get("value");
                if (v == null || !"false".equals(String.valueOf(v))) ignored = true;
                return;
            }
            if (REQUIRED_ANNOTATIONS.contains(simple)) {
                presentRequired.add(simple);
                if ("是".equals(field.required) || "未知".equals(field.required)) field.required = "是";
                field.constraints.add(simple);
                return;
            }
            if ("JsonProperty".equals(simple)) {
                Object required = attrs.get("required");
                if ("true".equals(String.valueOf(required))) {
                    if (!"是".equals(field.required)) field.required = "是";
                    field.constraints.add("JsonProperty(required=true)");
                } else if ("false".equals(String.valueOf(required))) {
                    if (!"是".equals(field.required)) field.required = "否";
                }
                Object value = attrs.get("value");
                if (value != null && !String.valueOf(value).trim().isEmpty()) {
                    String v = String.valueOf(value).trim();
                    field.jsonName = v;
                    field.constraints.add("JsonProperty(value=" + v + ")");
                }
                return;
            }
            String constraint = formatConstraint(simple, attrs);
            if (constraint != null) field.constraints.add(constraint);
        }

        private String formatConstraint(String simple, Map<String, Object> attrs) {
            switch (simple) {
                case "Size":
                    return composeAttrs(simple, attrs, "min", "max");
                case "Min":
                case "Max":
                case "DecimalMin":
                case "DecimalMax":
                    return composeAttrs(simple, attrs, "value");
                case "Pattern":
                    return composeAttrs(simple, attrs, "regexp");
                default:
                    return null;
            }
        }

        private String composeAttrs(String simple, Map<String, Object> attrs, String... keys) {
            List<String> parts = new ArrayList<>();
            for (String k : keys) {
                Object v = attrs.get(k);
                if (v == null) continue;
                String s = String.valueOf(v).trim();
                if (s.isEmpty()) continue;
                parts.add(k + "=" + s);
            }
            return parts.isEmpty() ? simple : simple + "(" + String.join(",", parts) + ")";
        }
    }

    /** AnnotationVisitor：收集 name → value 映射（基础类型 / 字符串 / 枚举 simple name）。 */
    private static final class BytecodeAnnotationVisitor extends AnnotationVisitor {
        private final String simpleName;
        private final java.util.function.Consumer<Map<String, Object>> onEnd;
        private final Map<String, Object> attrs = new LinkedHashMap<>();

        BytecodeAnnotationVisitor(String simpleName, java.util.function.Consumer<Map<String, Object>> onEnd) {
            super(ASM_API);
            this.simpleName = simpleName;
            this.onEnd = onEnd;
        }

        @Override
        public void visit(String name, Object value) {
            if (name != null) attrs.put(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if (name != null) attrs.put(name, value);
        }

        @Override
        public void visitEnd() {
            onEnd.accept(attrs);
        }
    }

    static String annotationSimpleName(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("L") || !descriptor.endsWith(";")) return null;
        String name = descriptor.substring(1, descriptor.length() - 1);
        int slash = name.lastIndexOf('/');
        String last = slash < 0 ? name : name.substring(slash + 1);
        int dollar = last.lastIndexOf('$');
        return dollar < 0 ? last : last.substring(dollar + 1);
    }

    static String internalToFqn(String internal) {
        return internal.replace('/', '.');
    }

    /** 极简 JVM signature 解析，覆盖 P1 需要的：泛型类签名、字段签名、字段描述符。 */
    static final class SignatureParser {
        private final String src;
        private int pos;

        private SignatureParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        static ClassSignature parseClassSignature(String sig) {
            SignatureParser p = new SignatureParser(sig);
            ClassSignature out = new ClassSignature();
            if (p.peek() == '<') {
                p.pos++;
                while (p.peek() != '>') {
                    int start = p.pos;
                    while (p.peek() != ':' && p.peek() != 0) p.pos++;
                    out.typeParameters.add(p.src.substring(start, p.pos));
                    while (p.peek() == ':') {
                        p.pos++;
                        if (p.peek() == 'L' || p.peek() == 'T' || p.peek() == '[') p.parseType();
                    }
                }
                p.pos++;
            }
            if (p.peek() == 'L' || p.peek() == 'T') {
                String s = p.parseType();
                if (!"java.lang.Object".equals(s)) out.superclass = s;
            }
            while (p.peek() == 'L') out.interfaces.add(p.parseType());
            return out;
        }

        static String parseFieldSignature(String sig) {
            return new SignatureParser(sig).parseType();
        }

        static String parseDescriptor(String desc) {
            return new SignatureParser(desc).parseType();
        }

        private char peek() {
            return pos < src.length() ? src.charAt(pos) : 0;
        }

        private String parseType() {
            char c = peek();
            switch (c) {
                case '[':
                    pos++;
                    return parseType() + "[]";
                case 'B': pos++; return "byte";
                case 'C': pos++; return "char";
                case 'D': pos++; return "double";
                case 'F': pos++; return "float";
                case 'I': pos++; return "int";
                case 'J': pos++; return "long";
                case 'S': pos++; return "short";
                case 'Z': pos++; return "boolean";
                case 'V': pos++; return "void";
                case 'T': {
                    pos++;
                    int start = pos;
                    while (peek() != ';' && peek() != 0) pos++;
                    String name = src.substring(start, pos);
                    if (peek() == ';') pos++;
                    return name;
                }
                case 'L':
                    return parseObjectType();
                case '*':
                    pos++;
                    return "?";
                case '+':
                    pos++;
                    return "? extends " + parseType();
                case '-':
                    pos++;
                    return "? super " + parseType();
                default:
                    pos++;
                    return "";
            }
        }

        private String parseObjectType() {
            pos++; // skip 'L'
            StringBuilder name = new StringBuilder();
            StringBuilder generics = new StringBuilder();
            while (peek() != ';' && peek() != 0) {
                char c = peek();
                if (c == '/') {
                    pos++;
                    name.append('.');
                } else if (c == '.') {
                    pos++;
                    name.append('$');
                } else if (c == '<') {
                    pos++;
                    generics.append('<');
                    boolean first = true;
                    while (peek() != '>' && peek() != 0) {
                        if (!first) generics.append(',');
                        first = false;
                        generics.append(parseType());
                    }
                    if (peek() == '>') pos++;
                    generics.append('>');
                } else {
                    pos++;
                    name.append(c);
                }
            }
            if (peek() == ';') pos++;
            return name.toString() + generics.toString();
        }
    }

    static final class ClassSignature {
        List<String> typeParameters = new ArrayList<>();
        String superclass;
        List<String> interfaces = new ArrayList<>();
    }
}
