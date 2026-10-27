package com.lumintra.backend.shared.security;

import com.lumintra.backend.iam.application.TokenSessionService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.caching.*;
import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenSessionService sessions;
    private final JwtTokenProvider tokens;
    private final HandlerExceptionResolver errors;
    public JwtAuthenticationFilter(TokenSessionService sessions, JwtTokenProvider tokens,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        this.sessions = sessions; this.tokens = tokens; this.errors = errors;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "OPTIONS".equals(request.getMethod()) || Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                "/api/management/auth/login").contains(path)
                || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator/health") || path.equals("/actuator/info");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null) { chain.doFilter(request, response); return; }
        try {
            if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) throw new AuthFlowException("INVALID_TOKEN", "The Authorization header is invalid.");
            String token = authorization.substring(7);
            SecurityContextHolder.getContext().setAuthentication(sessions.authenticate(token));
        } catch (DataAccessException ex) {
            reject(request, response, new AuthFlowException("AUTH_UNAVAILABLE", "Unable to verify the session. Please try again.", ex)); return;
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext(); reject(request, response, ex); return;
        }
        chain.doFilter(request, response);
    }
    public void reject(HttpServletRequest request, HttpServletResponse response, Exception exception) {
        SecurityContextHolder.clearContext();
        errors.resolveException(request, response, null, exception);
    }
}
