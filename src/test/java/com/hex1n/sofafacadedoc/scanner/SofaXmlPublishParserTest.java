package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SofaXmlPublishParserTest {
    @TempDir
    Path root;

    @Test
    void parsesSofaXmlWithProfileYamlPlaceholders() throws Exception {
        Path resources = root.resolve("service/src/main/resources");
        Files.createDirectories(resources.resolve("META-INF/spring"));
        Files.write(resources.resolve("application-test.yml"), Arrays.asList(
                "loan:",
                "  query:",
                "    facade: com.company.loan.facade.LoanQueryFacade",
                "    unique-id: xml-test",
                "    serialize: hessian2"
        ), StandardCharsets.UTF_8);
        Files.write(resources.resolve("META-INF/spring/sofa-services.xml"), Arrays.asList(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<beans xmlns:sofa=\"http://sofastack.io/schema/sofaboot\">",
                "    <sofa:service ref=\"loanQueryFacadeImpl\" interface=\"${loan.query.facade}\" unique-id=\"${loan.query.unique-id}\">",
                "        <sofa:global-attrs timeout=\"${loan.query.timeout:3000}\" serialize-type=\"${loan.query.serialize}\"/>",
                "        <sofa:binding.bolt/>",
                "    </sofa:service>",
                "</beans>"
        ), StandardCharsets.UTF_8);

        List<DocumentModel.PublishRecord> records = new SofaXmlPublishParser().parse(root, Arrays.asList(resources), Arrays.asList("test"));

        assertEquals(1, records.size());
        DocumentModel.PublishRecord record = records.get(0);
        assertEquals("xml", record.source);
        assertEquals("com.company.loan.facade.LoanQueryFacade", record.interfaceName);
        assertEquals("xml-test", record.uniqueId);
        assertEquals("bolt", record.binding);
        assertEquals("hessian2", record.serializeType);
        assertEquals("3000", record.timeout);
        assertEquals("service/src/main/resources/META-INF/spring/sofa-services.xml", record.sourcePath);
        assertEquals(3, record.sourceLine);
        assertFalse(record.incomplete);
    }

    @Test
    void marksUnresolvedPlaceholdersIncomplete() throws Exception {
        Path resources = root.resolve("service/src/main/resources");
        Files.createDirectories(resources);
        Files.write(resources.resolve("service.xml"), Arrays.asList(
                "<beans xmlns:sofa=\"http://sofastack.io/schema/sofaboot\">",
                "<sofa:service interface=\"${missing.facade}\"/>",
                "</beans>"
        ), StandardCharsets.UTF_8);

        List<DocumentModel.PublishRecord> records = new SofaXmlPublishParser().parse(root, Arrays.asList(resources), Arrays.asList("test"));

        assertEquals(1, records.size());
        assertTrue(records.get(0).incomplete);
        assertEquals("unresolved placeholder", records.get(0).incompleteReason);
    }
}
