package com.reslife.api.auth;

import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.security.ReslifeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
    }

    // -----------------------------------------------------------------------
    // POST /api/auth/login
    // -----------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        ReslifeUserDetails userDetails = authService.login(request, httpRequest);

        // Build a fully authenticated token and store it in a new security context.
        // We explicitly save to the session via SecurityContextRepository so that
        // Spring Security's session-persistence machinery picks it up correctly in
        // Spring Security 6 (where just setting the holder is not always sufficient).
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, null, userDetails.getAuthorities());

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        return ResponseEntity.ok(LoginResponse.from(user));
    }

    // -----------------------------------------------------------------------
    // POST /api/auth/logout
    // -----------------------------------------------------------------------

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal ReslifeUserDetails principal,
                                       HttpServletRequest httpRequest) {
        if (principal != null) {
            authService.recordLogout(principal, httpRequest);
        }

        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // GET /api/auth/csrf  (public)
    // Forces the CookieCsrfTokenRepository to write the XSRF-TOKEN cookie.
    // The React SPA calls this once on startup so it has the token available
    // for subsequent POST/PATCH/DELETE requests.
    // -----------------------------------------------------------------------

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        // Accessing csrfToken causes CookieCsrfTokenRepository to write the cookie
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        ));
    }

    // -----------------------------------------------------------------------
    // GET /api/auth/me
    // -----------------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> currentUser(
            @AuthenticationPrincipal ReslifeUserDetails principal) {

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        return ResponseEntity.ok(LoginResponse.from(user));
    }
}
