package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCommentReaderTest {
    private final JavaCommentReader comments = new JavaCommentReader(new JavaAnnotationReader());

    @Test
    void readsMethodJavadocsBySection() {
        MethodDeclaration method = method(
                "class Facade {" +
                        "/** 查询状态\n" +
                        " * @param request 查询请求，必填\n" +
                        " * @return 状态文本\n" +
                        " */\n" +
                        "String query(QueryRequest request) { return null; }" +
                        "}"
        );

        Map<String, String> params = comments.paramComments(method);

        assertEquals("查询状态", comments.methodMain(method));
        assertEquals("查询请求，必填", params.get("request"));
        assertEquals("状态文本", comments.returnComment(method));
    }

    @Test
    void readsFieldCommentsAndDeprecation() {
        CompilationUnit cu = StaticJavaParser.parse(
                "class Request {\n" +
                        "/** 订单号 */\n" +
                        "private String orderNo;\n" +
                        "// 内部字段\n" +
                        "private String internalTraceId;\n" +
                        "/** @deprecated use orderNo */\n" +
                        "@Deprecated private String oldOrderNo;\n" +
                        "}"
        );

        assertEquals("订单号", comments.comment(field(cu, "orderNo")).trim());
        assertEquals("内部字段", comments.comment(field(cu, "internalTraceId")));
        assertTrue(comments.deprecated(field(cu, "oldOrderNo")));
    }

    private MethodDeclaration method(String source) {
        return StaticJavaParser.parse(source).findFirst(MethodDeclaration.class).orElseThrow(AssertionError::new);
    }

    private FieldDeclaration field(CompilationUnit cu, String name) {
        return cu.findAll(FieldDeclaration.class).stream()
                .filter(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(name)))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }
}
