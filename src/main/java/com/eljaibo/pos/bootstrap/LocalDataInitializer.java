package com.eljaibo.pos.bootstrap;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class LocalDataInitializer implements ApplicationRunner {

    private static final String MAIN_BRANCH_CODE = "MAIN";
    private static final String ADMIN_USERNAME = "admin";

    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public LocalDataInitializer(
            BusinessRepository businessRepository,
            BranchRepository branchRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-password}") String adminPassword
    ) {
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Business business = businessRepository.findFirstByActiveTrue()
                .orElseGet(this::createBusiness);

        branchRepository
                .findByBusinessIdAndCodeIgnoreCase(
                        business.getId(),
                        MAIN_BRANCH_CODE
                )
                .orElseGet(() -> createMainBranch(business));

        if (!appUserRepository.existsByUsernameIgnoreCase(ADMIN_USERNAME)) {
            createAdminUser(business);
        }
    }

    private Business createBusiness() {
        return businessRepository.save(
                new Business(
                        "El Jaibo",
                        null,
                        "MXN",
                        "America/Mexico_City"
                )
        );
    }

    private Branch createMainBranch(Business business) {
        return branchRepository.save(
                new Branch(
                        business,
                        MAIN_BRANCH_CODE,
                        "Sucursal principal"
                )
        );
    }

    private AppUser createAdminUser(Business business) {
        return appUserRepository.save(
                new AppUser(
                        business,
                        ADMIN_USERNAME,
                        passwordEncoder.encode(adminPassword),
                        "Administrador",
                        UserRole.ADMIN
                )
        );
    }
}