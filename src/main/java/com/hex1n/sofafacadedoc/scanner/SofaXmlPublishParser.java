package com.hex1n.sofafacadedoc.scanner;

import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class SofaXmlPublishParser {
    public List<DocumentModel.PublishRecord> parse(Path projectRoot, List<Path> resourceRoots, List<String> springProfiles) {
        List<DocumentModel.PublishRecord> records = new ArrayList<>();
        Map<String, String> props = loadApplicationProperties(resourceRoots, springProfiles);
        Pattern service = Pattern.compile("<sofa:service\\b([^>]*)>(.*?)</sofa:service>", Pattern.DOTALL);
        Pattern self = Pattern.compile("<sofa:service\\b([^>]*)/>", Pattern.DOTALL);
        for (Path root : resourceRoots) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".xml")).forEach(p -> parseXmlFile(records, projectRoot, p, props, service, self));
            } catch (Exception ignored) {
            }
        }
        return records;
    }

    private void parseXmlFile(List<DocumentModel.PublishRecord> records, Path projectRoot, Path path, Map<String, String> props, Pattern service, Pattern self) {
        try {
            String body = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher m = service.matcher(body);
            while (m.find()) records.add(xmlRecord(projectRoot, path, body, m.start(), m.group(1), m.group(2), props));
            Matcher sm = self.matcher(body);
            while (sm.find()) records.add(xmlRecord(projectRoot, path, body, sm.start(), sm.group(1), "", props));
        } catch (Exception ignored) {
        }
    }

    private DocumentModel.PublishRecord xmlRecord(Path projectRoot, Path path, String body, int offset, String attrs, String block, Map<String, String> props) {
        DocumentModel.PublishRecord pr = new DocumentModel.PublishRecord();
        pr.source = "xml";
        pr.interfaceName = resolvePlaceholder(xmlAttr(attrs, "interface"), props);
        pr.uniqueId = resolvePlaceholder(xmlAttr(attrs, "unique-id"), props);
        pr.version = resolvePlaceholder(xmlAttr(attrs, "version"), props);
        String globalAttrs = tagAttrs(block, "sofa:global-attrs");
        String boltAttrs = tagAttrs(block, "sofa:binding.bolt");
        pr.binding = block.contains("binding.bolt") ? "bolt" : "";
        pr.serializeType = resolvePlaceholder(firstNonBlank(xmlAttr(boltAttrs, "serialize-type"), xmlAttr(globalAttrs, "serialize-type")), props);
        pr.timeout = resolvePlaceholder(firstNonBlank(xmlAttr(boltAttrs, "timeout"), xmlAttr(globalAttrs, "timeout")), props);
        pr.sourcePath = rel(projectRoot, path);
        pr.sourceLine = body.substring(0, offset).split("\n", -1).length;
        if (pr.interfaceName == null || pr.interfaceName.isEmpty()) {
            pr.incomplete = true;
            pr.incompleteReason = "missing interface attribute";
        } else if (containsUnresolvedPlaceholder(pr.interfaceName) || containsUnresolvedPlaceholder(pr.uniqueId) || containsUnresolvedPlaceholder(pr.version) || containsUnresolvedPlaceholder(pr.serializeType) || containsUnresolvedPlaceholder(pr.timeout)) {
            pr.incomplete = true;
            pr.incompleteReason = "unresolved placeholder";
        }
        return pr;
    }

    private String rel(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return path.toString().replace(File.separatorChar, '/');
        }
    }

    private String xmlAttr(String attrs, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(attrs);
        if (!m.find()) return "";
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private String tagAttrs(String body, String tag) {
        Matcher m = Pattern.compile("<" + Pattern.quote(tag) + "\\b([^>]*)/?\\s*>", Pattern.DOTALL).matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private Map<String, String> loadApplicationProperties(List<Path> resourceRoots, List<String> springProfiles) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path root : resourceRoots) {
            loadApplicationProperties(root.resolve("application.properties"), out);
            loadApplicationYaml(root.resolve("application.yml"), out);
            loadApplicationYaml(root.resolve("application.yaml"), out);
            for (String profile : springProfiles == null ? Collections.<String>emptyList() : springProfiles) {
                if (profile == null || profile.trim().isEmpty()) continue;
                String p = profile.trim();
                loadApplicationProperties(root.resolve("application-" + p + ".properties"), out);
                loadApplicationYaml(root.resolve("application-" + p + ".yml"), out);
                loadApplicationYaml(root.resolve("application-" + p + ".yaml"), out);
            }
        }
        return out;
    }

    private void loadApplicationProperties(Path path, Map<String, String> out) {
        if (!Files.exists(path)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            for (String name : props.stringPropertyNames()) out.put(name, props.getProperty(name));
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void loadApplicationYaml(Path path, Map<String, String> out) {
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map) flattenYaml("", (Map<Object, Object>) loaded, out);
        } catch (Exception ignored) {
        }
    }

    private void flattenYaml(String prefix, Map<Object, Object> map, Map<String, String> out) {
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> nested = (Map<Object, Object>) value;
                flattenYaml(key, nested, out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }

    private String resolvePlaceholder(String value, Map<String, String> props) {
        if (value == null || value.isEmpty()) return value;
        Matcher m = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}").matcher(value);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String replacement = props.get(m.group(1));
            if (replacement == null) replacement = m.group(2);
            if (replacement == null) replacement = m.group(0);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private boolean containsUnresolvedPlaceholder(String value) {
        return value != null && value.contains("${");
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.trim().isEmpty() ? a : b;
    }
}
