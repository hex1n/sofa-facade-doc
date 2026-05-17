package com.hex1n.sofafacadedoc.scanner;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class PayloadFieldRules {
    private final JavaAnnotationReader annotations;

    public PayloadFieldRules(JavaAnnotationReader annotations) {
        this.annotations = annotations;
    }

    public String required(String comment) {
        String s = comment == null ? "" : comment.toLowerCase();
        if (s.contains("非必填") || s.contains("选填") || s.contains("可为空") || s.contains("optional")) return "否";
        if (s.contains("必填") || s.contains("必传") || s.contains("不能为空") || s.contains("required")) return "是";
        return "未知";
    }

    public String required(FieldDeclaration fd, String comment) {
        if (annotations.find(fd, "NotNull").isPresent() || annotations.find(fd, "NotBlank").isPresent() || annotations.find(fd, "NotEmpty").isPresent()) return "是";
        Optional<AnnotationExpr> json = annotations.find(fd, "JsonProperty");
        if (json.isPresent()) {
            String required = annotations.attr(json.get(), "required");
            if ("true".equals(required)) return "是";
            if ("false".equals(required)) return "否";
        }
        return required(comment);
    }

    public boolean shouldSkip(FieldDeclaration fd, VariableDeclarator v) {
        if (fd.isStatic() || fd.isTransient()) return true;
        if ("serialVersionUID".equals(v.getNameAsString())) return true;
        Optional<AnnotationExpr> ignored = annotations.find(fd, "JsonIgnore");
        if (!ignored.isPresent()) return false;
        String value = annotations.value(ignored.get());
        return value == null || value.isEmpty() || !"false".equals(value);
    }

    public String jsonPropertyName(FieldDeclaration fd) {
        Optional<AnnotationExpr> json = annotations.find(fd, "JsonProperty");
        if (!json.isPresent()) return "";
        String value = annotations.value(json.get());
        if (value == null || value.trim().isEmpty()) return "";
        return value.trim();
    }

    public List<String> constraints(FieldDeclaration fd) {
        List<String> out = new ArrayList<>();
        addConstraint(out, fd, "NotNull");
        addConstraint(out, fd, "NotBlank");
        addConstraint(out, fd, "NotEmpty");
        addConstraint(out, fd, "Size", "min", "max");
        addConstraint(out, fd, "Min", "value");
        addConstraint(out, fd, "Max", "value");
        addConstraint(out, fd, "DecimalMin", "value");
        addConstraint(out, fd, "DecimalMax", "value");
        addConstraint(out, fd, "Pattern", "regexp");
        Optional<AnnotationExpr> json = annotations.find(fd, "JsonProperty");
        if (json.isPresent()) {
            String required = annotations.attr(json.get(), "required");
            if ("true".equals(required)) out.add("JsonProperty(required=true)");
            String value = annotations.value(json.get());
            if (value != null && !value.trim().isEmpty()) out.add("JsonProperty(value=" + value.trim() + ")");
        }
        return out;
    }

    private void addConstraint(List<String> out, FieldDeclaration fd, String name, String... attrs) {
        Optional<AnnotationExpr> ann = annotations.find(fd, name);
        if (!ann.isPresent()) return;
        if (attrs.length == 0) {
            out.add(name);
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String attrName : attrs) {
            String value = annotations.attr(ann.get(), attrName);
            if (value != null && !value.trim().isEmpty()) parts.add(attrName + "=" + value.trim());
        }
        out.add(parts.isEmpty() ? name : name + "(" + String.join(",", parts) + ")");
    }
}
