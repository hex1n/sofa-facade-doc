package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAnnotationReaderTest {
    private final JavaAnnotationReader reader = new JavaAnnotationReader();

    @Test
    void findsSimpleAndFullyQualifiedAnnotations() {
        FieldDeclaration field = field("class Request { @com.fasterxml.jackson.annotation.JsonProperty(\"order_no\") private String orderNo; }");

        Optional<AnnotationExpr> annotation = reader.find(field, "JsonProperty");

        assertTrue(annotation.isPresent());
        assertEquals("order_no", reader.value(annotation.get()));
    }

    @Test
    void readsNamedAttributesAndKeepsClassExpressionText() {
        FieldDeclaration field = field("class Service { @SofaService(interfaceType = LoanFacade.class, uniqueId = \"loan-test\") private String marker; }");

        AnnotationExpr annotation = reader.find(field, "SofaService").orElseThrow(AssertionError::new);

        assertEquals("LoanFacade.class", reader.attr(annotation, "interfaceType"));
        assertEquals("loan-test", reader.attr(annotation, "uniqueId"));
        assertEquals("", reader.attr(annotation, "missing"));
    }

    private FieldDeclaration field(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        return cu.findFirst(FieldDeclaration.class).orElseThrow(AssertionError::new);
    }
}
