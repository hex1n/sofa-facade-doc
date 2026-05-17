package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadFieldRulesTest {
    private final PayloadFieldRules rules = new PayloadFieldRules(new JavaAnnotationReader());

    @Test
    void infersRequiredFromCommentText() {
        assertEquals("是", rules.required("订单号，必填"));
        assertEquals("是", rules.required("required by gateway"));
        assertEquals("否", rules.required("扩展字段，非必填"));
        assertEquals("否", rules.required("optional memo"));
        assertEquals("未知", rules.required("订单备注"));
    }

    @Test
    void extractsJsonPropertyAndConstraints() {
        FieldDeclaration field = field("orderNo", "class Request { @JsonProperty(value = \"order_no\", required = true) @NotBlank @Size(min = 3, max = 20) private String orderNo; }");

        List<String> constraints = rules.constraints(field);

        assertEquals("order_no", rules.jsonPropertyName(field));
        assertEquals("是", rules.required(field, ""));
        assertTrue(constraints.contains("NotBlank"));
        assertTrue(constraints.contains("Size(min=3,max=20)"));
        assertTrue(constraints.contains("JsonProperty(required=true)"));
        assertTrue(constraints.contains("JsonProperty(value=order_no)"));
    }

    @Test
    void skipsNonPayloadFields() {
        assertTrue(shouldSkip("serialVersionUID", "class Request { private static final long serialVersionUID = 1L; }"));
        assertTrue(shouldSkip("traceId", "class Request { private transient String traceId; }"));
        assertTrue(shouldSkip("secret", "class Request { @JsonIgnore private String secret; }"));
        assertFalse(shouldSkip("visible", "class Request { @JsonIgnore(false) private String visible; }"));
    }

    private boolean shouldSkip(String name, String source) {
        FieldDeclaration field = field(name, source);
        VariableDeclarator variable = field.getVariables().get(0);
        return rules.shouldSkip(field, variable);
    }

    private FieldDeclaration field(String name, String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        return cu.findAll(FieldDeclaration.class).stream()
                .filter(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(name)))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }
}
