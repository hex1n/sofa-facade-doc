package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.config.AuthScope;
import com.hex1n.sofafacadedoc.config.ConfigurationAuthorization;
import com.hex1n.sofafacadedoc.service.DocumentQueryService;
import com.hex1n.sofafacadedoc.service.MessageSanitizer;
import com.hex1n.sofafacadedoc.service.ProjectAccessService;
import com.hex1n.sofafacadedoc.service.ProjectCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(ApiController.ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(Exception e, HttpServletRequest request) {
        return error("forbidden", "forbidden");
    }

    @ExceptionHandler(ConfigurationAuthorization.Forbidden.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> configurationForbidden(Exception e, HttpServletRequest request) {
        return error("forbidden", "forbidden");
    }

    @ExceptionHandler(ProjectAccessService.Forbidden.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> projectAccessForbidden(Exception e, HttpServletRequest request) {
        return error("forbidden", "forbidden");
    }

    @ExceptionHandler(DocumentQueryService.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> documentNotFound(Exception e, HttpServletRequest request) {
        return error("not_found", detailed(request) ? sanitize(e.getMessage()) : "resource not found");
    }

    @ExceptionHandler(ProjectCatalogService.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> projectCatalogNotFound(Exception e, HttpServletRequest request) {
        return error("not_found", detailed(request) ? sanitize(e.getMessage()) : "resource not found");
    }

    @ExceptionHandler(ProjectAccessService.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> projectAccessNotFound(Exception e, HttpServletRequest request) {
        return error("not_found", detailed(request) ? sanitize(e.getMessage()) : "resource not found");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception e, HttpServletRequest request) {
        return error("bad_request", detailed(request) ? sanitize(e.getMessage()) : "bad request");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> serverError(Exception e, HttpServletRequest request) {
        return error("internal_error", detailed(request) ? sanitize(e.getMessage()) : "operation failed; ask an admin to check details");
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", code);
        out.put("message", message);
        return out;
    }

    private boolean detailed(HttpServletRequest request) {
        Object scope = request.getAttribute(AuthFilter.ATTR);
        return scope instanceof AuthScope && ((AuthScope) scope).admin;
    }

    static String sanitize(String message) {
        return MessageSanitizer.sanitize(message);
    }
}
