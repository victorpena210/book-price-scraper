package com.victorpena.contacttracker.security;

import com.victorpena.contacttracker.contact.MelissaPersonSearchClient;
import com.victorpena.contacttracker.contact.SavedPeopleMelissaService;
import com.victorpena.contacttracker.contact.SavedPeopleReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    private static final String EMAIL = "clay@example.test";
    private static final String PASSWORD = "Test-only-password-987!";
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean SavedPeopleReader reader;
    @MockitoBean SavedPeopleMelissaService lookups;
    @MockitoBean MelissaPersonSearchClient client;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM app_users");
        accounts.createIfMissing(EMAIL, PASSWORD);
        when(reader.catalog()).thenReturn(new SavedPeopleReader.Catalog(0, 0, List.of()));
        when(lookups.test(List.of(1L))).thenReturn(new SavedPeopleMelissaService.BatchResult(List.of(), 0, false, "Finished"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/index.html"))
                .andReturn().getRequest().getSession(false);
    }

    @Test void anonymousUsersCannotReadDashboardOrCallApis() throws Exception {
        for (String path : List.of("/", "/index.html", "/saved-people.js")) {
            mvc.perform(get(path)).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        }
        for (String path : List.of("/api/session", "/api/people/melissa/records", "/api/people/missing-phone", "/api/melissa/search")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/people/melissa/test").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"personIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reader, lookups, client);
    }

    @Test void loginFormHasTokenAndGenericErrorsAndRequiresCsrf() throws Exception {
        mvc.perform(get("/login?error")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to sign in")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(post("/login").param("email", EMAIL).param("password", PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test void authenticatedSessionCanReadAndPaidRequestsRequireRealSessionToken() throws Exception {
        var session = login(" CLAY@EXAMPLE.TEST ", PASSWORD);
        mvc.perform(get("/index.html").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/people/melissa/records").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/people/melissa/test").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"personIds\":[1]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/people/melissa/test").session(session).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"personIds\":[1]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lookups);
        var result = mvc.perform(get("/api/session").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL)).andReturn();
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        mvc.perform(post("/api/people/melissa/test").session(session)
                .header(body.get("csrfHeader").asText(), body.get("csrfToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"personIds\":[1]}"))
                .andExpect(status().isOk());
        verify(lookups).test(List.of(1L));
    }

    @Test void paidLegacyRoutesCannotBeTriggeredByGet() throws Exception {
        var session = login(EMAIL, PASSWORD);
        for (String path : List.of("/api/melissa/diagnose", "/api/melissa/search", "/api/melissa/search-by-name", "/api/melissa/search-by-location", "/api/obituaries/melissa")) {
            mvc.perform(get(path).session(session)).andExpect(status().isMethodNotAllowed());
            mvc.perform(post(path).session(session)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(client);
    }

    @Test void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        var session = login(EMAIL, PASSWORD);
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/people/melissa/records")).andExpect(status().isUnauthorized());
    }

    @Test void loginRotatesTheSessionId() throws Exception {
        var oldSession = new MockHttpSession();
        String oldId = oldSession.getId();
        var result = mvc.perform(post("/login").session(oldSession).with(csrf()).param("email", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/index.html")).andReturn();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(oldId);
    }

    @Test void repeatedBadPasswordsLockAccountThenExpiredLockAllowsLogin() throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", "wrong"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        assertThat(accounts.loadUserByUsername(EMAIL).isAccountNonLocked()).isFalse();
        mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        jdbc.update("UPDATE app_users SET locked_until = ? WHERE email = ?", Timestamp.from(Instant.now().minusSeconds(1)), EMAIL);
        login(EMAIL, PASSWORD);
        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM app_users WHERE email = ?", Integer.class, EMAIL)).isZero();
    }

    @Test void bootstrapHashesPasswordAndNeverResetsAnExistingUser() {
        String original = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email = ?", String.class, EMAIL);
        assertThat(original).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, original)).isTrue();
        assertThat(accounts.createIfMissing(EMAIL, "Another-test-password-123!")).isFalse();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email = ?", String.class, EMAIL)).isEqualTo(original);
        var bootstrap = new AccountBootstrap(accounts, "new@example.test", PASSWORD);
        bootstrap.run(null);
        assertThat(accounts.loadUserByUsername("new@example.test").isEnabled()).isTrue();
        assertThatThrownBy(() -> accounts.createIfMissing("short@example.test", "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void disabledAccountCannotLoginAndThereIsNoPublicRegistration() throws Exception {
        jdbc.update("UPDATE app_users SET enabled = FALSE WHERE email = ?", EMAIL);
        mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/register").with(csrf()).param("email", "other@example.test").param("password", PASSWORD))
                .andExpect(status().isFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(1);
    }
}
