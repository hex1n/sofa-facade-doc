package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JavaAnnotationReader {
    public Optional<AnnotationExpr> find(NodeWithAnnotations<?> node, String name) {
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String actual = annotation.getNameAsString();
            if (actual.equals(name) || actual.endsWith("." + name)) return Optional.of(annotation);
        }
        return Optional.empty();
    }

    public String attr(AnnotationExpr annotation, String name) {
        if (annotation instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
                if (pair.getNameAsString().equals(name)) return clean(pair.getValue());
            }
        }
        return "";
    }

    public String value(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) return clean(annotation.asSingleMemberAnnotationExpr().getMemberValue());
        return attr(annotation, "value");
    }

    private String clean(Expression expr) {
        String s = expr.toString().trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }
}
