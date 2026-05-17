package com.hex1n.sofafacadedoc.invoke;

import com.alipay.hessian.generic.model.GenericCollection;
import com.alipay.hessian.generic.model.GenericMap;
import com.alipay.hessian.generic.model.GenericObject;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class GenericArgumentConverter {
    public Object[] genericArgs(DocumentModel.MethodDoc method, Object argsObject) {
        Object[] args = normalizeArgs(method, argsObject);
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = convertNode(method.params.get(i).tree, args[i]);
        }
        return converted;
    }

    public Object[] normalizeArgs(DocumentModel.MethodDoc method, Object argsObject) {
        if (method.params.size() == 0) return new Object[0];
        if (method.params.size() == 1) return new Object[]{argsObject};
        if (!(argsObject instanceof List)) throw new IllegalArgumentException("多参数方法必须使用 JSON 数组");
        List<?> list = (List<?>) argsObject;
        if (list.size() != method.params.size()) throw new IllegalArgumentException("参数数量不匹配");
        return list.toArray(new Object[0]);
    }

    private Object convertNode(DocumentModel.FieldNode node, Object value) {
        if (value == null || node == null) return value;
        String base = baseType(node.javaType);
        if (isEnum(node)) {
            GenericObject out = new GenericObject(base);
            out.putField("name", String.valueOf(value));
            return out;
        }
        if (isCollectionType(base) && value instanceof Collection) {
            List<Object> values = new ArrayList<>();
            DocumentModel.FieldNode item = node.children.isEmpty() ? null : node.children.get(0);
            for (Object raw : (Collection<?>) value) values.add(convertNode(item, raw));
            if ("java.util.Set".equals(base) || "java.util.HashSet".equals(base) || "java.util.LinkedHashSet".equals(base)) {
                return new LinkedHashSet<>(values);
            }
            if (!isStandardCollectionType(base)) {
                GenericCollection collection = new GenericCollection(base);
                collection.setCollection(values);
                return collection;
            }
            return values;
        }
        if (isMapType(base) && value instanceof Map) {
            Map<Object, Object> values = new LinkedHashMap<>();
            DocumentModel.FieldNode mapValue = node.children.isEmpty() ? null : node.children.get(0);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                values.put(entry.getKey(), convertNode(mapValue, entry.getValue()));
            }
            if (!isStandardMapType(base)) {
                GenericMap map = new GenericMap(base);
                map.setMap(values);
                return map;
            }
            return values;
        }
        if (isObjectType(node, base) && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) value;
            GenericObject out = new GenericObject(base);
            if (node.children.isEmpty()) {
                for (Map.Entry<String, Object> entry : input.entrySet()) out.putField(entry.getKey(), entry.getValue());
            } else {
                for (DocumentModel.FieldNode child : node.children) {
                    if (input.containsKey(child.name)) out.putField(child.name, convertNode(child, input.get(child.name)));
                }
            }
            return out;
        }
        return convertScalar(base, value);
    }

    private Object convertScalar(String base, Object value) {
        if (value == null) return null;
        if ("java.lang.String".equals(base) || "char".equals(base) || "java.lang.Character".equals(base)) {
            String s = String.valueOf(value);
            return ("char".equals(base) || "java.lang.Character".equals(base)) && !s.isEmpty() ? s.charAt(0) : s;
        }
        if ("java.math.BigDecimal".equals(base)) return value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value));
        if ("java.math.BigInteger".equals(base)) return value instanceof BigInteger ? value : new BigInteger(String.valueOf(value));
        if ("int".equals(base) || "java.lang.Integer".equals(base)) return number(value).intValue();
        if ("long".equals(base) || "java.lang.Long".equals(base)) return number(value).longValue();
        if ("short".equals(base) || "java.lang.Short".equals(base)) return number(value).shortValue();
        if ("byte".equals(base) || "java.lang.Byte".equals(base)) return number(value).byteValue();
        if ("float".equals(base) || "java.lang.Float".equals(base)) return number(value).floatValue();
        if ("double".equals(base) || "java.lang.Double".equals(base)) return number(value).doubleValue();
        if ("boolean".equals(base) || "java.lang.Boolean".equals(base)) return value instanceof Boolean ? value : Boolean.valueOf(String.valueOf(value));
        if ("java.util.Date".equals(base)) return utilDate(value);
        if ("java.sql.Date".equals(base)) return value instanceof java.sql.Date ? value : java.sql.Date.valueOf(String.valueOf(value).substring(0, 10));
        if ("java.time.LocalDate".equals(base)) return value instanceof LocalDate ? value : LocalDate.parse(String.valueOf(value).substring(0, 10));
        if ("java.time.LocalDateTime".equals(base)) return value instanceof LocalDateTime ? value : LocalDateTime.parse(String.valueOf(value));
        if ("java.time.LocalTime".equals(base)) return value instanceof LocalTime ? value : LocalTime.parse(String.valueOf(value));
        return value;
    }

    private Number number(Object value) {
        if (value instanceof Number) return (Number) value;
        return new BigDecimal(String.valueOf(value));
    }

    private Date utilDate(Object value) {
        if (value instanceof Date) return (Date) value;
        if (value instanceof Number) return new Date(((Number) value).longValue());
        String s = String.valueOf(value);
        try {
            return Date.from(Instant.parse(s));
        } catch (Exception ignored) {
            return java.sql.Date.valueOf(s.substring(0, 10));
        }
    }

    private boolean isEnum(DocumentModel.FieldNode node) {
        return node != null && ((node.jsonType != null && node.jsonType.startsWith("enum")) || !node.enumValues.isEmpty());
    }

    private boolean isObjectType(DocumentModel.FieldNode node, String base) {
        return node != null && node.jsonType != null && node.jsonType.startsWith("object") && !isMapType(base) && !"java.lang.Object".equals(base);
    }

    private boolean isCollectionType(String base) {
        return "java.util.List".equals(base) || "java.util.Set".equals(base) || "java.util.Collection".equals(base) || "java.util.ArrayList".equals(base) || "java.util.HashSet".equals(base) || "java.util.LinkedHashSet".equals(base) || (!base.startsWith("java.") && base.toLowerCase().contains("list"));
    }

    private boolean isStandardCollectionType(String base) {
        return "java.util.List".equals(base) || "java.util.Collection".equals(base) || "java.util.ArrayList".equals(base) || "java.util.Set".equals(base) || "java.util.HashSet".equals(base) || "java.util.LinkedHashSet".equals(base);
    }

    private boolean isMapType(String base) {
        return "java.util.Map".equals(base) || "java.util.HashMap".equals(base) || "java.util.LinkedHashMap".equals(base) || (!base.startsWith("java.") && base.toLowerCase().contains("map"));
    }

    private boolean isStandardMapType(String base) {
        return "java.util.Map".equals(base) || "java.util.HashMap".equals(base) || "java.util.LinkedHashMap".equals(base);
    }

    private String baseType(String type) {
        if (type == null) return "java.lang.Object";
        int angle = type.indexOf('<');
        return (angle >= 0 ? type.substring(0, angle) : type).trim();
    }
}
