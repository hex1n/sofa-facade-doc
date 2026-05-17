package com.hex1n.sofafacadedoc.diff;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BranchDiffAnnotator {
    public static final String ADDED = "added";
    public static final String MODIFIED = "modified";
    public static final String REMOVED = "removed";
    public static final String UNCHANGED = "unchanged";

    public List<DocumentModel.ServiceDoc> annotate(DocumentModel.Document current, DocumentModel.Document base) {
        if (current == null) {
            return base == null ? new ArrayList<>() : ghostServices(base);
        }
        if (base == null) {
            return new ArrayList<>(current.services);
        }
        Map<String, DocumentModel.ServiceDoc> baseByFqn = byFqn(base.services);
        Map<String, DocumentModel.ServiceDoc> currentByFqn = byFqn(current.services);
        List<DocumentModel.ServiceDoc> out = new ArrayList<>();
        for (DocumentModel.ServiceDoc svc : current.services) {
            DocumentModel.ServiceDoc baseSvc = baseByFqn.get(svc.fqn);
            if (baseSvc == null) {
                markServiceAdded(svc);
            } else {
                annotateService(svc, baseSvc);
            }
            out.add(svc);
        }
        for (DocumentModel.ServiceDoc baseSvc : base.services) {
            if (currentByFqn.containsKey(baseSvc.fqn)) continue;
            markServiceRemoved(baseSvc);
            out.add(baseSvc);
        }
        sort(out);
        return out;
    }

    private void annotateService(DocumentModel.ServiceDoc current, DocumentModel.ServiceDoc base) {
        Map<String, DocumentModel.MethodDoc> baseByMethodId = methodsById(base);
        Map<String, DocumentModel.MethodDoc> currentByMethodId = methodsById(current);
        boolean anyChange = false;
        for (DocumentModel.MethodDoc m : current.methods) {
            DocumentModel.MethodDoc bm = baseByMethodId.get(m.id);
            if (bm == null) {
                m.changeKind = ADDED;
                for (DocumentModel.ParamDoc p : m.params) markAll(p.tree, ADDED);
                if (m.returnTree != null) markAll(m.returnTree, ADDED);
                anyChange = true;
            } else if (annotateMethod(m, bm)) {
                m.changeKind = MODIFIED;
                anyChange = true;
            } else {
                m.changeKind = UNCHANGED;
            }
        }
        for (DocumentModel.MethodDoc bm : base.methods) {
            if (currentByMethodId.containsKey(bm.id)) continue;
            bm.changeKind = REMOVED;
            for (DocumentModel.ParamDoc p : bm.params) markAll(p.tree, REMOVED);
            if (bm.returnTree != null) markAll(bm.returnTree, REMOVED);
            current.methods.add(bm);
            anyChange = true;
        }
        current.changeKind = anyChange ? MODIFIED : UNCHANGED;
    }

    private boolean annotateMethod(DocumentModel.MethodDoc current, DocumentModel.MethodDoc base) {
        boolean changed = false;
        if (!sameText(current.returnType, base.returnType)) changed = true;
        if (!sameText(current.comment, base.comment)) changed = true;
        if (!sameText(current.returnComment, base.returnComment)) changed = true;
        if (current.params.size() != base.params.size()) changed = true;
        if (annotateFieldTree(current.returnTree, base.returnTree)) changed = true;
        int n = Math.min(current.params.size(), base.params.size());
        for (int i = 0; i < n; i++) {
            DocumentModel.ParamDoc currentParam = current.params.get(i);
            DocumentModel.ParamDoc baseParam = base.params.get(i);
            if (!sameText(currentParam.name, baseParam.name)) changed = true;
            if (!sameText(currentParam.javaType, baseParam.javaType)) changed = true;
            if (!sameText(currentParam.comment, baseParam.comment)) changed = true;
            if (!sameText(currentParam.required, baseParam.required)) changed = true;
            if (annotateFieldTree(current.params.get(i).tree, base.params.get(i).tree)) changed = true;
        }
        return changed;
    }

    private boolean annotateFieldTree(DocumentModel.FieldNode current, DocumentModel.FieldNode base) {
        if (current == null && base == null) return false;
        if (current == null) return true;
        if (base == null) {
            markAll(current, ADDED);
            return true;
        }
        boolean changed = false;
        if (!sameText(current.javaType, base.javaType)) {
            current.changeKind = MODIFIED;
            changed = true;
        }
        if (!sameText(current.jsonType, base.jsonType)
                || !sameText(current.jsonName, base.jsonName)
                || !sameText(current.comment, base.comment)
                || !sameText(current.required, base.required)
                || !safeList(current.constraints).equals(safeList(base.constraints))) {
            current.changeKind = MODIFIED;
            changed = true;
        }
        Map<String, DocumentModel.FieldNode> baseChildren = byName(base.children);
        Set<String> seen = new LinkedHashSet<>();
        for (DocumentModel.FieldNode child : current.children) {
            DocumentModel.FieldNode baseChild = baseChildren.get(child.name);
            if (baseChild == null) {
                markAll(child, ADDED);
                changed = true;
            } else {
                if (annotateFieldTree(child, baseChild)) changed = true;
                seen.add(child.name);
            }
        }
        for (DocumentModel.FieldNode baseChild : base.children) {
            if (seen.contains(baseChild.name)) continue;
            markAll(baseChild, REMOVED);
            current.children.add(baseChild);
            changed = true;
        }
        return changed;
    }

    private void markServiceAdded(DocumentModel.ServiceDoc svc) {
        svc.changeKind = ADDED;
        for (DocumentModel.MethodDoc m : svc.methods) {
            m.changeKind = ADDED;
            for (DocumentModel.ParamDoc p : m.params) markAll(p.tree, ADDED);
            if (m.returnTree != null) markAll(m.returnTree, ADDED);
        }
    }

    private void markServiceRemoved(DocumentModel.ServiceDoc svc) {
        svc.changeKind = REMOVED;
        for (DocumentModel.MethodDoc m : svc.methods) {
            m.changeKind = REMOVED;
            for (DocumentModel.ParamDoc p : m.params) markAll(p.tree, REMOVED);
            if (m.returnTree != null) markAll(m.returnTree, REMOVED);
        }
    }

    private void markAll(DocumentModel.FieldNode node, String kind) {
        if (node == null) return;
        node.changeKind = kind;
        for (DocumentModel.FieldNode c : node.children) markAll(c, kind);
    }

    private Map<String, DocumentModel.ServiceDoc> byFqn(List<DocumentModel.ServiceDoc> services) {
        Map<String, DocumentModel.ServiceDoc> out = new LinkedHashMap<>();
        if (services == null) return out;
        for (DocumentModel.ServiceDoc s : services) out.put(s.fqn, s);
        return out;
    }

    private Map<String, DocumentModel.MethodDoc> methodsById(DocumentModel.ServiceDoc svc) {
        Map<String, DocumentModel.MethodDoc> out = new LinkedHashMap<>();
        if (svc == null || svc.methods == null) return out;
        for (DocumentModel.MethodDoc m : svc.methods) out.put(m.id, m);
        return out;
    }

    private Map<String, DocumentModel.FieldNode> byName(List<DocumentModel.FieldNode> children) {
        Map<String, DocumentModel.FieldNode> out = new LinkedHashMap<>();
        if (children == null) return out;
        for (DocumentModel.FieldNode c : children) out.put(c.name, c);
        return out;
    }

    private List<DocumentModel.ServiceDoc> ghostServices(DocumentModel.Document base) {
        List<DocumentModel.ServiceDoc> out = new ArrayList<>();
        for (DocumentModel.ServiceDoc s : base.services) {
            markServiceRemoved(s);
            out.add(s);
        }
        sort(out);
        return out;
    }

    private void sort(List<DocumentModel.ServiceDoc> services) {
        services.sort(Comparator
                .comparingInt((DocumentModel.ServiceDoc s) -> orderKey(s.changeKind))
                .thenComparing(s -> s.fqn == null ? "" : s.fqn));
        for (DocumentModel.ServiceDoc s : services) sortMethods(s.methods);
    }

    private void sortMethods(List<DocumentModel.MethodDoc> methods) {
        if (methods == null) return;
        methods.sort(Comparator
                .comparingInt((DocumentModel.MethodDoc m) -> orderKey(m.changeKind))
                .thenComparing(m -> m.name == null ? "" : m.name)
                .thenComparing(m -> m.id == null ? "" : m.id));
    }

    private int orderKey(String changeKind) {
        if (changeKind == null) return 3;
        switch (changeKind) {
            case ADDED: return 0;
            case MODIFIED: return 1;
            case REMOVED: return 2;
            default: return 3;
        }
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
