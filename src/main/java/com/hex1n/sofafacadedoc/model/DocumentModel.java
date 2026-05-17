package com.hex1n.sofafacadedoc.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DocumentModel {
    public static class Document {
        public String project;
        public String branch;
        public String commit;
        public String generatedAt;
        public Diagnostics diagnostics = new Diagnostics();
        public List<ServiceDoc> services = new ArrayList<>();
    }

    public static class Diagnostics {
        public String status = "success";
        public List<String> messages = new ArrayList<>();
        public int parsedFiles;
        public List<String> failedFiles = new ArrayList<>();
        public List<String> sourceRoots = new ArrayList<>();
        public List<String> resourceRoots = new ArrayList<>();
    }

    public static class ServiceDoc {
        public String fqn;
        public String comment;
        public String status;
        public String sourcePath;
        public int sourceLine;
        public boolean deprecated;
        public List<PublishRecord> publishRecords = new ArrayList<>();
        public List<MethodDoc> methods = new ArrayList<>();
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String changeKind;
    }

    public static class PublishRecord {
        public String source;
        public String interfaceName;
        public String implementation;
        public String binding;
        public String uniqueId;
        public String version;
        public String serializeType;
        public String timeout;
        public String sourcePath;
        public int sourceLine;
        public boolean incomplete;
        public String incompleteReason;
    }

    public static class MethodDoc {
        public String id;
        public String name;
        public String comment;
        public Map<String, String> paramComments = new LinkedHashMap<>();
        public String returnComment;
        public boolean deprecated;
        public List<ParamDoc> params = new ArrayList<>();
        public String returnType;
        public FieldNode returnTree;
        public Object returnExample;
        public Object requestExample;
        public List<String> throwsTypes = new ArrayList<>();
        public String sourcePath;
        public int sourceLine;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String changeKind;
    }

    public static class ParamDoc {
        public String name;
        public String javaType;
        public String jsonType;
        public String comment;
        public String required;
        public FieldNode tree;
        public Object example;
    }

    public static class FieldNode {
        public String path;
        public String name;
        public String jsonName;
        public String javaType;
        public String jsonType;
        public String comment;
        public String required;
        public List<String> constraints = new ArrayList<>();
        public List<EnumValue> enumValues = new ArrayList<>();
        public List<FieldNode> children = new ArrayList<>();
        public boolean truncated;
        public String note;
        public String sourcePath;
        public int sourceLine;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String changeKind;
    }

    public static class EnumValue {
        public String name;
        public String comment;
    }

    public static class ScanResult {
        public long snapshotId;
        public String commit;
        public String structureHash;
        public String status;
        public String message;
        public boolean snapshotCreated;
        public Document document;
    }

    public static class SavedCase {
        public long id;
        public String project;
        public String branch;
        public String service;
        public String methodId;
        public String name;
        public String note;
        public String argsJson;
        public String createdAt;
        public String updatedAt;
    }

    public static class DiffChange {
        public String kind;
        public String path;
        public String message;

        public DiffChange() {
        }

        public DiffChange(String kind, String path, String message) {
            this.kind = kind;
            this.path = path;
            this.message = message;
        }
    }
}
