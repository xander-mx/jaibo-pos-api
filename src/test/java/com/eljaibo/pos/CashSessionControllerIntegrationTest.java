package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Branch;
import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BranchRepository;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.cash.persistence.CashSessionEventRepository;
import com.eljaibo.pos.terminal.domain.Terminal;
import com.eljaibo.pos.terminal.persistence.TerminalRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        TestcontainersConfiguration.class,
        CashSessionControllerIntegrationTest.FixedClockConfiguration.class
})
@Transactional
class CashSessionControllerIntegrationTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-09-06T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Autowired
    private CashSessionEventRepository eventRepository;

    private AppUser admin;
    private Terminal terminal;

    @BeforeEach
    void setUp() {
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

        admin = appUserRepository.save(
                new AppUser(
                        business,
                        "admin",
                        "encoded-password-for-test",
                        "Administrador",
                        UserRole.ADMIN
                )
        );

        terminal = terminalRepository.save(
                new Terminal(
                        branch,
                        admin,
                        "Tableta principal"
                )
        );
    }

    @Test
    void shouldOpenCloseAndReopenSharedCashSession()
            throws Exception {
        String openResponse = mockMvc.perform(
                        post("/api/v1/cash-sessions/open")
                                .with(authenticatedUser())
                                .header(
                                        "X-Terminal-Id",
                                        terminal.getId()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "openingAmount": 500.00
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openingAmount").value(500.00))
                .andExpect(jsonPath("$.businessDate")
                        .value("2026-09-06"))
                .andExpect(jsonPath("$.cycleStartsOn")
                        .value("2026-09-04"))
                .andExpect(jsonPath("$.cycleEndsOn")
                        .value("2026-09-10"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String cashSessionId = JsonPath.read(
                openResponse,
                "$.id"
        );

        mockMvc.perform(
                        get("/api/v1/cash-sessions/current")
                                .with(authenticatedUser())
                                .header(
                                        "X-Terminal-Id",
                                        terminal.getId()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashSessionId));

        mockMvc.perform(
                        post(
                                "/api/v1/cash-sessions/{id}/close",
                                cashSessionId
                        )
                                .with(authenticatedUser())
                                .header(
                                        "X-Terminal-Id",
                                        terminal.getId()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "countedAmount": 480.00
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closingExpectedAmount")
                        .value(500.00))
                .andExpect(jsonPath("$.closingCountedAmount")
                        .value(480.00))
                .andExpect(jsonPath("$.closingDifference")
                        .value(-20.00));

        mockMvc.perform(
                        get("/api/v1/cash-sessions/current")
                                .with(authenticatedUser())
                                .header(
                                        "X-Terminal-Id",
                                        terminal.getId()
                                )
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post(
                                "/api/v1/cash-sessions/{id}/reopen",
                                cashSessionId
                        )
                                .with(authenticatedUser())
                                .header(
                                        "X-Terminal-Id",
                                        terminal.getId()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "comment": "Faltó registrar una venta"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.closingExpectedAmount")
                        .doesNotExist());

        UUID sessionId = UUID.fromString(cashSessionId);

        assertThat(
                eventRepository
                        .findAllByCashSessionIdOrderByOccurredAtAsc(
                                sessionId
                        )
        ).hasSize(3);
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
    authenticatedUser() {
        return jwt().jwt(token -> token
                .subject(admin.getUsername())
                .claim("user_id", admin.getId().toString())
                .claim(
                        "business_id",
                        admin.getBusiness().getId().toString()
                )
                .claim("display_name", admin.getDisplayName())
                .claim("roles", java.util.List.of("ADMIN"))
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );
        }
    }
}