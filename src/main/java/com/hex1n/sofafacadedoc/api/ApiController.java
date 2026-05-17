package com.hex1n.sofafacadedoc.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("time", Instant.now().toString());
        return out;
    }

    @ResponseStatus(code = org.springframework.http.HttpStatus.FORBIDDEN)
    public static class ForbiddenException extends RuntimeException {
    }
}
