package com.hex1n.sofafacadedoc.invoke;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class InvocationValidator {
    private final GenericArgumentConverter arguments;

    public InvocationValidator(GenericArgumentConverter arguments) {
        this.arguments = arguments;
    }

    public InvokeService.ValidateResult validate(DocumentModel.MethodDoc method, Map<String, Object> body) {
        InvokeService.ValidateResult out = new InvokeService.ValidateResult();
        Object argsObject = body == null ? null : body.get("args");
        try {
            if (method.params.size() == 0) {
                out.ok = argsObject == null;
                if (!out.ok) out.errors.add("args must be omitted for no-arg method");
                return out;
            }
            Object[] args = arguments.normalizeArgs(method, argsObject);
            for (int i = 0; i < method.params.size(); i++) {
                DocumentModel.ParamDoc param = method.params.get(i);
                validateNode(param.tree, args[i], param.name, out.errors, out.warnings);
            }
        } catch (Exception e) {
            out.errors.add(e.getMessage());
        }
        out.ok = out.errors.isEmpty();
        return out;
    }

    private void validateNode(DocumentModel.FieldNode node, Object value, String path, List<String> errors, List<String> warnings) {
        if (node == null) return;
        boolean required = "是".equals(node.required);
        if (value == null) {
            if (required) warnings.add(path + " is required");
            return;
        }
        String jsonType = node.jsonType == null ? "" : node.jsonType;
        if (jsonType.startsWith("object") && !(value instanceof Map)) {
            errors.add(path + " must be object");
            return;
        }
        if (jsonType.startsWith("array") && !(value instanceof Collection)) {
            errors.add(path + " must be array");
            return;
        }
        if (jsonType.startsWith("boolean") && !(value instanceof Boolean)) {
            errors.add(path + " must be boolean");
            return;
        }
        if (jsonType.startsWith("number") && !(value instanceof Number)) {
            errors.add(path + " must be number");
            return;
        }
        if ((jsonType.startsWith("string") || jsonType.startsWith("enum")) && !(value instanceof String) && !(value instanceof Number)) {
            errors.add(path + " must be string");
            return;
        }
        if (jsonType.startsWith("enum") && value instanceof String && !node.enumValues.isEmpty()) {
            boolean found = false;
            for (DocumentModel.EnumValue ev : node.enumValues) {
                if (ev.name.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) errors.add(path + " must be one of " + enumNames(node.enumValues));
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            LinkedHashSet<String> known = new LinkedHashSet<>();
            for (DocumentModel.FieldNode child : node.children) {
                known.add(child.name);
                validateNode(child, map.get(child.name), child.path, errors, warnings);
            }
            for (String key : map.keySet()) {
                if (!known.contains(key)) warnings.add(path + "." + key + " is unknown");
            }
        }
        if (value instanceof Collection && !node.children.isEmpty()) {
            int i = 0;
            for (Object item : (Collection<?>) value) {
                validateNode(node.children.get(0), item, path + "[" + i + "]", errors, warnings);
                i++;
            }
        }
    }

    private List<String> enumNames(List<DocumentModel.EnumValue> values) {
        List<String> names = new ArrayList<>();
        for (DocumentModel.EnumValue value : values) names.add(value.name);
        return names;
    }
}
