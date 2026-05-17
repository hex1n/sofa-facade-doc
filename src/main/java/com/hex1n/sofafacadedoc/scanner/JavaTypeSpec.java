package com.hex1n.sofafacadedoc.scanner;

import java.util.ArrayList;
import java.util.List;

public class JavaTypeSpec {
    public String base;
    public List<JavaTypeSpec> args = new ArrayList<>();

    public static JavaTypeSpec parse(String raw) {
        raw = raw == null ? "" : raw.trim();
        JavaTypeSpec s = new JavaTypeSpec();
        int i = raw.indexOf('<');
        if (i > 0 && raw.endsWith(">")) {
            s.base = raw.substring(0, i).trim();
            String inner = raw.substring(i + 1, raw.length() - 1);
            int depth = 0, start = 0;
            for (int x = 0; x < inner.length(); x++) {
                char c = inner.charAt(x);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                else if (c == ',' && depth == 0) {
                    s.args.add(parse(inner.substring(start, x)));
                    start = x + 1;
                }
            }
            s.args.add(parse(inner.substring(start)));
        } else {
            s.base = raw;
        }
        return s;
    }

    public String toString() {
        if (args.isEmpty()) return base;
        List<String> parts = new ArrayList<>();
        for (JavaTypeSpec a : args) parts.add(a.toString());
        return base + "<" + String.join(", ", parts) + ">";
    }
}
