package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class CorePersistenceIntegrationTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndRetrieveCoreEntities() {
        Business business = businessRepository.save(
                new Business(
                        "El Jaibo",
                        null,
                        "MXN",
                        "America/Mexico_City"
                )
        );

        Branch branch = branchRepository.save(
                new Branch(
                        business,
                        "MAIN",
                        "Sucursal principal"
                )
        );

        AppUser admin = appUserRepository.save(
                new AppUser(
                        business,
                        "admin",
                        "encoded-password-for-test",
                        "Administrador",
                        UserRole.ADMIN
                )
        );

        entityManager.flush();
        entityManager.clear();

        assertThat(businessRepository.findById(business.getId()))
                .isPresent()
                .get()
                .extracting(Business::getName)
                .isEqualTo("El Jaibo");

        assertThat(
                branchRepository.findByBusinessIdAndCodeIgnoreCase(
                        business.getId(),
                        "main"
                )
        )
                .isPresent()
                .get()
                .extracting(Branch::getName)
                .isEqualTo("Sucursal principal");

        assertThat(appUserRepository.findByUsernameIgnoreCase("ADMIN"))
                .isPresent()
                .get()
                .satisfies(savedUser -> {
                    assertThat(savedUser.getId()).isEqualTo(admin.getId());
                    assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
                    assertThat(savedUser.isActive()).isTrue();
                });

        assertThat(branch.getId()).isNotNull();
    }
}