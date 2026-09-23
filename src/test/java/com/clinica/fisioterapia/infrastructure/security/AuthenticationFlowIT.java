package com.clinica.fisioterapia.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion del flujo de autenticacion contra PostgreSQL real
 * (Testcontainers) con las migraciones Flyway aplicadas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIT {

    private static final String ADMIN_EMAIL = "admin@clinica.test";
    private static final String ADMIN_PASSWORD = "AdminTest-123";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer local = startPostgresIfNeeded();
        if (local != null) {
            registry.add("spring.datasource.url", () -> local.getJdbcUrl());
            registry.add("spring.datasource.username", () -> local.getUsername());
            registry.add("spring.datasource.password", () -> local.getPassword());
        } else {
            // Modo override: se usa un PostgreSQL ya en ejecucion (p.ej. local sin Docker)
            registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
        }
        registry.add("app.bootstrap.admin-email", () -> ADMIN_EMAIL);
        registry.add("app.bootstrap.admin-password", () -> ADMIN_PASSWORD);
        registry.add("jwt.secret", () -> "test-secret-integracion-0123456789abcdefghijkl");
        registry.add("jwt.access-expiration", () -> "PT15M");
        registry.add("jwt.refresh-expiration", () -> "P1D");
    }

    private static final Object LOCK = new Object();
    private static PostgreSQLContainer postgres;

    private static PostgreSQLContainer startPostgresIfNeeded() {
        String override = System.getenv("TEST_DATABASE_URL");
        if (override != null && !override.isBlank()) {
            return null; // usa el Postgres ya corriendo (p.ej. local sin Docker)
        }
        synchronized (LOCK) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer(
                        DockerImageName.parse("postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
                        .withDatabaseName("fisioterapia")
                        .withUsername("fisioterapia")
                        .withPassword("fisioterapia");
                postgres.start();
            }
            return postgres;
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void limpiarTablasTransitorias() {
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM audit_logs");
    }

    @Test
    void loginConCredencialesValidasEntregaAccessTokenYCookieHttpOnly() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        assertTrue(contarAudit("LOGIN") >= 1);
        assertNotNull(accessTokenDe(result));
    }

    @Test
    void loginConContrasenaIncorrectaDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"ClaveIncorrecta!\"}"))
                .andExpect(status().isUnauthorized());

        assertTrue(contarAudit("LOGIN_FAILED") >= 1);
    }

    @Test
    void loginConEmailInexistenteDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@clinica.test\",\"password\":\"Cualquiera-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoConTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer token-invalido-abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevuelveElUsuarioAutenticado() throws Exception {
        MvcResult login = loginAdmin();
        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessTokenDe(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void refreshRotaElTokenYElUsoDelAnteriorFalla() throws Exception {
        MvcResult login = loginAdmin();
        String cookie = login.getResponse().getCookie("refresh_token").getValue();

        mvc.perform(post("/api/auth/refresh")
                        .cookie(login.getResponse().getCookie("refresh_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andReturn();

        // El token ya usado queda revocado: reutilizarlo debe fallar
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreaUsuarioYElNuevoUsuarioPuedeLoguearse() throws Exception {
        MvcResult login = loginAdmin();
        String email = "atendente-" + UUID.randomUUID() + "@clinica.test";

        MvcResult created = mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessTokenDe(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Andrea Gómez\",\"email\":\"" + email + "\",\"password\":\"Clave-12345\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ATENDENTE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String userId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Clave-12345\"}"))
                .andExpect(status().isOk());

        assertTrue(contarAudit("USER_CREATED") >= 1);

        // Desactivar usuario y comprobar que ya no puede iniciar sesion
        mvc.perform(patch("/api/users/{id}/status", userId)
                        .header("Authorization", "Bearer " + accessTokenDe(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Clave-12345\"}"))
                .andExpect(status().isUnauthorized());

        assertTrue(contarAudit("USER_DEACTIVATED") >= 1);
    }

    @Test
    void fisioterapeutaNoPuedeCrearUsuarios() throws Exception {
        MvcResult adminLogin = loginAdmin();
        String physioEmail = "fisio-" + UUID.randomUUID() + "@clinica.test";

        MvcResult physioCreated = mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessTokenDe(adminLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Federico Ruiz\",\"email\":\"" + physioEmail + "\",\"password\":\"Clave-12345\",\"role\":\"FISIOTERAPEUTA\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String physioId = objectMapper.readTree(physioCreated.getResponse().getContentAsString()).get("id").asText();

        MvcResult physioLogin = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + physioEmail + "\",\"password\":\"Clave-12345\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Un FISIOTERAPEUTA no puede crear usuarios ni desactivarlos (403)
        mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessTokenDe(physioLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otro\",\"email\":\"otro@clinica.test\",\"password\":\"Clave-12345\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/users/{id}/status", physioId)
                        .header("Authorization", "Bearer " + accessTokenDe(physioLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevocaLaSesion() throws Exception {
        MvcResult login = loginAdmin();
        String cookie = login.getResponse().getCookie("refresh_token").getValue();

        mvc.perform(post("/api/auth/logout")
                        .cookie(login.getResponse().getCookie("refresh_token")))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validacionDeEntradaDevuelve400() throws Exception {
        MvcResult login = loginAdmin();
        mvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + accessTokenDe(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"email\":\"email-invalido\",\"password\":\"corta\",\"role\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private MvcResult loginAdmin() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String accessTokenDe(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private int contarAudit(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
        return count;
    }
}