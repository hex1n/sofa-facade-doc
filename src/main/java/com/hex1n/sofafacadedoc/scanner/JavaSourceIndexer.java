package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class JavaSourceIndexer {
    private final PayloadFieldRules payloadRules;
    private final JavaCommentReader comments;
    private final JavaTypeResolver types;
    private final SofaAnnotationPublishParser sofaAnnotations;

    public JavaSourceIndexer(PayloadFieldRules payloadRules, JavaCommentReader comments, JavaTypeResolver types, SofaAnnotationPublishParser sofaAnnotations) {
        this.payloadRules = payloadRules;
        this.comments = comments;
        this.types = types;
        this.sofaAnnotations = sofaAnnotations;
    }

    public JavaSourceIndex index(Path root, Iterable<Path> sourceRoots) throws Exception {
        JavaSourceIndex index = new JavaSourceIndex(root);
        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(this::isJavaFile).forEach(path -> parseJava(index, path));
            }
        }
        return index;
    }

    private void parseJava(JavaSourceIndex index, Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            Map<String, String> imports = new LinkedHashMap<>();
            for (ImportDeclaration id : cu.getImports()) {
                if (id.isAsterisk() && !id.isStatic()) {
                    String pkgImport = id.getNameAsString();
                    imports.put(pkgImport + ".*", pkgImport + ".*");
                } else if (!id.isStatic()) {
                    String fqn = id.getNameAsString();
                    imports.put(simpleName(fqn), fqn);
                }
            }
            for (TypeDeclaration<?> type : cu.getTypes()) {
                indexType(index, pkg, imports, path, type, null);
            }
            index.parsedFiles++;
        } catch (Exception e) {
            index.failedFiles.add(index.rel(path) + ": " + e.getMessage());
        }
    }

    private void indexType(JavaSourceIndex index, String pkg, Map<String, String> imports, Path path, TypeDeclaration<?> type, String ownerFqn) {
        JavaSourceIndex.JavaClassInfo cls = new JavaSourceIndex.JavaClassInfo();
        cls.name = type.getNameAsString();
        cls.kind = type instanceof ClassOrInterfaceDeclaration && ((ClassOrInterfaceDeclaration) type).isInterface() ? "interface" :
                type instanceof EnumDeclaration ? "enum" : "class";
        cls.fqn = ownerFqn == null ? (pkg.isEmpty() ? cls.name : pkg + "." + cls.name) : ownerFqn + "." + cls.name;
        cls.comment = comments.javadoc(type);
        cls.deprecated = comments.deprecated(type);
        cls.sourcePath = index.rel(path);
        cls.sourceLine = type.getBegin().map(p -> p.line).orElse(0);
        cls.imports = imports;
        cls.pkg = pkg;
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cd = (ClassOrInterfaceDeclaration) type;
            for (com.github.javaparser.ast.type.TypeParameter tp : cd.getTypeParameters()) cls.typeParams.add(tp.getNameAsString());
            cd.getExtendedTypes().forEach(t -> {
                if (cd.isInterface()) cls.interfaces.add(types.resolve(t.toString(), pkg, imports, index, null));
                else cls.superclass = types.resolve(t.toString(), pkg, imports, index, null);
            });
            cd.getImplementedTypes().forEach(t -> cls.interfaces.add(types.resolve(t.toString(), pkg, imports, index, null)));
        }
        index.classes.put(cls.fqn, cls);
        index.simpleNames.put(cls.name, cls.fqn);
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration) {
                indexType(index, pkg, imports, path, (TypeDeclaration<?>) member, cls.fqn);
            }
        }
        Set<String> classTypeParams = new LinkedHashSet<>(cls.typeParams);
        for (FieldDeclaration fd : type.getFields()) {
            for (VariableDeclarator v : fd.getVariables()) {
                if (payloadRules.shouldSkip(fd, v)) continue;
                JavaSourceIndex.JavaFieldInfo field = new JavaSourceIndex.JavaFieldInfo();
                field.name = v.getNameAsString();
                field.jsonName = payloadRules.jsonPropertyName(fd);
                field.type = types.resolve(v.getType().toString(), pkg, imports, index, classTypeParams);
                field.comment = comments.comment(fd);
                field.required = payloadRules.required(fd, field.comment);
                field.constraints = payloadRules.constraints(fd);
                field.sourcePath = cls.sourcePath;
                field.sourceLine = fd.getBegin().map(p -> p.line).orElse(0);
                cls.fields.add(field);
            }
        }
        for (MethodDeclaration md : type.getMethods()) {
            JavaSourceIndex.JavaMethodInfo method = new JavaSourceIndex.JavaMethodInfo();
            method.name = md.getNameAsString();
            Set<String> methodTypeParams = new LinkedHashSet<>(classTypeParams);
            md.getTypeParameters().forEach(tp -> methodTypeParams.add(tp.getNameAsString()));
            method.returnType = types.resolve(md.getType().toString(), pkg, imports, index, methodTypeParams);
            method.comment = comments.methodMain(md);
            method.paramComments = comments.paramComments(md);
            method.returnComment = comments.returnComment(md);
            method.deprecated = comments.deprecated(md);
            method.sourcePath = cls.sourcePath;
            method.sourceLine = md.getBegin().map(p -> p.line).orElse(0);
            for (Parameter p : md.getParameters()) {
                JavaSourceIndex.JavaParamInfo jp = new JavaSourceIndex.JavaParamInfo();
                jp.name = p.getNameAsString();
                jp.type = types.resolve(p.getType().toString(), pkg, imports, index, methodTypeParams);
                method.params.add(jp);
            }
            for (com.github.javaparser.ast.type.ReferenceType rt : md.getThrownExceptions()) {
                method.throwsTypes.add(types.resolve(rt.toString(), pkg, imports, index, null));
            }
            cls.methods.add(method);
            sofaAnnotations.publishFromBeanMethod(index, md, pkg, imports);
        }
        if (type instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) type;
            for (EnumConstantDeclaration c : ed.getEntries()) {
                DocumentModel.EnumValue ev = new DocumentModel.EnumValue();
                ev.name = c.getNameAsString();
                ev.comment = comments.comment(c);
                cls.enumValues.add(ev);
            }
        }
        sofaAnnotations.publishFromClass(index, type, cls);
    }

    private boolean isJavaFile(Path p) {
        String s = p.toString().replace(File.separatorChar, '/');
        return s.endsWith(".java") && !s.contains("/src/test/") && !s.contains("/target/") && !s.contains("/build/") && !s.contains("/generated-sources/");
    }

    private String simpleName(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }
}
