package com.hex1n.sofafacadedoc.doc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {
    private final ObjectMapper mapper;

    public MarkdownRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String render(DocumentModel.Document doc, DocumentModel.ServiceDoc service, DocumentModel.MethodDoc method) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(service.fqn).append(".").append(method.name).append("\n\n");
        b.append("- 项目：`").append(doc.project).append("`\n");
        b.append("- 分支：`").append(doc.branch).append("`\n");
        b.append("- Commit：`").append(doc.commit).append("`\n");
        b.append("- 状态：`").append(service.status).append("`\n");
        String methodChange = changeLabel(method.changeKind);
        if (notBlank(methodChange)) b.append("- 变更：`").append(methodChange).append("`\n");
        if (service.deprecated || method.deprecated) b.append("- 废弃：`true`\n");
        b.append("\n");
        if (notBlank(service.comment)) b.append("## 接口说明\n\n").append(service.comment).append("\n\n");
        if (notBlank(method.comment)) b.append("## 方法说明\n\n").append(method.comment).append("\n\n");
        if (!service.publishRecords.isEmpty()) {
            b.append("## 发布记录\n\n| 来源 | Binding | UniqueId | Version | Serialize | Timeout | 实现类 |\n| --- | --- | --- | --- | --- | --- | --- |\n");
            for (DocumentModel.PublishRecord p : service.publishRecords) {
                b.append("| ").append(text(p.source))
                        .append(" | ").append(text(p.binding))
                        .append(" | ").append(text(p.uniqueId))
                        .append(" | ").append(text(p.version))
                        .append(" | ").append(text(p.serializeType))
                        .append(" | ").append(text(p.timeout))
                        .append(" | ").append(text(p.implementation))
                        .append(" |\n");
            }
            b.append("\n");
        }
        b.append("## 入参\n\n");
        if (method.params.isEmpty()) {
            b.append("无\n\n");
        }
        for (DocumentModel.ParamDoc p : method.params) {
            b.append("### ").append(p.name).append("\n\n");
            b.append("- Java 类型：`").append(p.javaType).append("`\n");
            b.append("- JSON 类型：`").append(p.jsonType).append("`\n");
            if (notBlank(p.comment)) b.append("- 说明：").append(p.comment).append("\n");
            b.append("\n");
            fieldTable(b, p.tree);
        }
        b.append("## 出参\n\n");
        if ("void".equals(method.returnType) || method.returnType == null) {
            b.append("`void`\n\n");
        } else {
            b.append("- Java 类型：`").append(method.returnType).append("`\n");
            if (notBlank(method.returnComment)) b.append("- 说明：").append(method.returnComment).append("\n");
            b.append("\n");
            fieldTable(b, method.returnTree);
        }
        if (!method.throwsTypes.isEmpty()) {
            b.append("## Throws\n\n");
            for (String t : method.throwsTypes) b.append("- `").append(t).append("`\n");
            b.append("\n");
        }
        b.append("## 请求 JSON 骨架\n\n");
        jsonBlock(b, method.requestExample);
        if (!"void".equals(method.returnType) && method.returnType != null) {
            b.append("## 返回结构示例\n\n> 结构示例，非真实响应。\n\n");
            jsonBlock(b, method.returnExample);
        }
        return b.toString();
    }

    private void fieldTable(StringBuilder b, DocumentModel.FieldNode root) {
        if (root == null) {
            b.append("类型未展开。\n\n");
            return;
        }
        boolean withChange = hasChange(root);
        if (withChange) {
            b.append("| 变更 | 字段路径 | Java 类型 | JSON 输入 | 必填 | 说明 |\n| --- | --- | --- | --- | --- | --- |\n");
        } else {
            b.append("| 字段路径 | Java 类型 | JSON 输入 | 必填 | 说明 |\n| --- | --- | --- | --- | --- |\n");
        }
        appendFieldRow(b, root, withChange);
        b.append("\n");
    }

    private void appendFieldRow(StringBuilder b, DocumentModel.FieldNode n, boolean withChange) {
        if (withChange) b.append("| ").append(text(changeLabel(n.changeKind))).append(" ");
        b.append("| `").append(n.path).append("` | `").append(text(n.javaType)).append("` | `").append(text(n.jsonType)).append("` | ")
                .append(text(n.required)).append(" | ").append(text(description(n))).append(" |\n");
        for (DocumentModel.FieldNode child : n.children) appendFieldRow(b, child, withChange);
    }

    private String description(DocumentModel.FieldNode n) {
        StringBuilder out = new StringBuilder(first(n.comment, n.note, "未填写"));
        if (notBlank(n.jsonName) && !n.jsonName.equals(n.name)) {
            appendMeta(out, "Jackson 名称：" + n.jsonName);
        }
        if (n.constraints != null && !n.constraints.isEmpty()) {
            appendMeta(out, "约束：" + String.join(", ", n.constraints));
        }
        return out.toString();
    }

    private void appendMeta(StringBuilder out, String value) {
        if (out.length() > 0) out.append("<br>");
        out.append(value);
    }

    private void jsonBlock(StringBuilder b, Object value) {
        try {
            b.append("```json\n").append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)).append("\n```\n\n");
        } catch (Exception e) {
            b.append("```json\nnull\n```\n\n");
        }
    }

    private String text(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.replace("\n", "<br>");
    }

    private boolean hasChange(DocumentModel.FieldNode node) {
        if (node == null) return false;
        if (notBlank(changeLabel(node.changeKind))) return true;
        for (DocumentModel.FieldNode child : node.children) {
            if (hasChange(child)) return true;
        }
        return false;
    }

    private String changeLabel(String kind) {
        if (!notBlank(kind) || "unchanged".equals(kind)) return "";
        if ("added".equals(kind)) return "新增";
        if ("modified".equals(kind)) return "修改";
        if ("removed".equals(kind)) return "删除";
        return kind;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String first(String a, String b, String c) {
        if (notBlank(a)) return a;
        if (notBlank(b)) return b;
        return c;
    }

}
