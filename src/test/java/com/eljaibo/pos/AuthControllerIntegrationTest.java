package com.eljaibo.pos;

import com.eljaibo.pos.business.domain.Business;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

        appUserRepository.save(
                new AppUser(
                        business,
                        "admin",
                        passwordEncoder.encode("Admin123!"),
                        "Administrador",
                        UserRole.ADMIN
                )
        );
    }

    @Test
    void shouldLoginAndAccessCurrentUser() throws Exception {
        String response = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "Admin123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(43200))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = JsonPath.read(
                response,
                "$.accessToken"
        );

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.displayName")
                        .value("Administrador"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "incorrect"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectProtectedEndpointWithoutToken()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBlankCredentials() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "",
                                          "password": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }
}