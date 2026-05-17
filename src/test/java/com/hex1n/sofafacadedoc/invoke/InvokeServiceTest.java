package com.hex1n.sofafacadedoc.invoke;

import com.alipay.hessian.generic.model.GenericArray;
import com.alipay.hessian.generic.model.GenericCollection;
import com.alipay.hessian.generic.model.GenericMap;
import com.alipay.hessian.generic.model.GenericObject;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InvokeServiceTest {
    @Test
    void convertsDtoAndEnumToSofaGenericObjects() {
        InvokeService service = new InvokeService();
        DocumentModel.MethodDoc method = method(param("request", requestTree("request")));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", "A001");
        request.put("amount", "12.34");
        request.put("status", "NEW");

        Object[] args = service.genericArgs(method, request);

        assertEquals(1, args.length);
        GenericObject dto = (GenericObject) args[0];
        assertEquals("com.company.loan.facade.dto.ApplyRequest", dto.getType());
        assertEquals("A001", dto.getField("orderNo"));
        assertEquals(new BigDecimal("12.34"), dto.getField("amount"));
        GenericObject status = (GenericObject) dto.getField("status");
        assertEquals("com.company.loan.facade.dto.ApplyStatus", status.getType());
        assertEquals("NEW", status.getField("name"));
    }

    @Test
    void convertsCollectionsAndMapsRecursively() {
        InvokeService service = new InvokeService();
        DocumentModel.FieldNode list = node("requests", "requests", "java.util.List<com.company.loan.facade.dto.ApplyRequest>", "array");
        list.children.add(requestTree("requests.items"));
        DocumentModel.FieldNode map = node("requestMap", "requestMap", "java.util.Map<java.lang.String, com.company.loan.facade.dto.ApplyRequest>", "object");
        map.children.add(requestTree("requestMap.<key>"));
        DocumentModel.MethodDoc method = method(param("requests", list), param("requestMap", map));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderNo", "A001");
        item.put("amount", "12.34");
        item.put("status", "NEW");
        Map<String, Object> item2 = new LinkedHashMap<>(item);
        item2.put("orderNo", "A002");
        Map<String, Object> argMap = new LinkedHashMap<>();
        argMap.put("first", item2);

        Object[] args = service.genericArgs(method, Arrays.asList(Collections.singletonList(item), argMap));

        List<?> convertedList = (List<?>) args[0];
        assertEquals("com.company.loan.facade.dto.ApplyRequest", ((GenericObject) convertedList.get(0)).getType());
        @SuppressWarnings("unchecked")
        Map<String, Object> convertedMap = (Map<String, Object>) args[1];
        GenericObject convertedValue = (GenericObject) convertedMap.get("first");
        assertEquals("A002", convertedValue.getField("orderNo"));
    }

    @Test
    void convertsDateAndNumberScalars() {
        InvokeService service = new InvokeService();
        DocumentModel.MethodDoc method = method(
                param("day", node("day", "day", "java.time.LocalDate", "string date/time")),
                param("count", node("count", "count", "java.lang.Integer", "number"))
        );

        Object[] args = service.genericArgs(method, Arrays.asList("2026-05-17", "7"));

        assertEquals(LocalDate.of(2026, 5, 17), args[0]);
        assertEquals(7, args[1]);
    }

    @Test
    void treatsRequiredMissingAndUnknownFieldsAsWarnings() {
        InvokeService service = new InvokeService();
        DocumentModel.FieldNode tree = requestTree("request");
        tree.children.get(0).required = "是";
        DocumentModel.MethodDoc method = method(param("request", tree));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", "12.34");
        request.put("status", "NEW");
        request.put("extra", "allowed");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", request);

        InvokeService.ValidateResult result = service.validate(method, body);

        assertTrue(result.ok);
        assertTrue(result.errors.isEmpty());
        assertTrue(result.warnings.contains("request.orderNo is required"));
        assertTrue(result.warnings.contains("request.extra is unknown"));
    }

    @Test
    void keepsEnumTypeErrorsBlocking() {
        InvokeService service = new InvokeService();
        DocumentModel.MethodDoc method = method(param("request", requestTree("request")));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", "A001");
        request.put("amount", "12.34");
        request.put("status", "BAD");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("args", request);

        InvokeService.ValidateResult result = service.validate(method, body);

        assertFalse(result.ok);
        assertTrue(result.errors.get(0).contains("request.status must be one of"));
    }

    @Test
    void normalizesGenericResponsesToJsonFriendlyMaps() {
        InvokeService service = new InvokeService();
        GenericObject response = new GenericObject("com.company.loan.facade.dto.ApplyResponse");
        response.putField("success", true);
        GenericObject status = new GenericObject("com.company.loan.facade.dto.ApplyStatus");
        status.putField("name", "NEW");
        response.putField("status", status);
        GenericCollection collection = new GenericCollection("com.company.CustomList");
        collection.setCollection(Collections.singletonList(response));
        GenericMap map = new GenericMap("com.company.CustomMap");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("items", collection);
        map.setMap(raw);
        GenericArray array = new GenericArray("java.lang.String");
        array.setObjects(new Object[]{"A", "B"});

        Object normalizedMap = service.normalizeResponse(map);
        Object normalizedArray = service.normalizeResponse(array);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) normalizedMap;
        assertEquals("com.company.CustomMap", out.get("_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) out.get("entries");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) entries.get("items");
        assertEquals("com.company.CustomList", items.get("_type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) items.get("items");
        assertEquals("com.company.loan.facade.dto.ApplyResponse", list.get(0).get("_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedStatus = (Map<String, Object>) list.get(0).get("status");
        assertEquals("NEW", nestedStatus.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> arrayOut = (Map<String, Object>) normalizedArray;
        assertEquals("java.lang.String", arrayOut.get("componentType"));
        assertEquals(Arrays.asList("A", "B"), arrayOut.get("items"));
    }

    @Test
    void returnsUnreachableBeforeCallingSofaRpcWhenDirectUrlCannotConnect() {
        FakeClient client = new FakeClient(false);
        InvokeService service = new InvokeService(new GenericArgumentConverter(), new InvocationValidator(new GenericArgumentConverter()), new GenericResponseNormalizer(), client);
        DocumentModel.MethodDoc method = method(param("request", requestTree("request")));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", "A001");
        request.put("amount", "12.34");
        request.put("status", "NEW");
        com.hex1n.sofafacadedoc.config.AppConfig.EffectiveBranch branch = new com.hex1n.sofafacadedoc.config.AppConfig.EffectiveBranch();
        branch.directUrl = "bolt://127.0.0.1:12200";

        InvokeService.InvokeResult result = service.invoke("com.company.loan.facade.LoanApplyFacade", method, branch, Collections.singletonMap("args", request));

        assertFalse(result.ok);
        assertEquals("unreachable", result.status);
        assertTrue(result.error.contains("目标服务不可连接"), result.error);
        assertFalse(client.invoked);
    }

    private DocumentModel.MethodDoc method(DocumentModel.ParamDoc... params) {
        DocumentModel.MethodDoc method = new DocumentModel.MethodDoc();
        method.name = "submit";
        method.params.addAll(Arrays.asList(params));
        return method;
    }

    private DocumentModel.ParamDoc param(String name, DocumentModel.FieldNode tree) {
        DocumentModel.ParamDoc param = new DocumentModel.ParamDoc();
        param.name = name;
        param.javaType = tree.javaType;
        param.tree = tree;
        return param;
    }

    private DocumentModel.FieldNode requestTree(String path) {
        DocumentModel.FieldNode request = node(path, leaf(path), "com.company.loan.facade.dto.ApplyRequest", "object");
        request.children.add(node(path + ".orderNo", "orderNo", "java.lang.String", "string"));
        request.children.add(node(path + ".amount", "amount", "java.math.BigDecimal", "string decimal"));
        DocumentModel.FieldNode status = node(path + ".status", "status", "com.company.loan.facade.dto.ApplyStatus", "enum string");
        DocumentModel.EnumValue ev = new DocumentModel.EnumValue();
        ev.name = "NEW";
        status.enumValues.add(ev);
        request.children.add(status);
        return request;
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

    private String leaf(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static class FakeClient extends SofaRpcGenericClient {
        private final boolean reachable;
        private boolean invoked;

        FakeClient(boolean reachable) {
            this.reachable = reachable;
        }

        @Override
        public InvokeService.ProbeResult probe(String directUrl) {
            InvokeService.ProbeResult result = new InvokeService.ProbeResult();
            result.target = directUrl;
            result.reachable = reachable;
            if (!reachable) result.error = "Connection refused";
            return result;
        }

        @Override
        public Object invoke(String serviceName, DocumentModel.MethodDoc method, com.hex1n.sofafacadedoc.config.AppConfig.EffectiveBranch branchCfg, Object[] args) {
            invoked = true;
            return Collections.emptyMap();
        }
    }
}
