package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@Component
public class SofaAnnotationPublishParser {
    private final JavaAnnotationReader annotations;
    private final JavaTypeResolver types;

    public SofaAnnotationPublishParser(JavaAnnotationReader annotations, JavaTypeResolver types) {
        this.annotations = annotations;
        this.types = types;
    }

    public void publishFromClass(JavaSourceIndex index, TypeDeclaration<?> type, JavaSourceIndex.JavaClassInfo cls) {
        Optional<AnnotationExpr> ann = annotations.find(type, "SofaService");
        if (!ann.isPresent()) return;
        DocumentModel.PublishRecord pr = new DocumentModel.PublishRecord();
        pr.source = "annotation";
        pr.implementation = cls.fqn;
        pr.interfaceName = interfaceType(ann.get(), cls);
        if ((pr.interfaceName == null || pr.interfaceName.isEmpty()) && cls.interfaces.size() == 1) pr.interfaceName = cls.interfaces.get(0);
        pr.binding = binding(ann.get());
        pr.uniqueId = annotations.attr(ann.get(), "uniqueId");
        pr.version = annotations.attr(ann.get(), "version");
        pr.sourcePath = cls.sourcePath;
        pr.sourceLine = cls.sourceLine;
        if (pr.interfaceName == null || pr.interfaceName.isEmpty()) {
            pr.incomplete = true;
            pr.incompleteReason = "missing interfaceType and implementation does not have exactly one interface";
        }
        index.publishRecords.add(pr);
    }

    public void publishFromBeanMethod(JavaSourceIndex index, MethodDeclaration md, String pkg, Map<String, String> imports) {
        Optional<AnnotationExpr> ann = annotations.find(md, "SofaService");
        if (!ann.isPresent()) return;
        DocumentModel.PublishRecord pr = new DocumentModel.PublishRecord();
        pr.source = "annotation-bean";
        String iface = annotations.attr(ann.get(), "interfaceType");
        if (iface != null && iface.endsWith(".class")) iface = iface.substring(0, iface.length() - ".class".length());
        pr.interfaceName = types.resolve(iface, pkg, imports, index, null);
        pr.binding = binding(ann.get());
        pr.uniqueId = annotations.attr(ann.get(), "uniqueId");
        pr.version = annotations.attr(ann.get(), "version");
        pr.sourcePath = index.rel(Paths.get(md.findCompilationUnit().flatMap(CompilationUnit::getStorage).map(s -> s.getPath().toString()).orElse("")));
        pr.sourceLine = md.getBegin().map(p -> p.line).orElse(0);
        if (pr.interfaceName == null || pr.interfaceName.isEmpty()) {
            pr.incomplete = true;
            pr.incompleteReason = "missing interfaceType on @Bean @SofaService";
        }
        index.publishRecords.add(pr);
    }

    private String interfaceType(AnnotationExpr ann, JavaSourceIndex.JavaClassInfo cls) {
        String v = annotations.attr(ann, "interfaceType");
        if (v != null && v.endsWith(".class")) v = v.substring(0, v.length() - 6);
        if (v == null || v.isEmpty()) return "";
        JavaSourceIndex importOnly = new JavaSourceIndex(Paths.get("."));
        importOnly.simpleNames.putAll(cls.imports);
        return types.resolve(v, cls.pkg, cls.imports, importOnly, null);
    }

    private String binding(AnnotationExpr ann) {
        String text = ann.toString();
        return text.contains("bolt") || text.contains("SofaServiceBinding") ? "bolt" : "";
    }
}
