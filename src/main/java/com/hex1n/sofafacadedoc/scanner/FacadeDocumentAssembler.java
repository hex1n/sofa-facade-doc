package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@Component
public class FacadeDocumentAssembler {
    private final FacadePayloadTreeBuilder payloadTreeBuilder;

    public FacadeDocumentAssembler(FacadePayloadTreeBuilder payloadTreeBuilder) {
        this.payloadTreeBuilder = payloadTreeBuilder;
    }

    public DocumentModel.Document assemble(String project, String branch, String commit, AppConfig.EffectiveBranch cfg, JavaSourceIndex index, BytecodeJarSet jars) {
        Map<String, DocumentModel.ServiceDoc> services = new TreeMap<>();
        for (DocumentModel.PublishRecord pr : index.publishRecords) {
            if (pr.interfaceName == null || pr.interfaceName.trim().isEmpty()) continue;
            DocumentModel.ServiceDoc svc = services.computeIfAbsent(pr.interfaceName, this::service);
            svc.publishRecords.add(pr);
            svc.status = "published";
        }
        for (JavaSourceIndex.JavaClassInfo cls : index.classes.values()) {
            if ("interface".equals(cls.kind) && matchesFacade(cls.fqn, cfg.facadePackages)) {
                DocumentModel.ServiceDoc svc = services.computeIfAbsent(cls.fqn, this::service);
                if (svc.status == null) svc.status = "candidate";
            }
        }
        DocumentModel.Document doc = new DocumentModel.Document();
        doc.project = project;
        doc.branch = branch;
        doc.commit = commit;
        doc.generatedAt = Instant.now().toString();
        for (DocumentModel.ServiceDoc svc : services.values()) {
            JavaSourceIndex.JavaClassInfo cls = index.classes.get(svc.fqn);
            if (cls == null) {
                svc.status = "source_missing";
                doc.services.add(svc);
                continue;
            }
            svc.comment = cls.comment;
            svc.deprecated = cls.deprecated;
            svc.sourcePath = cls.sourcePath;
            svc.sourceLine = cls.sourceLine;
            svc.methods.addAll(payloadTreeBuilder.buildMethods(index, cls, jars));
            doc.services.add(svc);
        }
        return doc;
    }

    private DocumentModel.ServiceDoc service(String fqn) {
        DocumentModel.ServiceDoc svc = new DocumentModel.ServiceDoc();
        svc.fqn = fqn;
        return svc;
    }

    private boolean matchesFacade(String fqn, java.util.List<String> pkgs) {
        if (pkgs == null) return false;
        for (String p : pkgs) {
            String prefix = p.replace(".*", "");
            if (fqn.equals(prefix) || fqn.startsWith(prefix + ".")) return true;
        }
        return false;
    }
}
