package com.hex1n.sofafacadedoc.service;

public final class MessageSanitizer {
    private MessageSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.trim().isEmpty()) return "";
        return message
                .replaceAll("(?i)Authorization:\\s*Bearer\\s+\\S+", "Authorization: Bearer ***")
                .replaceAll("(https?://)([^\\s/@:]+)(:[^\\s/@]+)?@", "$1***@");
    }
}
