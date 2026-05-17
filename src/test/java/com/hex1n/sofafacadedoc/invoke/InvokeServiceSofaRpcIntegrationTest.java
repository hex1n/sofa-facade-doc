package com.hex1n.sofafacadedoc.invoke;

import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.invoke.fixture.EchoFacade;
import com.hex1n.sofafacadedoc.invoke.fixture.EchoFacadeImpl;
import com.hex1n.sofafacadedoc.invoke.fixture.EchoRequest;
import com.hex1n.sofafacadedoc.invoke.fixture.EchoResponse;
import com.hex1n.sofafacadedoc.invoke.fixture.EchoStatus;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InvokeServiceSofaRpcIntegrationTest {
    private ProviderConfig<EchoFacade> provider;

    @AfterEach
    void stopProvider() {
        if (provider != null) {
            provider.unExport();
            provider = null;
        }
    }

    @Test
    void invokesLocalBoltProviderWithGenericArguments() throws Exception {
        int port = freePort();
        String uniqueId = "it-" + port;
        exportProvider(port, uniqueId);

        InvokeService service = new InvokeService();
        AppConfig.EffectiveBranch branch = branch(port, uniqueId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", "A001");
        request.put("amount", "12.34");
        request.put("status", "NEW");
        Map<String, Object> body = Collections.singletonMap("args", request);

        InvokeService.ProbeResult probe = service.probe(branch.directUrl);
        assertTrue(probe.reachable, probe.error);

        InvokeService.InvokeResult result = service.invoke(EchoFacade.class.getName(), method(), branch, body);

        assertTrue(result.ok, result.error);
        assertEquals("success", result.status);
        assertEquals(EchoFacade.class.getName(), result.targetService);
        assertEquals(uniqueId, result.targetUniqueId);
        assertEquals(branch.directUrl, result.targetDirectUrl);
        assertNotNull(result.response);
        assertTrue(result.response instanceof Map, "response should be JSON-friendly map but was " + result.response.getClass());
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result.response;
        assertEquals(EchoResponse.class.getName(), response.get("_type"));
        assertEquals(Boolean.TRUE, response.get("success"));
        assertEquals("A001", response.get("orderNo"));
        assertEquals(new BigDecimal("12.34"), response.get("amount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) response.get("status");
        assertEquals(EchoStatus.class.getName(), status.get("_type"));
        assertEquals("NEW", status.get("name"));
    }

    @Test
    void reportsProviderBusinessException() throws Exception {
        int port = freePort();
        String uniqueId = "it-" + port;
        exportProvider(port, uniqueId);

        InvokeService service = new InvokeService();
        AppConfig.EffectiveBranch branch = branch(port, uniqueId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", "FAIL");
        request.put("amount", "12.34");
        request.put("status", "NEW");

        InvokeService.InvokeResult result = service.invoke(EchoFacade.class.getName(), method(), branch, Collections.singletonMap("args", request));

        assertFalse(result.ok);
        assertEquals("failed", result.status);
        assertTrue(result.error.contains("business rejected FAIL"), result.error);
        assertEquals(uniqueId, result.targetUniqueId);
    }

    @Test
    void reportsProtocolDecodeFailureFromReachableNonSofaEndpoint() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(200);
            AtomicBoolean running = new AtomicBoolean(true);
            Thread fakeProvider = new Thread(() -> serveInvalidProtocol(server, running), "invalid-sofa-provider");
            fakeProvider.setDaemon(true);
            fakeProvider.start();

            InvokeService service = new InvokeService();
            AppConfig.EffectiveBranch branch = branch(server.getLocalPort(), "invalid-" + server.getLocalPort());
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("orderNo", "A001");
            request.put("amount", "12.34");
            request.put("status", "NEW");

            InvokeService.ProbeResult probe = service.probe(branch.directUrl);
            assertTrue(probe.reachable, probe.error);

            InvokeService.InvokeResult result = service.invoke(EchoFacade.class.getName(), method(), branch, Collections.singletonMap("args", request));

            running.set(false);
            fakeProvider.join(1000);
            assertFalse(result.ok);
            assertEquals("failed", result.status);
            assertEquals(branch.directUrl, result.targetDirectUrl);
            assertNotNull(result.error);
            assertFalse(result.error.trim().isEmpty());
        }
    }

    private void exportProvider(int port, String uniqueId) {
        ServerConfig server = new ServerConfig()
                .setProtocol("bolt")
                .setPort(port)
                .setSerialization("hessian2")
                .setDaemon(true);
        provider = new ProviderConfig<EchoFacade>()
                .setInterfaceId(EchoFacade.class.getName())
                .setRef(new EchoFacadeImpl())
                .setUniqueId(uniqueId)
                .setServer(server)
                .setSerialization("hessian2")
                .setRegister(false);
        provider.export();
    }

    private AppConfig.EffectiveBranch branch(int port, String uniqueId) {
        AppConfig.EffectiveBranch branch = new AppConfig.EffectiveBranch();
        branch.directUrl = "bolt://127.0.0.1:" + port;
        branch.uniqueId = uniqueId;
        return branch;
    }

    private DocumentModel.MethodDoc method() {
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.name = "submit";
        method.params.add(param("request", requestTree()));
        method.returnType = EchoResponse.class.getName();
        method.returnTree = responseTree();
        return method;
    }

    private DocumentModel.ParamDoc param(String name, DocumentModel.FieldNode tree) {
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = name;
        param.javaType = tree.javaType;
        param.tree = tree;
        return param;
    }

    private DocumentModel.FieldNode requestTree() {
        DocumentModel.FieldNode request = node("request", "request", EchoRequest.class.getName(), "object");
        request.children.add(node("request.orderNo", "orderNo", String.class.getName(), "string"));
        request.children.add(node("request.amount", "amount", BigDecimal.class.getName(), "string decimal"));
        DocumentModel.FieldNode status = node("request.status", "status", EchoStatus.class.getName(), "enum string");
        DocumentModel.EnumValue ev = new DocumentModel.EnumValue();
        ev.name = "NEW";
        status.enumValues.add(ev);
        request.children.add(status);
        return request;
    }

    private DocumentModel.FieldNode responseTree() {
        DocumentModel.FieldNode response = node("return", "return", EchoResponse.class.getName(), "object");
        response.children.add(node("return.success", "success", Boolean.class.getName(), "boolean"));
        response.children.add(node("return.orderNo", "orderNo", String.class.getName(), "string"));
        response.children.add(node("return.amount", "amount", BigDecimal.class.getName(), "string decimal"));
        response.children.add(node("return.status", "status", EchoStatus.class.getName(), "enum string"));
        return response;
    }

    private DocumentModel.FieldNode node(String path, String name, String javaType, String jsonType) {
        DocumentModel.FieldNode node = new DocumentModel.FieldNode();
        node.path = path;
        node.name = name;
        node.javaType = javaType;
        node.jsonType = jsonType;
        node.required = "未知";
        return node;
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private void serveInvalidProtocol(ServerSocket server, AtomicBoolean running) {
        while (running.get() && !server.isClosed()) {
            try (Socket socket = server.accept()) {
                socket.getOutputStream().write("not-a-sofa-bolt-hessian2-response".getBytes("UTF-8"));
                socket.getOutputStream().flush();
            } catch (SocketTimeoutException ignored) {
            } catch (Exception ignored) {
                return;
            }
        }
    }
}
