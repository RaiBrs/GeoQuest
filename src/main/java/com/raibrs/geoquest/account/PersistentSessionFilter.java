package com.raibrs.geoquest.account;

import com.raibrs.geoquest.http.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Adapts the existing database session to Spring's request-scoped SecurityContext. */
public final class PersistentSessionFilter extends OncePerRequestFilter {
    private final AccountSessions accounts;
    public PersistentSessionFilter(AccountSessions accounts) { this.accounts = accounts; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !accounts.enabled() || !path.startsWith("/api/") || path.equals("/api/csrf") || path.equals("/api/leaderboard");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var user = accounts.find(request);
            if (user != null) {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))));
                SecurityContextHolder.setContext(context);
            }
        } catch (IOException error) {
            ApiErrors.write(response, 503, "Unable to complete the operation. Please try again."); return;
        }
        chain.doFilter(request, response);
    }
}
