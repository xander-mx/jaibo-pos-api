package com.eljaibo.pos.terminal.application;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.terminal.api.CurrentTerminalResponse;
import com.eljaibo.pos.terminal.api.RegisterTerminalRequest;
import com.eljaibo.pos.terminal.api.TerminalResponse;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;

    public TerminalService(
            TerminalRepository terminalRepository,
            BranchRepository branchRepository,
            AppUserRepository appUserRepository
    ) {
        this.terminalRepository = terminalRepository;
        this.branchRepository = branchRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public TerminalResponse register(
            UUID currentUserId,
            UUID businessId,
            RegisterTerminalRequest request
    ) {
        AppUser currentUser = findCurrentUser(
                currentUserId,
                businessId
        );

        Branch branch = resolveBranch(
                businessId,
                request.branchId()
        );

        Terminal terminal = terminalRepository.save(
                new Terminal(
                        branch,
                        currentUser,
                        request.name()
                )
        );

        return toResponse(terminal);
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> findActive(UUID businessId) {
        return terminalRepository
                .findAllByBranchBusinessIdAndActiveTrueOrderByNameAsc(
                        businessId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentTerminalResponse current(
            UUID businessId,
            UUID terminalId
    ) {
        Terminal terminal =
                terminalRepository
                        .findByIdAndBranchBusinessIdAndActiveTrue(
                                terminalId,
                                businessId
                        )
                        .filter(value ->
                                value.getBranch().isActive()
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Active terminal not found"
                                )
                        );

        return new CurrentTerminalResponse(
                terminal.getId(),
                terminal.getName(),
                terminal.getBranch().getId(),
                terminal.getBranch().getName()
        );
    }

    private AppUser findCurrentUser(
            UUID currentUserId,
            UUID businessId
    ) {
        AppUser user = appUserRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists"
                ));

        boolean belongsToBusiness = user.getBusiness()
                .getId()
                .equals(businessId);

        if (!user.isActive() || !belongsToBusiness) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is not active"
            );
        }

        return user;
    }

    private Branch resolveBranch(
            UUID businessId,
            UUID requestedBranchId
    ) {
        if (requestedBranchId != null) {
            Branch branch = branchRepository
                    .findById(requestedBranchId)
                    .orElseThrow(this::branchNotFound);

            boolean validBranch = branch.isActive()
                    && branch.getBusiness()
                    .getId()
                    .equals(businessId);

            if (!validBranch) {
                throw branchNotFound();
            }

            return branch;
        }

        List<Branch> branches = branchRepository
                .findAllByBusinessIdAndActiveTrueOrderByName(
                        businessId
                );

        if (branches.isEmpty()) {
            throw branchNotFound();
        }

        if (branches.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "branchId is required when multiple branches exist"
            );
        }

        return branches.getFirst();
    }

    private ResponseStatusException branchNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active branch not found"
        );
    }

    private TerminalResponse toResponse(Terminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getBranch().getId(),
                terminal.getName(),
                terminal.isActive(),
                terminal.getRegisteredBy().getId(),
                terminal.getRegisteredBy().getDisplayName(),
                terminal.getLastSeenAt(),
                terminal.getCreatedAt()
        );
    }
}