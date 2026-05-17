package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.config.AppConfig;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SourceScanner {
    private final SourceRootResolver rootResolver;
    private final JavaSourceIndexer sourceIndexer;
    private final SofaXmlPublishParser xmlPublishParser;
    private final FacadeDocumentAssembler documentAssembler;
    private final DocumentStructureHasher structureHasher;

    public SourceScanner(SourceRootResolver rootResolver, JavaSourceIndexer sourceIndexer, SofaXmlPublishParser xmlPublishParser, FacadeDocumentAssembler documentAssembler, DocumentStructureHasher structureHasher) {
        this.rootResolver = rootResolver;
        this.sourceIndexer = sourceIndexer;
        this.xmlPublishParser = xmlPublishParser;
        this.documentAssembler = documentAssembler;
        this.structureHasher = structureHasher;
    }

    public ScanOutput scan(String project, String branch, String commit, Path root, AppConfig.EffectiveBranch cfg) throws Exception {
        SourceRootResolver.ResolvedRoots roots = rootResolver.resolve(root, cfg);
        JavaSourceIndex index = sourceIndexer.index(root, roots.sourceRoots);
        index.publishRecords.addAll(xmlPublishParser.parse(root, roots.resourceRoots, cfg.springProfiles));

        DocumentModel.Document doc;
        try (BytecodeJarSet jars = new BytecodeJarSet(root, cfg.dependencyJars)) {
            doc = documentAssembler.assemble(project, branch, commit, cfg, index, jars);
            if (!jars.unresolvedPatterns().isEmpty()) {
                doc.diagnostics.messages.add("dependencyJars unresolved: " + String.join(", ", jars.unresolvedPatterns()));
            }
            if (!jars.conflicts().isEmpty()) {
                doc.diagnostics.messages.add("dependencyJars duplicate class entries: " + jars.conflicts().size());
            }
        }
        doc.diagnostics.sourceRoots = roots.sourceRootNames;
        doc.diagnostics.resourceRoots = roots.resourceRootNames;
        doc.diagnostics.parsedFiles = index.parsedFiles;
        doc.diagnostics.failedFiles = index.failedFiles;
        if (!index.failedFiles.isEmpty()) {
            doc.diagnostics.status = "partial";
            doc.diagnostics.messages.add("some Java files could not be parsed");
        }
        if (doc.services.isEmpty()) {
            throw new IllegalStateException("no facade service found; check sourceRoots/facadePackages/resourceRoots");
        }
        ScanOutput out = new ScanOutput();
        out.document = doc;
        out.structureHash = structureHasher.hash(doc);
        return out;
    }

    public static class ScanOutput {
        public DocumentModel.Document document;
        public String structureHash;
    }
}
