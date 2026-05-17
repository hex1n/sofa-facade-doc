package com.hex1n.sofafacadedoc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.SofaFacadeDocApplication;
import com.hex1n.sofafacadedoc.store.StoreService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SofaFacadeDocApplication.class)
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final RuntimeFixture FIXTURE = RuntimeFixture.create();

    static {
        System.setProperty("sofa.doc.config", FIXTURE.config.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    StoreService store;

    @AfterAll
    static void clearProperty() {
        System.clearProperty("sofa.doc.config");
    }

    @Test
    void servesStaticUiAssetsWithCoreWorkflowHooks() throws Exception {
        assertEquals("index.html", mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getForwardedUrl());

        String index = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(index.contains("SOFABoot Facade 文档平台"));
        assertTrue(index.contains("id=\"projectSelect\""));
        assertTrue(index.contains("id=\"branchSelect\""));
        assertTrue(index.contains("id=\"serviceList\""));
        assertTrue(index.contains("id=\"docPane\""));
        assertTrue(index.contains("id=\"argsEditor\""));
        assertTrue(index.contains("id=\"configBtn\""));
        assertTrue(index.contains("class=\"platform-entry\""));
        assertTrue(index.contains("id=\"configEditor\""));
        assertTrue(index.contains("<script src=\"/config-workbench.js\"></script>"));
        assertTrue(index.contains("<script src=\"/app.js\"></script>"));
        assertTrue(index.contains("<link rel=\"stylesheet\" href=\"/styles.css\">"));

        String app = mvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(app.contains("sessionStorage.setItem(\"sofaDocToken\""));
        assertTrue(app.contains("refreshSelectedMethod(previousMethodId"));
        assertTrue(app.contains("clearSelectedMethod(\"当前分支不存在该接口方法\""));
        assertTrue(app.contains("copyMd"));
        assertTrue(app.contains("downloadMd"));
        assertTrue(app.contains("invokeBtn"));
        assertTrue(app.contains("saveCaseBtn"));
        assertTrue(app.contains("/scan-reports?"));
        assertTrue(app.contains("/search?q="));

        String configWorkbench = mvc.perform(get("/config-workbench.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(configWorkbench.contains("/api/config/projects"));
        assertTrue(configWorkbench.contains("/api/config/projects/validate"));
        assertTrue(configWorkbench.contains("createConfigWorkbench"));

        String css = mvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(css.contains(".app {"));
        assertTrue(css.contains(".sidebar"));
        assertTrue(css.contains(".content"));
        assertTrue(css.contains(".invoke"));
        assertTrue(css.contains(".platform-entry"));
        assertTrue(css.contains(".modal-panel"));
        assertTrue(css.contains("@media (max-width: 820px)"));
    }

    @Test
    void scansAndServesSlashBranchesThroughHttpApi() throws Exception {
        String health = mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("UP", mapper.readTree(health).get("status").asText());

        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        String projectTokenResponse = mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("loan", mapper.readTree(projectTokenResponse).get(0).get("id").asText());
        mvc.perform(post("/api/admin/reload-config").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reload-config").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/config").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/config/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isForbidden());

        String configBody = mvc.perform(get("/api/admin/config").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode configJson = mapper.readTree(configBody);
        assertTrue(configJson.get("path").asText().endsWith("config.yml"));
        String configContent = configJson.get("content").asText();
        assertTrue(configContent.contains("projects:"));

        mvc.perform(post("/api/admin/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"projects: [\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("content", configContent))))
                .andExpect(status().isOk());

        String loanTeamConfigBody = mvc.perform(get("/api/config/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode loanTeamConfig = mapper.readTree(loanTeamConfigBody);
        assertFalse(loanTeamConfig.get("admin").asBoolean());
        assertEquals("loan-team", loanTeamConfig.get("teams").get(0).get("id").asText());
        assertEquals(1, loanTeamConfig.get("projects").size());
        assertEquals("loan", loanTeamConfig.get("projects").get(0).get("id").asText());
        assertEquals("loan-team", loanTeamConfig.get("projects").get(0).get("team").asText());
        assertFalse(loanTeamConfig.toString().contains("\"card\""));

        String adminEditableConfig = mvc.perform(get("/api/config/projects").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode adminEditable = mapper.readTree(adminEditableConfig);
        assertTrue(adminEditable.get("admin").asBoolean());
        assertTrue(adminEditable.toString().contains("\"loan\""));
        assertTrue(adminEditable.toString().contains("\"card\""));

        String validationBody = mvc.perform(post("/api/config/projects/validate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", loanTeamConfig.get("projects")))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(mapper.readTree(validationBody).get("ok").asBoolean());

        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer card-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", loanTeamConfig.get("projects")))))
                .andExpect(status().isForbidden());

        Map<String, Object> migratedLoan = mapper.convertValue(loanTeamConfig.get("projects").get(0), LinkedHashMap.class);
        migratedLoan.put("team", "card-team");
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(migratedLoan)))))
                .andExpect(status().isForbidden());

        Map<String, Object> foreignTeamProject = new LinkedHashMap<>();
        foreignTeamProject.put("id", "loan-owned-by-card");
        foreignTeamProject.put("team", "card-team");
        foreignTeamProject.put("displayName", "错误团队项目");
        foreignTeamProject.put("repo", FIXTURE.repo.toString());
        foreignTeamProject.put("baselineBranch", "main");
        foreignTeamProject.put("tokens", Collections.singletonList("bad-token"));
        Map<String, Object> foreignDefaults = new LinkedHashMap<>();
        foreignDefaults.put("directUrl", "bolt://127.0.0.1:12210");
        foreignDefaults.put("springProfiles", Collections.singletonList("test"));
        foreignTeamProject.put("branchDefaults", foreignDefaults);
        Map<String, Object> foreignBranches = new LinkedHashMap<>();
        foreignBranches.put("include", Collections.singletonList("main"));
        foreignBranches.put("exclude", Collections.emptyList());
        foreignBranches.put("maxMatched", 20);
        foreignTeamProject.put("branches", foreignBranches);
        foreignTeamProject.put("sourceRoots", Arrays.asList("facade/src/main/java", "service/src/main/java"));
        foreignTeamProject.put("resourceRoots", Collections.singletonList("service/src/main/resources"));
        foreignTeamProject.put("facadePackages", Collections.singletonList("com.company.loan.facade"));
        foreignTeamProject.put("branchOverrides", Collections.emptyMap());
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(foreignTeamProject)))))
                .andExpect(status().isForbidden());

        Map<String, Object> loanExtra = new LinkedHashMap<>();
        loanExtra.put("id", "loan-extra");
        loanExtra.put("team", "loan-team");
        loanExtra.put("displayName", "贷款扩展服务");
        loanExtra.put("repo", FIXTURE.repo.toString());
        loanExtra.put("baselineBranch", "main");
        loanExtra.put("tokens", Collections.singletonList("loan-extra-token"));
        Map<String, Object> loanExtraDefaults = new LinkedHashMap<>();
        loanExtraDefaults.put("directUrl", "bolt://127.0.0.1:12209");
        loanExtraDefaults.put("springProfiles", Collections.singletonList("test"));
        loanExtra.put("branchDefaults", loanExtraDefaults);
        Map<String, Object> loanExtraBranches = new LinkedHashMap<>();
        loanExtraBranches.put("include", Collections.singletonList("main"));
        loanExtraBranches.put("exclude", Collections.emptyList());
        loanExtraBranches.put("maxMatched", 20);
        loanExtra.put("branches", loanExtraBranches);
        loanExtra.put("sourceRoots", Arrays.asList("facade/src/main/java", "service/src/main/java"));
        loanExtra.put("resourceRoots", Collections.singletonList("service/src/main/resources"));
        loanExtra.put("facadePackages", Collections.singletonList("com.company.loan.facade"));
        loanExtra.put("branchOverrides", Collections.emptyMap());
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(loanExtra)))))
                .andExpect(status().isOk());

        loanExtra.put("displayName", "贷款扩展服务二");
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(loanExtra)))))
                .andExpect(status().isOk());

        Map<String, Object> changedRepo = new LinkedHashMap<>(loanExtra);
        changedRepo.put("repo", FIXTURE.repo.resolve("other").toString());
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(changedRepo)))))
                .andExpect(status().isForbidden());

        Map<String, Object> changedTokens = new LinkedHashMap<>(loanExtra);
        changedTokens.put("tokens", Collections.singletonList("changed-token"));
        mvc.perform(put("/api/config/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Collections.singletonMap("projects", Collections.singletonList(changedTokens)))))
                .andExpect(status().isForbidden());

        String loanExtraProjectTokenBody = mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-extra-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(loanExtraProjectTokenBody.contains("loan-extra"));
        assertFalse(loanExtraProjectTokenBody.contains("card"));

        String afterLockedConfigBody = mvc.perform(get("/api/config/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode afterLockedLoanExtra = findProject(mapper.readTree(afterLockedConfigBody), "loan-extra");
        assertEquals("贷款扩展服务二", afterLockedLoanExtra.get("displayName").asText());
        assertEquals(FIXTURE.repo.toString(), afterLockedLoanExtra.get("repo").asText());
        assertEquals("loan-extra-token", afterLockedLoanExtra.get("tokens").get(0).asText());

        String loanTeamProjectsBody = mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(loanTeamProjectsBody.contains("loan-extra"));
        String cardTeamProjectsBody = mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer card-team-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(cardTeamProjectsBody.contains("loan"));
        assertTrue(cardTeamProjectsBody.contains("card"));
        mvc.perform(get("/api/projects/card/branches").header(HttpHeaders.AUTHORIZATION, "Bearer loan-team-token"))
                .andExpect(status().isForbidden());

        JsonNode projects = getJson("/api/projects");
        assertEquals("loan", projects.get(0).get("id").asText());
        assertTrue(projects.get(0).get("branches").toString().contains("main"));
        assertTrue(projects.get(0).get("branches").toString().contains("feature/apply-flow"));
        assertTrue(projects.get(0).get("branches").toString().contains("feature/no-facade"));
        assertFalse(projects.get(0).get("branches").toString().contains("feature/wip-skip"));

        JsonNode scan = postJson("/api/projects/loan/scan", null);
        assertEquals("success", scan.get("main").get("status").asText());
        assertEquals("success", scan.get("feature/apply-flow").get("status").asText());
        assertEquals("failed", scan.get("feature/no-facade").get("status").asText());
        assertFalse(scan.get("feature/no-facade").get("snapshotCreated").asBoolean());
        assertTrue(scan.get("feature/no-facade").get("message").asText().contains("no facade service found"));

        String projectTokenScanBody = mvc.perform(post("/api/projects/loan/scan").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode projectTokenScan = mapper.readTree(projectTokenScanBody);
        assertEquals("failed", projectTokenScan.get("feature/no-facade").get("status").asText());
        assertEquals("scan failed; ask an admin to check details", projectTokenScan.get("feature/no-facade").get("message").asText());

        String projectTokenFailure = mvc.perform(post("/api/projects/loan/branches/scan?branch=" + enc("feature/no-facade")).header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("operation failed; ask an admin to check details", mapper.readTree(projectTokenFailure).get("message").asText());
        String adminFailure = mvc.perform(post("/api/projects/loan/branches/scan?branch=" + enc("feature/no-facade")).header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(mapper.readTree(adminFailure).get("message").asText().contains("no facade service found"));

        for (int i = 0; i < 25; i++) {
            store.saveReport("loan", "feature/no-facade", null, "failed", "failure-" + i);
        }
        assertEquals(20, countFailedReports("loan", "feature/no-facade"));
        JsonNode adminReports = getJson("/api/projects/loan/scan-reports?branch=" + enc("feature/no-facade") + "&limit=3");
        assertEquals(3, adminReports.size());
        assertEquals("failure-24", adminReports.get(0).get("message").asText());
        String projectReportsBody = mvc.perform(get("/api/projects/loan/scan-reports?branch=" + enc("feature/no-facade") + "&limit=1").header(HttpHeaders.AUTHORIZATION, "Bearer loan-token"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode projectReports = mapper.readTree(projectReportsBody);
        assertEquals("scan failed; ask an admin to check details", projectReports.get(0).get("message").asText());

        JsonNode repeated = postJson("/api/projects/loan/branches/scan?branch=" + enc("feature/apply-flow"), null);
        assertEquals("unchanged", repeated.get("status").asText());
        assertFalse(repeated.get("snapshotCreated").asBoolean());

        FIXTURE.createBranchFrom("feature/scan-behavior", "main");
        JsonNode scanBehaviorInitial = postJson("/api/projects/loan/branches/scan?branch=" + enc("feature/scan-behavior"), null);
        assertEquals("success", scanBehaviorInitial.get("status").asText());
        assertTrue(scanBehaviorInitial.get("snapshotCreated").asBoolean());
        long scanBehaviorSnapshotId = scanBehaviorInitial.get("snapshotId").asLong();
        String scanBehaviorHash = scanBehaviorInitial.get("structureHash").asText();

        FIXTURE.commitText("feature/scan-behavior", "README.md", "only docs changed\n", "docs-only-change");
        JsonNode pathSkipped = postJson("/api/projects/loan/branches/scan?branch=" + enc("feature/scan-behavior"), null);
        assertEquals("skipped", pathSkipped.get("status").asText());
        assertEquals("no interface-related file changed", pathSkipped.get("message").asText());
        assertFalse(pathSkipped.get("snapshotCreated").asBoolean());
        assertEquals(scanBehaviorSnapshotId, pathSkipped.get("snapshotId").asLong());
        assertEquals(scanBehaviorHash, pathSkipped.get("structureHash").asText());

        FIXTURE.replaceAndCommit("feature/scan-behavior",
                "service/src/main/java/com/company/loan/service/LoanApplyFacadeImpl.java",
                "        return null;",
                "        return null; ",
                "implementation-body-whitespace-change");
        JsonNode hashSkipped = postJson("/api/projects/loan/branches/scan?branch=" + enc("feature/scan-behavior"), null);
        assertEquals("skipped", hashSkipped.get("status").asText());
        assertEquals("structure hash unchanged", hashSkipped.get("message").asText());
        assertFalse(hashSkipped.get("snapshotCreated").asBoolean());
        assertEquals(scanBehaviorSnapshotId, hashSkipped.get("snapshotId").asLong());
        assertEquals(scanBehaviorHash, hashSkipped.get("structureHash").asText());

        FIXTURE.replaceAndCommit("feature/scan-behavior",
                "facade/src/main/java/com/company/loan/facade/dto/ApplyRequest.java",
                "金额，必填",
                "授信金额，必填",
                "structure-change");
        JsonNode hashChanged = postJson("/api/projects/loan/branches/scan?branch=" + enc("feature/scan-behavior"), null);
        assertEquals("success", hashChanged.get("status").asText());
        assertTrue(hashChanged.get("snapshotCreated").asBoolean());
        assertNotEquals(scanBehaviorSnapshotId, hashChanged.get("snapshotId").asLong());
        assertNotEquals(scanBehaviorHash, hashChanged.get("structureHash").asText());

        JsonNode method = getJson("/api/projects/loan/methods/submitApply-294ff142d795?branch=" + enc("feature/apply-flow"));
        assertEquals("feature/apply-flow", method.get("snapshot").get("branch").asText());
        assertEquals("submitApply", method.get("method").get("name").asText());
        assertEquals("bolt://127.0.0.1:12201", method.get("runtime").get("directUrl").asText());
        assertEquals("feature-v1", method.get("runtime").get("version").asText());
        assertEquals("apply-flow-app", method.get("runtime").get("targetAppName").asText());

        String markdown = exchange("/api/projects/loan/methods/submitApply-294ff142d795/markdown?branch=" + enc("feature/apply-flow"), HttpMethod.GET, null, String.class).getBody();
        assertNotNull(markdown);
        assertTrue(markdown.contains("贷款申请金额，必填"));
        assertTrue(markdown.contains("| `request.amount` | `java.math.BigDecimal` |"));

        JsonNode search = getJson("/api/projects/loan/search?q=" + enc("订单号"));
        assertTrue(search.size() >= 2);
        assertTrue(search.toString().contains("feature/apply-flow"));
        JsonNode fqnSearch = getJson("/api/projects/loan/search?q=" + enc("com.company.loan.facade.LoanApplyFacade/submitApply"));
        assertTrue(fqnSearch.size() >= 1);
        assertTrue(fqnSearch.toString().contains("submitApply"));
        JsonNode methodPrefixSearch = getJson("/api/projects/loan/search?q=" + enc("query"));
        assertTrue(methodPrefixSearch.toString().contains("queryStatus"));
        JsonNode methodCamelPartSearch = getJson("/api/projects/loan/search?q=" + enc("Status"));
        assertTrue(methodCamelPartSearch.toString().contains("queryStatus"));
        JsonNode fieldPathSearch = getJson("/api/projects/loan/search?q=" + enc("request.status"));
        assertTrue(fieldPathSearch.size() >= 1);
        assertTrue(fieldPathSearch.toString().contains("submitApply"));
        JsonNode symbolOnlySearch = getJson("/api/projects/loan/search?q=" + enc(":/()\"-"));
        assertEquals(0, symbolOnlySearch.size());

        JsonNode diff = getJson("/api/projects/loan/diff?branch=" + enc("feature/apply-flow") + "&base=main");
        assertEquals("Non-breaking", diff.get(0).get("kind").asText());
        assertTrue(diff.get(0).get("message").asText().contains("字段注释变化"));

        JsonNode saved = postJson("/api/projects/loan/methods/submitApply-294ff142d795/cases?branch=" + enc("feature/apply-flow") + "&service=" + enc("com.company.loan.facade.LoanApplyFacade"), "{\"name\":\"demo\",\"argsJson\":\"{\\\"orderNo\\\":\\\"T1\\\"}\"}");
        assertTrue(saved.get("id").asLong() > 0);

        String updateBody = "{\"branch\":\"feature/apply-flow\",\"service\":\"com.company.loan.facade.LoanApplyFacade\",\"methodId\":\"submitApply-294ff142d795\",\"name\":\"demo-updated\",\"argsJson\":\"{\\\"orderNo\\\":\\\"T2\\\"}\"}";
        JsonNode updated = putJson("/api/projects/loan/cases/" + saved.get("id").asLong(), updateBody);
        assertEquals(saved.get("id").asLong(), updated.get("id").asLong());

        JsonNode cases = getJson("/api/projects/loan/methods/submitApply-294ff142d795/cases?branch=" + enc("feature/apply-flow") + "&service=" + enc("com.company.loan.facade.LoanApplyFacade"));
        assertEquals("feature/apply-flow", cases.get(0).get("branch").asText());
        assertEquals("demo-updated", cases.get(0).get("name").asText());

        String invalidArgs = "{\"args\":{\"amount\":\"0.00\",\"status\":\"BAD\"}}";
        JsonNode validation = postJson("/api/projects/loan/methods/submitApply-294ff142d795/validate?branch=" + enc("feature/apply-flow"), invalidArgs);
        assertFalse(validation.get("ok").asBoolean());
        assertTrue(validation.get("errors").toString().contains("request.status must be one of"));
        assertTrue(validation.get("warnings").toString().contains("request.orderNo is required"));

        JsonNode invalidInvoke = postJson("/api/projects/loan/methods/submitApply-294ff142d795/invoke?branch=" + enc("feature/apply-flow"), invalidArgs);
        assertEquals("validation_failed", invalidInvoke.get("status").asText());
        assertEquals("bolt://127.0.0.1:12201", invalidInvoke.get("targetDirectUrl").asText());
        assertEquals("test", invalidInvoke.get("targetUniqueId").asText());
        assertEquals("feature-v1", invalidInvoke.get("targetVersion").asText());
        assertEquals("apply-flow-app", invalidInvoke.get("targetAppName").asText());
        assertEquals(1, invalidInvoke.get("validationErrors").size());
        assertTrue(invalidInvoke.get("validationWarnings").toString().contains("request.orderNo is required"));

        JsonNode probe = getJson("/api/projects/loan/methods/submitApply-294ff142d795/probe?branch=" + enc("feature/apply-flow"));
        assertFalse(probe.get("reachable").asBoolean());
        assertEquals("bolt://127.0.0.1:12201", probe.get("target").asText());

        deleteOk("/api/projects/loan/cases/" + saved.get("id").asLong());
        JsonNode emptyCases = getJson("/api/projects/loan/methods/submitApply-294ff142d795/cases?branch=" + enc("feature/apply-flow") + "&service=" + enc("com.company.loan.facade.LoanApplyFacade"));
        assertEquals(0, emptyCases.size());
    }

    private JsonNode getJson(String path) throws Exception {
        String body = exchange(path, HttpMethod.GET, null, String.class).getBody();
        return mapper.readTree(body);
    }

    private JsonNode postJson(String path, String body) throws Exception {
        String response = exchange(path, HttpMethod.POST, body, String.class).getBody();
        return mapper.readTree(response);
    }

    private JsonNode putJson(String path, String body) throws Exception {
        String response = exchange(path, HttpMethod.PUT, body, String.class).getBody();
        return mapper.readTree(response);
    }

    private void deleteOk(String path) throws Exception {
        exchange(path, HttpMethod.DELETE, null, String.class);
    }

    private int countFailedReports(String project, String branch) throws Exception {
        try (Connection c = store.connect(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM scan_reports WHERE project = ? AND branch = ? AND status = 'failed'")) {
            ps.setString(1, project);
            ps.setString(2, branch);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, String body, Class<T> responseType) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request;
        if (method == HttpMethod.POST) {
            request = post(path);
        } else if (method == HttpMethod.PUT) {
            request = put(path);
        } else if (method == HttpMethod.DELETE) {
            request = delete(path);
        } else {
            request = get(path);
        }
        request.header(HttpHeaders.AUTHORIZATION, "Bearer admin-token");
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        String response = mvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new ResponseEntity<>(responseType.cast(response), HttpStatus.OK);
    }

    private static String enc(String value) throws Exception {
        return value;
    }

    private JsonNode findProject(JsonNode config, String id) {
        for (JsonNode project : config.get("projects")) {
            if (id.equals(project.get("id").asText())) return project;
        }
        fail("project not found: " + id);
        return null;
    }

    private static class RuntimeFixture {
        final Path root;
        final Path repo;
        final Path config;

        RuntimeFixture(Path root, Path repo, Path config) {
            this.root = root;
            this.repo = repo;
            this.config = config;
        }

        static RuntimeFixture create() {
            try {
                Path root = Files.createTempDirectory("sofa-facade-doc-api-");
                Path repo = root.resolve("repo");
                Files.createDirectories(repo);
                copy(Paths.get("src/test/resources/fixture/loan/facade"), repo.resolve("facade"));
                copy(Paths.get("src/test/resources/fixture/loan/service"), repo.resolve("service"));
                git(repo, "init", "-b", "main");
                git(repo, "add", ".");
                git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "initial");

                git(repo, "checkout", "-b", "feature/apply-flow");
                replace(repo.resolve("facade/src/main/java/com/company/loan/facade/dto/ApplyRequest.java"), "申请金额", "贷款申请金额");
                git(repo, "add", "facade/src/main/java/com/company/loan/facade/dto/ApplyRequest.java");
                git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "feature-doc-change");

                git(repo, "checkout", "main");
                git(repo, "checkout", "-b", "feature/wip-skip");
                replace(repo.resolve("facade/src/main/java/com/company/loan/facade/dto/ApplyRequest.java"), "订单号", "订单编号");
                git(repo, "add", "facade/src/main/java/com/company/loan/facade/dto/ApplyRequest.java");
                git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "wip-change");
                git(repo, "checkout", "main");

                git(repo, "checkout", "-b", "feature/no-facade");
                git(repo, "rm",
                        "facade/src/main/java/com/company/loan/facade/LoanApplyFacade.java",
                        "facade/src/main/java/com/company/loan/facade/LoanQueryFacade.java",
                        "service/src/main/java/com/company/loan/service/LoanApplyFacadeImpl.java",
                        "service/src/main/resources/META-INF/spring/sofa-services.xml");
                Path keepJava = repo.resolve("service/src/main/java/com/company/loan/service/KeepAlive.java");
                Files.createDirectories(keepJava.getParent());
                Files.write(keepJava, "package com.company.loan.service;\npublic class KeepAlive {}\n".getBytes(StandardCharsets.UTF_8));
                Path keepResource = repo.resolve("service/src/main/resources/application.properties");
                Files.createDirectories(keepResource.getParent());
                Files.write(keepResource, "# keep resource root\n".getBytes(StandardCharsets.UTF_8));
                git(repo, "add", "service/src/main/java/com/company/loan/service/KeepAlive.java", "service/src/main/resources/application.properties");
                git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", "remove-facades");
                git(repo, "checkout", "main");

                Path config = root.resolve("config.yml");
                String yaml = "" +
                        "server:\n" +
                        "  listen: \"127.0.0.1:0\"\n" +
                        "  dataDir: \"" + root.resolve("data").toString().replace("\\", "\\\\") + "\"\n" +
                        "auth:\n" +
                        "  adminTokens: [\"admin-token\"]\n" +
                        "teams:\n" +
                        "  loan-team:\n" +
                        "    displayName: \"贷款团队\"\n" +
                        "    tokens: [\"loan-team-token\"]\n" +
                        "  card-team:\n" +
                        "    displayName: \"卡团队\"\n" +
                        "    tokens: [\"card-team-token\"]\n" +
                        "projects:\n" +
                        "  loan:\n" +
                        "    team: \"loan-team\"\n" +
                        "    displayName: \"贷款服务\"\n" +
                        "    repo: \"" + repo.toString().replace("\\", "\\\\") + "\"\n" +
                        "    baselineBranch: \"main\"\n" +
                        "    tokens: [\"loan-token\"]\n" +
                        "    branchDefaults:\n" +
                        "      directUrl: \"bolt://127.0.0.1:12200\"\n" +
                        "      springProfiles: [\"test\"]\n" +
                        "    branches:\n" +
                        "      include: [\"main\", \"feature/*\"]\n" +
                        "      exclude: [\"feature/wip-*\"]\n" +
                        "      maxMatched: 20\n" +
                        "    sourceRoots: [\"facade/src/main/java\", \"service/src/main/java\"]\n" +
                        "    resourceRoots: [\"service/src/main/resources\"]\n" +
                        "    facadePackages: [\"com.company.loan.facade\"]\n" +
                        "    branchOverrides:\n" +
                        "      \"feature/*\":\n" +
                        "        directUrl: \"bolt://127.0.0.1:12201\"\n" +
                        "        targetAppName: \"feature-app\"\n" +
                        "      \"feature/apply-flow\":\n" +
                        "        version: \"feature-v1\"\n" +
                        "        targetAppName: \"apply-flow-app\"\n" +
                        "  card:\n" +
                        "    team: \"card-team\"\n" +
                        "    displayName: \"卡服务\"\n" +
                        "    repo: \"" + repo.toString().replace("\\", "\\\\") + "\"\n" +
                        "    baselineBranch: \"main\"\n" +
                        "    tokens: [\"card-token\"]\n" +
                        "    branchDefaults:\n" +
                        "      directUrl: \"bolt://127.0.0.1:12202\"\n" +
                        "      springProfiles: [\"test\"]\n" +
                        "    branches:\n" +
                        "      include: [\"main\"]\n" +
                        "      exclude: []\n" +
                        "      maxMatched: 20\n" +
                        "    sourceRoots: [\"facade/src/main/java\", \"service/src/main/java\"]\n" +
                        "    resourceRoots: [\"service/src/main/resources\"]\n" +
                        "    facadePackages: [\"com.company.loan.facade\"]\n";
                Files.write(config, yaml.getBytes(StandardCharsets.UTF_8));
                return new RuntimeFixture(root, repo, config);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static void copy(Path from, Path to) throws Exception {
            Files.walk(from).forEach(source -> {
                try {
                    Path target = to.resolve(from.relativize(source).toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        private static void replace(Path file, String from, String to) throws Exception {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Files.write(file, text.replace(from, to).getBytes(StandardCharsets.UTF_8));
        }

        void createBranchFrom(String branch, String from) throws Exception {
            git(repo, "checkout", from);
            git(repo, "checkout", "-B", branch);
        }

        void commitText(String branch, String relativePath, String text, String message) throws Exception {
            git(repo, "checkout", branch);
            Path file = repo.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            git(repo, "add", relativePath);
            git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", message);
        }

        void replaceAndCommit(String branch, String relativePath, String from, String to, String message) throws Exception {
            git(repo, "checkout", branch);
            Path file = repo.resolve(relativePath);
            replace(file, from, to);
            git(repo, "add", relativePath);
            git(repo, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-m", message);
        }

        private static void git(Path dir, String... args) throws Exception {
            String[] command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);
            Process p = new ProcessBuilder(command).directory(dir.toFile()).start();
            byte[] out = readAll(p.getInputStream());
            byte[] err = readAll(p.getErrorStream());
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException(new String(out, StandardCharsets.UTF_8) + new String(err, StandardCharsets.UTF_8));
            }
        }

        private static byte[] readAll(InputStream in) throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
