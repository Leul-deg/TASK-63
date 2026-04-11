package com.reslife.api.security;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-request filter that re-checks the authenticated user's account status
 * against the database. This provides sub-request granularity for admin actions
 * like DISABLE or BLACKLIST — the affected user's active session is invalidated
 * on their very next request, not just at the next login.
 *
 * <p>The DB lookup is a PK lookup (~1ms) and is acceptable overhead for a small
 * internal portal. For high-throughput APIs a short-TTL cache would be appropriate.
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public AccountStatusFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof ReslifeUserDetails principal) {

            boolean blocked = userRepository.findById(principal.getUserId())
                    .map(user -> user.getAccountStatus() != AccountStatus.ACTIVE)
                    .orElse(true); // user row gone (hard-deleted) → treat as blocked

            if (blocked) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Session is no longer valid\"}");
                return; // do NOT continue the filter chain
            }
        }

        chain.doFilter(request, response);
    }
}
