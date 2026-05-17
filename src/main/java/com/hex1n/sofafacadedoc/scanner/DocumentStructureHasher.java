package com.hex1n.sofafacadedoc.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hex1n.sofafacadedoc.model.DocumentModel;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

@Component
public class DocumentStructureHasher {
    private final ObjectMapper mapper;

    public DocumentStructureHasher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String hash(DocumentModel.Document doc) throws Exception {
        DocumentModel.Document stable = mapper.readValue(mapper.writeValueAsBytes(doc), DocumentModel.Document.class);
        stable.generatedAt = null;
        stable.commit = null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(mapper.writeValueAsBytes(stable));
        StringBuilder b = new StringBuilder();
        for (byte x : digest) b.append(String.format("%02x", x));
        return b.toString();
    }
}
