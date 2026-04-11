package com.reslife.api.security;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serializable Spring Security principal stored in the HTTP session.
 * Holds only the data needed for security decisions — no JPA proxies.
 */
public final class ReslifeUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
    private final String passwordHash;
    private final AccountStatus accountStatus;
    private final Set<SimpleGrantedAuthority> authorities;

    private ReslifeUserDetails(UUID userId, String username, String passwordHash,
                                AccountStatus accountStatus,
                                Set<SimpleGrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus;
        this.authorities = authorities;
    }

    public static ReslifeUserDetails from(User user) {
        Set<SimpleGrantedAuthority> authorities = user.getUserRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName().name()))
                .collect(Collectors.toSet());

        return new ReslifeUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getAccountStatus(),
                authorities
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    // -----------------------------------------------------------------------
    // UserDetails contract
    // -----------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** An account that is not ACTIVE is considered "disabled" for Spring Security purposes. */
    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /** BLACKLISTED and FROZEN accounts are considered locked. */
    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.BLACKLISTED && accountStatus != AccountStatus.FROZEN;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
