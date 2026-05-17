package com.hex1n.sofafacadedoc.api;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import com.hex1n.sofafacadedoc.config.AuthScope;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AuthFilter extends org.springframework.web.filter.OncePerRequestFilter {
    public static final String ATTR = "authScope";
    private final AppConfigLoader loader;

    public AuthFilter(AppConfigLoader loader) {
        this.loader = loader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("/api/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthScope scope = loader.authenticate(request.getHeader("Authorization"));
        if (!scope.admin && scope.projects.isEmpty() && scope.teams.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"missing or invalid token\"}");
            return;
        }
        request.setAttribute(ATTR, scope);
        filterChain.doFilter(request, response);
    }
}
