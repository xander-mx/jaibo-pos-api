package com.eljaibo.pos.expense.api.controller;

import com.eljaibo.pos.expense.api.request.CreateExpenseRequest;
import com.eljaibo.pos.expense.api.response.ExpenseResponse;
import com.eljaibo.pos.expense.application.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(
            ExpenseService expenseService
    ) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId,
            @Valid
            @RequestBody
            CreateExpenseRequest request
    ) {
        return expenseService.create(
                claim(jwt, "user_id"),
                claim(jwt, "business_id"),
                terminalId,
                request
        );
    }

    @GetMapping
    public List<ExpenseResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Terminal-Id")
            UUID terminalId
    ) {
        return expenseService.findAll(
                claim(jwt, "business_id"),
                terminalId
        );
    }

    private UUID claim(
            Jwt jwt,
            String name
    ) {
        return UUID.fromString(
                Objects.requireNonNull(
                        jwt.getClaimAsString(name)
                )
        );
    }
}