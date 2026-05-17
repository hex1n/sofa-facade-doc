package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaSourceIndex {
    public final Path root;
    public final Map<String, JavaClassInfo> classes = new LinkedHashMap<>();
    public final Map<String, String> simpleNames = new LinkedHashMap<>();
    public final List<DocumentModel.PublishRecord> publishRecords = new ArrayList<>();
    public final List<String> failedFiles = new ArrayList<>();
    public int parsedFiles;

    public JavaSourceIndex(Path root) {
        this.root = root;
    }

    public String rel(Path p) {
        try {
            return root.relativize(p).toString().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return p.toString().replace(File.separatorChar, '/');
        }
    }

    public static class JavaClassInfo {
        public String kind, name, fqn, pkg, superclass, comment, sourcePath;
        public int sourceLine;
        public boolean deprecated;
        public Map<String, String> imports = new LinkedHashMap<>();
        public List<String> typeParams = new ArrayList<>();
        public List<String> interfaces = new ArrayList<>();
        public List<JavaFieldInfo> fields = new ArrayList<>();
        public List<JavaMethodInfo> methods = new ArrayList<>();
        public List<DocumentModel.EnumValue> enumValues = new ArrayList<>();
    }

    public static class JavaFieldInfo {
        public String name, jsonName, type, comment, required, sourcePath;
        public List<String> constraints = new ArrayList<>();
        public int sourceLine;

        public JavaFieldInfo copy() {
            JavaFieldInfo f = new JavaFieldInfo();
            f.name = name;
            f.jsonName = jsonName;
            f.type = type;
            f.comment = comment;
            f.required = required;
            f.sourcePath = sourcePath;
            f.sourceLine = sourceLine;
            f.constraints = new ArrayList<>(constraints);
            return f;
        }
    }

    public static class JavaMethodInfo {
        public String name, returnType, comment, returnComment, sourcePath;
        public int sourceLine;
        public boolean deprecated;
        public Map<String, String> paramComments = new LinkedHashMap<>();
        public List<JavaParamInfo> params = new ArrayList<>();
        public List<String> throwsTypes = new ArrayList<>();

        public JavaMethodInfo copy() {
            JavaMethodInfo m = new JavaMethodInfo();
            m.name = name;
            m.returnType = returnType;
            m.comment = comment;
            m.returnComment = returnComment;
            m.sourcePath = sourcePath;
            m.sourceLine = sourceLine;
            m.deprecated = deprecated;
            m.paramComments = new LinkedHashMap<>(paramComments);
            for (JavaParamInfo p : params) m.params.add(p.copy());
            m.throwsTypes = new ArrayList<>(throwsTypes);
            return m;
        }
    }

    public static class JavaParamInfo {
        public String name, type;

        public JavaParamInfo copy() {
            JavaParamInfo p = new JavaParamInfo();
            p.name = name;
            p.type = type;
            return p;
        }
    }
}
