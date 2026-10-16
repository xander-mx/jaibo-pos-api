package com.eljaibo.pos.business.application;

import com.eljaibo.pos.business.api.BranchResponse;
import com.eljaibo.pos.business.persistence.BranchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> findActive(UUID businessId) {
        return branchRepository
                .findAllByBusinessIdAndActiveTrueOrderByName(businessId)
                .stream()
                .map(branch -> new BranchResponse(
                        branch.getId(),
                        branch.getCode(),
                        branch.getName()
                ))
                .toList();
    }
}