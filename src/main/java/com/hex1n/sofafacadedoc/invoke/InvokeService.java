package com.hex1n.sofafacadedoc.invoke;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InvokeService {
    private final GenericArgumentConverter arguments;
    private final InvocationValidator validator;
    private final GenericResponseNormalizer responses;
    private final SofaRpcGenericClient client;

    public InvokeService() {
        this.arguments = new GenericArgumentConverter();
        this.validator = new InvocationValidator(arguments);
        this.responses = new GenericResponseNormalizer();
        this.client = new SofaRpcGenericClient();
    }

    @Autowired
    public InvokeService(GenericArgumentConverter arguments, InvocationValidator validator, GenericResponseNormalizer responses, SofaRpcGenericClient client) {
        this.arguments = arguments;
        this.validator = validator;
        this.responses = responses;
        this.client = client;
    }

    public ProbeResult probe(String directUrl) {
        return client.probe(directUrl);
    }

    public InvokeResult invoke(String serviceName, DocumentModel.MethodDoc method, AppConfig.EffectiveBranch branchCfg, Map<String, Object> body) {
        Instant start = Instant.now();
        InvokeResult r = new InvokeResult();
        r.targetService = serviceName;
        r.targetDirectUrl = branchCfg.directUrl;
        r.targetUniqueId = branchCfg.uniqueId;
        r.targetVersion = branchCfg.version;
        r.targetAppName = branchCfg.targetAppName;
        try {
            ValidateResult validation = validate(method, body);
            if (!validation.ok) {
                r.ok = false;
                r.status = "validation_failed";
                r.error = "validation failed";
                r.validationErrors = validation.errors;
                r.validationWarnings = validation.warnings;
                return r;
            }
            ProbeResult probe = probe(branchCfg.directUrl);
            if (!probe.reachable) {
                r.ok = false;
                r.status = "unreachable";
                r.error = "目标服务不可连接：" + (probe.error == null || probe.error.trim().isEmpty() ? "unknown error" : probe.error);
                r.validationWarnings = validation.warnings;
                return r;
            }
            Object argsObject = body == null ? null : body.get("args");
            Object[] args = genericArgs(method, argsObject);
            Object response = client.invoke(serviceName, method, branchCfg, args);
            r.ok = true;
            r.status = "success";
            r.validationWarnings = validation.warnings;
            r.response = normalizeResponse(response);
        } catch (Exception e) {
            r.ok = false;
            r.status = "failed";
            r.error = e.getMessage();
        } finally {
            r.elapsedMs = Duration.between(start, Instant.now()).toMillis();
        }
        return r;
    }

    Object[] genericArgs(DocumentModel.MethodDoc method, Object argsObject) {
        return arguments.genericArgs(method, argsObject);
    }

    public ValidateResult validate(DocumentModel.MethodDoc method, Map<String, Object> body) {
        return validator.validate(method, body);
    }

    Object normalizeResponse(Object value) {
        return responses.normalize(value);
    }

    public static class ProbeResult {
        public String target;
        public boolean reachable;
        public String error;
        public long latencyMs;
    }

    public static class InvokeResult {
        public boolean ok;
        public String status;
        public long elapsedMs;
        public String error;
        public String targetService;
        public String targetDirectUrl;
        public String targetUniqueId;
        public String targetVersion;
        public String targetAppName;
        public List<String> validationErrors = new ArrayList<>();
        public List<String> validationWarnings = new ArrayList<>();
        public Object response;
    }

    public static class ValidateResult {
        public boolean ok;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }
}
