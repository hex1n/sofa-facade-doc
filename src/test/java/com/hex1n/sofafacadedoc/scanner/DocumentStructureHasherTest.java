package com.hex1n.sofafacadedoc.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DocumentStructureHasherTest {
    private final DocumentStructureHasher hasher = new DocumentStructureHasher(new ObjectMapper());

    @Test
    void ignoresCommitAndGeneratedAtButIncludesInterfaceShape() throws Exception {
        DocumentModel.Document left = document("c1", "2026-05-17T10:00:00Z", "订单号");
        DocumentModel.Document right = document("c2", "2026-05-17T10:05:00Z", "订单号");
        DocumentModel.Document changed = document("c2", "2026-05-17T10:05:00Z", "订单编号");

        assertEquals(hasher.hash(left), hasher.hash(right));
        assertNotEquals(hasher.hash(left), hasher.hash(changed));
    }

    private DocumentModel.Document document(String commit, String generatedAt, String fieldComment) {
        DocumentModel.Document doc = new DocumentModel.Document();
        doc.project = "loan";
        doc.branch = "main";
        doc.commit = commit;
        doc.generatedAt = generatedAt;
        DocumentModel.ServiceDoc service = new DocumentModel.ServiceDoc();
        service.fqn = "com.company.loan.facade.LoanFacade";
        service.status = "published";
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.id = "query-abc";
        method.name = "query";
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = "request";
        param.javaType = "com.company.loan.facade.dto.QueryRequest";
        param.jsonType = "object";
        DocumentModel.FieldNode root = new DocumentModel.FieldNode();
        root.path = "request";
        root.name = "request";
        root.javaType = param.javaType;
        root.jsonType = "object";
        DocumentModel.FieldNode orderNo = new DocumentModel.FieldNode();
        orderNo.path = "request.orderNo";
        orderNo.name = "orderNo";
        orderNo.javaType = "java.lang.String";
        orderNo.jsonType = "string";
        orderNo.comment = fieldComment;
        root.children.add(orderNo);
        param.tree = root;
        method.params.add(param);
        service.methods.add(method);
        doc.services.add(service);
        return doc;
    }
}
