package com.hex1n.sofafacadedoc.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.doc.MarkdownRenderer;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SourceScannerTest {
    @Test
    void scansPublishedFacadeAndGeneratesMarkdown() throws Exception {
        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.sourceRoots = Arrays.asList("facade/src/main/java", "service/src/main/java");
        cfg.resourceRoots = Arrays.asList("service/src/main/resources");
        cfg.facadePackages = Arrays.asList("com.company.loan.facade");
        cfg.springProfiles = Arrays.asList("test");

        SourceScanner scanner = scanner();
        SourceScanner.ScanOutput out = scanner.scan(
                "loan",
                "develop",
                "abc123",
                Paths.get("src/test/resources/fixture/loan"),
                cfg
        );

        assertNotNull(out.structureHash);
        assertEquals(2, out.document.services.size());
        DocumentModel.ServiceDoc service = out.document.services.stream()
                .filter(s -> s.fqn.equals("com.company.loan.facade.LoanApplyFacade"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("com.company.loan.facade.LoanApplyFacade", service.fqn);
        assertEquals("published", service.status);
        assertEquals("贷款申请接口", service.comment.trim());
        assertEquals(1, service.publishRecords.size());
        assertEquals("test", service.publishRecords.get(0).uniqueId);

        DocumentModel.MethodDoc method = service.methods.stream()
                .filter(m -> m.name.equals("submitApply") && m.params.size() == 1 && "com.company.loan.facade.dto.ApplyRequest".equals(m.params.get(0).javaType))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(method.comment.contains("提交贷款申请"));
        assertEquals("submitApply", method.name);
        assertEquals("com.company.loan.facade.dto.ApplyResponse", method.returnType);
        assertEquals("request", method.params.get(0).name);
        assertEquals("java.lang.IllegalArgumentException", method.throwsTypes.get(0));
        DocumentModel.FieldNode orderNo = method.params.get(0).tree.children.stream()
                .filter(f -> f.path.endsWith(".orderNo"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(orderNo.comment.contains("订单号"));
        assertEquals("order_no", orderNo.jsonName);
        assertTrue(orderNo.constraints.contains("JsonProperty(required=true)"));
        assertEquals("是", orderNo.required);
        DocumentModel.FieldNode amount = method.params.get(0).tree.children.stream()
                .filter(f -> f.path.endsWith(".amount"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("string decimal", amount.jsonType);
        assertTrue(amount.constraints.contains("NotNull"));
        assertEquals("是", amount.required);
        assertFalse(method.params.get(0).tree.children.stream().anyMatch(f -> f.path.endsWith(".serialVersionUID")));
        assertFalse(method.params.get(0).tree.children.stream().anyMatch(f -> f.path.endsWith(".internalTraceId")));
        assertFalse(method.params.get(0).tree.children.stream().anyMatch(f -> f.path.endsWith(".localCacheKey")));

        long overloads = service.methods.stream().filter(m -> m.name.equals("submitApply")).count();
        assertEquals(3, overloads);
        DocumentModel.MethodDoc multiParam = service.methods.stream()
                .filter(m -> m.name.equals("submitApply") && m.params.size() == 2)
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(multiParam.requestExample instanceof java.util.List);
        assertEquals("java.lang.String", multiParam.params.get(0).javaType);
        assertEquals("com.company.loan.facade.dto.ApplyRequest", multiParam.params.get(1).javaType);

        DocumentModel.MethodDoc pageMethod = service.methods.stream()
                .filter(m -> m.name.equals("listApplications"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("com.company.loan.facade.dto.PageResult<com.company.loan.facade.dto.ApplyResponse>", pageMethod.returnType);
        DocumentModel.FieldNode items = pageMethod.returnTree.children.stream()
                .filter(f -> f.name.equals("items"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("array", items.jsonType);
        assertEquals("com.company.loan.facade.dto.ApplyResponse", items.children.get(0).javaType);
        assertTrue(items.children.get(0).children.stream().anyMatch(f -> f.path.endsWith(".success")));

        DocumentModel.MethodDoc auditMethod = service.methods.stream()
                .filter(m -> m.name.equals("audit"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("com.company.loan.facade.LoanApplyFacade.AuditInfo", auditMethod.returnType);
        assertTrue(auditMethod.returnTree.children.stream().anyMatch(f -> f.path.endsWith(".operator") && f.comment.contains("操作员")));

        DocumentModel.MethodDoc externalMethod = service.methods.stream()
                .filter(m -> m.name.equals("submitExternalApply"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        DocumentModel.FieldNode externalParam = externalMethod.params.get(0).tree;
        assertEquals("com.company.partner.ExternalApplyRequest", externalParam.javaType);
        assertEquals("object", externalParam.jsonType);
        assertTrue(externalParam.children.isEmpty());
        assertEquals("sourceMissing: com.company.partner.ExternalApplyRequest", externalParam.note);
        assertTrue(externalMethod.requestExample.toString().contains("sourceMissing: com.company.partner.ExternalApplyRequest"));

        DocumentModel.ServiceDoc xmlService = out.document.services.stream()
                .filter(s -> s.fqn.equals("com.company.loan.facade.LoanQueryFacade"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("published", xmlService.status);
        assertEquals("xml", xmlService.publishRecords.get(0).source);
        assertEquals("xml-test", xmlService.publishRecords.get(0).uniqueId);
        assertEquals("hessian2", xmlService.publishRecords.get(0).serializeType);
        assertEquals("3000", xmlService.publishRecords.get(0).timeout);

        MarkdownRenderer renderer = new MarkdownRenderer(new ObjectMapper());
        String markdown = renderer.render(out.document, service, method);
        assertTrue(markdown.contains("贷款申请接口"));
        assertTrue(markdown.contains("订单号"));
        assertTrue(markdown.contains("Jackson 名称：order_no"));
        assertTrue(markdown.contains("约束：NotNull"));
        assertTrue(markdown.contains("请求 JSON 骨架"));
        assertFalse(markdown.contains("源码："));
        String externalMarkdown = renderer.render(out.document, service, externalMethod);
        assertTrue(externalMarkdown.contains("com.company.partner.ExternalApplyRequest"));
        assertTrue(externalMarkdown.contains("sourceMissing: com.company.partner.ExternalApplyRequest"));
        String xmlMarkdown = renderer.render(out.document, xmlService, xmlService.methods.get(0));
        assertTrue(xmlMarkdown.contains("| xml | bolt | xml-test | - | hessian2 | 3000 | - |"));
    }

    @Test
    void autoDiscoversRootsAndDoesNotRequireFacadePackagesForPublishedServices() throws Exception {
        AppConfig.EffectiveBranch cfg = new AppConfig.EffectiveBranch();
        cfg.springProfiles = Arrays.asList("test");

        SourceScanner scanner = scanner();
        SourceScanner.ScanOutput out = scanner.scan(
                "loan",
                "develop",
                "abc123",
                Paths.get("src/test/resources/fixture/loan"),
                cfg
        );

        assertTrue(out.document.diagnostics.sourceRoots.contains("facade/src/main/java"));
        assertTrue(out.document.diagnostics.sourceRoots.contains("service/src/main/java"));
        assertTrue(out.document.diagnostics.resourceRoots.contains("service/src/main/resources"));
        assertTrue(out.document.services.stream().anyMatch(s ->
                s.fqn.equals("com.company.loan.facade.LoanApplyFacade") && "published".equals(s.status)));
        assertTrue(out.document.services.stream().anyMatch(s ->
                s.fqn.equals("com.company.loan.facade.LoanQueryFacade") && "published".equals(s.status)));
        assertFalse(out.document.services.stream().anyMatch(s -> "candidate".equals(s.status)));
    }

    private SourceScanner scanner() {
        JavaAnnotationReader annotations = new JavaAnnotationReader();
        JavaTypeResolver types = new JavaTypeResolver();
        PayloadFieldRules payloadRules = new PayloadFieldRules(annotations);
        JavaCommentReader comments = new JavaCommentReader(annotations);
        SofaAnnotationPublishParser sofaAnnotations = new SofaAnnotationPublishParser(annotations, types);
        JavaSourceIndexer sourceIndexer = new JavaSourceIndexer(payloadRules, comments, types, sofaAnnotations);
        FacadePayloadTreeBuilder payloadTreeBuilder = new FacadePayloadTreeBuilder(payloadRules, types, new DtoBytecodeFallback());
        FacadeDocumentAssembler assembler = new FacadeDocumentAssembler(payloadTreeBuilder);
        DocumentStructureHasher hasher = new DocumentStructureHasher(new ObjectMapper());
        return new SourceScanner(new SourceRootResolver(), sourceIndexer, new SofaXmlPublishParser(), assembler, hasher);
    }
}
