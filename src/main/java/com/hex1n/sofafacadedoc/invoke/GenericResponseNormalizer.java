package com.hex1n.sofafacadedoc.invoke;

import com.alipay.hessian.generic.model.GenericArray;
import com.alipay.hessian.generic.model.GenericCollection;
import com.alipay.hessian.generic.model.GenericMap;
import com.alipay.hessian.generic.model.GenericObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenericResponseNormalizer {
    public Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof GenericObject) {
            GenericObject generic = (GenericObject) value;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", generic.getType());
            for (String name : generic.getFieldNames()) {
                out.put(name, normalize(generic.getField(name)));
            }
            return out;
        }
        if (value instanceof GenericMap) {
            GenericMap generic = (GenericMap) value;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", generic.getType());
            Map<Object, Object> entries = new LinkedHashMap<>();
            if (generic.getMap() != null) {
                for (Object entryObject : generic.getMap().entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                    entries.put(entry.getKey(), normalize(entry.getValue()));
                }
            }
            out.put("entries", entries);
            return out;
        }
        if (value instanceof GenericCollection) {
            GenericCollection generic = (GenericCollection) value;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", generic.getType());
            List<Object> items = new ArrayList<>();
            if (generic.getCollection() != null) {
                for (Object item : generic.getCollection()) items.add(normalize(item));
            }
            out.put("items", items);
            return out;
        }
        if (value instanceof GenericArray) {
            GenericArray generic = (GenericArray) value;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("_type", generic.getType());
            out.put("componentType", generic.getComponentType());
            List<Object> items = new ArrayList<>();
            if (generic.getObjects() != null) {
                for (Object item : generic.getObjects()) items.add(normalize(item));
            }
            out.put("items", items);
            return out;
        }
        if (value instanceof Map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                out.put(entry.getKey(), normalize(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Collection) {
            List<Object> out = new ArrayList<>();
            for (Object item : (Collection<?>) value) out.add(normalize(item));
            return out;
        }
        if (value.getClass().isArray()) {
            List<Object> out = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) out.add(normalize(Array.get(value, i)));
            return out;
        }
        if (value instanceof Date) return ((Date) value).toInstant().toString();
        if (value instanceof LocalDate) return value.toString();
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).toInstant(ZoneOffset.UTC).toString();
        if (value instanceof LocalTime) return value.toString();
        return value;
    }
}
