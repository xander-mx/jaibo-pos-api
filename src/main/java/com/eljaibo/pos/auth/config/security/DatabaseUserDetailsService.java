package com.eljaibo.pos.auth.security;

import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public DatabaseUserDetailsService(
            AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials")
                );

        return new AuthenticatedUser(
                user.getId(),
                user.getBusiness().getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                user.getRole(),
                user.isActive()
        );
    }
}