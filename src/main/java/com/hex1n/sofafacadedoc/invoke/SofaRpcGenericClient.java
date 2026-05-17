package com.hex1n.sofafacadedoc.invoke;

import com.alipay.sofa.rpc.api.GenericService;
import com.alipay.sofa.rpc.config.ApplicationConfig;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Component
public class SofaRpcGenericClient {
    public InvokeService.ProbeResult probe(String directUrl) {
        Instant start = Instant.now();
        InvokeService.ProbeResult out = new InvokeService.ProbeResult();
        out.target = directUrl;
        try {
            HostPort hp = hostPort(directUrl);
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(hp.host, hp.port), 1500);
            socket.close();
            out.reachable = true;
        } catch (Exception e) {
            out.error = e.getMessage();
        }
        out.latencyMs = Duration.between(start, Instant.now()).toMillis();
        return out;
    }

    public Object invoke(String serviceName, DocumentModel.MethodDoc method, AppConfig.EffectiveBranch branchCfg, Object[] args) {
        ConsumerConfig<GenericService> cfg = null;
        try {
            String[] argTypes = new String[method.params.size()];
            for (int i = 0; i < method.params.size(); i++) argTypes[i] = method.params.get(i).javaType;
            cfg = new ConsumerConfig<GenericService>()
                    .setInterfaceId(serviceName)
                    .setProtocol("bolt")
                    .setSerialization("hessian2")
                    .setGeneric(true)
                    .setDirectUrl(branchCfg.directUrl)
                    .setTimeout(3000)
                    .setConnectTimeout(1000);
            if (branchCfg.uniqueId != null && !branchCfg.uniqueId.trim().isEmpty()) cfg.setUniqueId(branchCfg.uniqueId);
            if (branchCfg.version != null && !branchCfg.version.trim().isEmpty()) cfg.setVersion(branchCfg.version);
            if (branchCfg.targetAppName != null && !branchCfg.targetAppName.trim().isEmpty()) {
                cfg.setApplication(new ApplicationConfig().setAppName(branchCfg.targetAppName));
            }
            GenericService proxy = cfg.refer();
            return proxy.$genericInvoke(method.name, argTypes, args);
        } finally {
            if (cfg != null) {
                try {
                    cfg.unRefer();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private HostPort hostPort(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("directUrl is empty");
        if (!raw.contains("://")) raw = "bolt://" + raw;
        URI uri = new URI(raw);
        HostPort hp = new HostPort();
        hp.host = uri.getHost();
        hp.port = uri.getPort() > 0 ? uri.getPort() : 12200;
        if (hp.host == null) throw new IllegalArgumentException("directUrl host is empty");
        return hp;
    }

    private static class HostPort {
        String host;
        int port;
    }
}
