package com.hex1n.sofafacadedoc.diff;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffServiceTest {
    private final DiffService diffService = new DiffService();

    @Test
    void fieldMetadataChangesAreReported() {
        DocumentModel.FieldNode leftField = field("request.amount", "amount", "java.math.BigDecimal", "string decimal", "", "旧金额说明", "否");
        DocumentModel.FieldNode rightField = field("request.amount", "amount", "java.math.BigDecimal", "number", "amount_value", "新金额说明", "是");
        rightField.constraints = Arrays.asList("NotNull");

        List<DocumentModel.DiffChange> changes = diffService.compare(document(leftField), document(rightField));

        assertHasMessage(changes, "字段 JSON 类型变化");
        assertHasMessage(changes, "字段 JSON 名称变化");
        assertHasMessage(changes, "字段必填约束新增");
        assertHasMessage(changes, "字段约束变化");
        assertHasMessage(changes, "字段注释变化");
    }

    @Test
    void nullAndBlankMetadataAreEquivalent() {
        DocumentModel.FieldNode leftField = field("request.amount", "amount", "java.math.BigDecimal", "string decimal", null, null, null);
        DocumentModel.FieldNode rightField = field("request.amount", "amount", "java.math.BigDecimal", "string decimal", " ", " ", " ");

        List<DocumentModel.DiffChange> changes = diffService.compare(document(leftField), document(rightField));

        assertFalse(changes.stream().anyMatch(c -> c.message.startsWith("字段")));
    }

    private void assertHasMessage(List<DocumentModel.DiffChange> changes, String message) {
        assertTrue(changes.stream().anyMatch(c -> message.equals(c.message)), "missing diff message: " + message);
    }

    private DocumentModel.Document document(DocumentModel.FieldNode field) {
        DocumentModel.Document doc = new DocumentModel.Document();
        DocumentModel.ServiceDoc service = new DocumentModel.ServiceDoc();
        service.fqn = "com.example.LoanFacade";
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.id = "submit-1";
        method.name = "submit";
        method.returnType = "java.lang.String";
        method.returnTree = field("return", "return", "java.lang.String", "string", "", "", "未知");
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = "request";
        param.javaType = "com.example.Request";
        param.comment = "";
        param.required = "是";
        param.tree = field("request", "request", "com.example.Request", "object", "", "", "是");
        param.tree.children.add(field);
        method.params.add(param);
        service.methods.add(method);
        doc.services.add(service);
        return doc;
    }

    private DocumentModel.FieldNode field(String path, String name, String javaType, String jsonType, String jsonName, String comment, String required) {
        DocumentModel.FieldNode field = new DocumentModel.FieldNode();
        field.path = path;
        field.name = name;
        field.javaType = javaType;
        field.jsonType = jsonType;
        field.jsonName = jsonName;
        field.comment = comment;
        field.required = required;
        return field;
    }
}
