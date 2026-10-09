package com.eljaibo.pos;

import com.eljaibo.pos.auth.security.AuthenticatedUser;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class PasswordAuthenticationIntegrationTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder
            passwordEncoder;

    @Test
    void shouldAuthenticateActiveUser() {
        Business business = businessRepository.save(
                new Business(
                        "El Jaibo",
                        null,
                        "MXN",
                        "America/Mexico_City"
                )
        );

        AppUser user = appUserRepository.save(
                new AppUser(
                        business,
                        "admin",
                        passwordEncoder.encode("Admin123!"),
                        "Administrador",
                        UserRole.ADMIN
                )
        );

        var request = UsernamePasswordAuthenticationToken
                .unauthenticated("ADMIN", "Admin123!");

        var authentication =
                authenticationManager.authenticate(request);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal())
                .isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser principal =
                (AuthenticatedUser) authentication.getPrincipal();

        assertThat(principal.getId()).isEqualTo(user.getId());
        assertThat(principal.getBusinessId()).isEqualTo(business.getId());
        assertThat(principal.getRole()).isEqualTo(UserRole.ADMIN);
    }
}