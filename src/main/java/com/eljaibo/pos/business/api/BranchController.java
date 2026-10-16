package com.eljaibo.pos.business.api;

import com.eljaibo.pos.business.application.BranchService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchResponse> findActive(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID businessId = UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString("business_id")
                )
        );

        return branchService.findActive(businessId);
    }
}