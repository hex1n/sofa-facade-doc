package com.hex1n.sofafacadedoc.diff;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiffService {
    public List<DocumentModel.DiffChange> compare(DocumentModel.Document left, DocumentModel.Document right) {
        Map<String, DocumentModel.MethodDoc> l = methods(left);
        Map<String, DocumentModel.MethodDoc> r = methods(right);
        List<DocumentModel.DiffChange> out = new ArrayList<>();
        for (String key : l.keySet()) {
            if (!r.containsKey(key)) {
                out.add(new DocumentModel.DiffChange("Breaking", key, "方法删除"));
                continue;
            }
            DocumentModel.MethodDoc lm = l.get(key);
            DocumentModel.MethodDoc rm = r.get(key);
            if (!sameText(lm.returnType, rm.returnType)) {
                out.add(new DocumentModel.DiffChange("Breaking", key, "返回类型变化: " + lm.returnType + " -> " + rm.returnType));
            }
            if (!sameText(lm.returnComment, rm.returnComment)) out.add(new DocumentModel.DiffChange("Info", key, "返回说明变化"));
            compareFields(key + ".return", lm.returnTree, rm.returnTree, out);
            for (int i = 0; i < lm.params.size(); i++) {
                if (i >= rm.params.size()) {
                    out.add(new DocumentModel.DiffChange("Breaking", key, "参数删除"));
                } else {
                    DocumentModel.ParamDoc lp = lm.params.get(i);
                    DocumentModel.ParamDoc rp = rm.params.get(i);
                    String paramPath = key + "." + lp.name;
                    if (!sameText(lp.name, rp.name)) out.add(new DocumentModel.DiffChange("Breaking", paramPath, "参数名称变化: " + lp.name + " -> " + rp.name));
                    if (!sameText(lp.javaType, rp.javaType)) {
                        out.add(new DocumentModel.DiffChange("Breaking", paramPath, "参数类型变化"));
                    } else {
                        if (!sameText(lp.comment, rp.comment)) out.add(new DocumentModel.DiffChange("Info", paramPath, "参数说明变化"));
                        if (!sameText(lp.required, rp.required)) out.add(requiredChange(paramPath, "参数", lp.required, rp.required));
                        compareFields(paramPath, lp.tree, rp.tree, out);
                    }
                }
            }
            if (rm.params.size() > lm.params.size()) out.add(new DocumentModel.DiffChange("Breaking", key, "参数新增"));
            if (!sameText(lm.comment, rm.comment)) out.add(new DocumentModel.DiffChange("Info", key, "方法说明变化"));
        }
        for (String key : r.keySet()) if (!l.containsKey(key)) out.add(new DocumentModel.DiffChange("Non-breaking", key, "方法新增"));
        return out;
    }

    private Map<String, DocumentModel.MethodDoc> methods(DocumentModel.Document doc) {
        Map<String, DocumentModel.MethodDoc> out = new LinkedHashMap<>();
        if (doc == null) return out;
        for (DocumentModel.ServiceDoc s : doc.services) for (DocumentModel.MethodDoc m : s.methods) out.put(s.fqn + "#" + m.id, m);
        return out;
    }

    private void compareFields(String prefix, DocumentModel.FieldNode left, DocumentModel.FieldNode right, List<DocumentModel.DiffChange> out) {
        Map<String, DocumentModel.FieldNode> l = fields(left);
        Map<String, DocumentModel.FieldNode> r = fields(right);
        for (String path : l.keySet()) {
            if (!r.containsKey(path)) out.add(new DocumentModel.DiffChange("Breaking", prefix + "." + path, "字段删除"));
            else {
                DocumentModel.FieldNode lf = l.get(path), rf = r.get(path);
                String fullPath = prefix + "." + path;
                if (!sameText(lf.javaType, rf.javaType)) out.add(new DocumentModel.DiffChange("Breaking", fullPath, "字段 Java 类型变化"));
                if (!sameText(lf.jsonType, rf.jsonType)) out.add(new DocumentModel.DiffChange("Breaking", fullPath, "字段 JSON 类型变化"));
                if (!sameText(lf.jsonName, rf.jsonName)) out.add(new DocumentModel.DiffChange("Breaking", fullPath, "字段 JSON 名称变化"));
                if (!sameText(lf.required, rf.required)) out.add(requiredChange(fullPath, "字段", lf.required, rf.required));
                if (!safeList(lf.constraints).equals(safeList(rf.constraints))) out.add(new DocumentModel.DiffChange("Breaking", fullPath, "字段约束变化"));
                if (!sameText(lf.comment, rf.comment)) out.add(new DocumentModel.DiffChange("Non-breaking", fullPath, "字段注释变化"));
            }
        }
        for (String path : r.keySet()) if (!l.containsKey(path)) out.add(new DocumentModel.DiffChange("Non-breaking", prefix + "." + path, "字段新增"));
    }

    private Map<String, DocumentModel.FieldNode> fields(DocumentModel.FieldNode root) {
        Map<String, DocumentModel.FieldNode> out = new LinkedHashMap<>();
        walk(root, out);
        return out;
    }

    private void walk(DocumentModel.FieldNode node, Map<String, DocumentModel.FieldNode> out) {
        if (node == null) return;
        out.put(node.path, node);
        for (DocumentModel.FieldNode child : node.children) walk(child, out);
    }

    private DocumentModel.DiffChange requiredChange(String path, String target, String left, String right) {
        if (!"是".equals(normalize(left)) && "是".equals(normalize(right))) {
            return new DocumentModel.DiffChange("Breaking", path, target + "必填约束新增");
        }
        if ("是".equals(normalize(left)) && !"是".equals(normalize(right))) {
            return new DocumentModel.DiffChange("Non-breaking", path, target + "必填约束取消");
        }
        return new DocumentModel.DiffChange("Info", path, target + "必填标记变化");
    }

    private boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> safeList(List<String> value) {
        return value == null ? new ArrayList<>() : value;
    }
}
