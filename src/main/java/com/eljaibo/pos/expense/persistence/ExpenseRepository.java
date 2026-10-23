package com.eljaibo.pos.expense.persistence;

import com.eljaibo.pos.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository
        extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByBranchIdOrderByOccurredAtDesc(
            UUID branchId
    );
}