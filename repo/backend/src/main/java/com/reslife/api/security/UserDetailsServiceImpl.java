package com.reslife.api.security;

import com.reslife.api.domain.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a {@link ReslifeUserDetails} from the database.
 *
 * <p>Used by Spring Security to re-validate the session principal on each request.
 * The @SQLRestriction("deleted_at IS NULL") on {@link com.reslife.api.domain.user.User}
 * ensures that soft-deleted accounts cannot authenticate — returning empty here causes
 * Spring Security to treat the session as invalid.
 *
 * <p>The error message intentionally does not mention whether the username exists,
 * to prevent username-enumeration via error messages.
 */
@Service
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByEmailIgnoreCase(username))
                .map(ReslifeUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
