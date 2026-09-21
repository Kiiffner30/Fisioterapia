# Arquitetura — FisioVida Backend

## Visão geral

O backend segue **DDD + Clean Architecture** em um único módulo Maven, com o pacote base
`com.clinica.fisioterapia` dividido em quatro camadas. A regra de dependência é explícita no código: o
`domain` **não** importa Spring nem JPA (as entidades de domínio são classes puras e os repositórios são
interfaces/portas); a `infrastructure` implementa essas portas e é quem conhece JPA, JJWT, BCrypt e Spring
Security; a `application` orquestra casos de uso sobre as portas; a `presentation` só fala HTTP.

## Camadas

### `domain` — regra de negócio pura

- `user`: `User` (entidade, com `User.create(...)` e `changeStatus(...)`), `RoleName` (ADMIN, ATENDENTE,
  FISIOTERAPEUTA + `from(String)` case-insensitive), `UserRepository`, `PasswordEncoder` (porta).
- `auth`: `RefreshToken` (`issue`, `revoke`, `isValid`) e `RefreshTokenRepository`.
- `audit`: `AuditLog`, `AuditLogRepository` e `AuditAction` (LOGIN, LOGIN_FAILED, REFRESH, LOGOUT,
  USER_CREATED, USER_ACTIVATED, USER_DEACTIVATED).
- `common`: value objects `Email` e `PasswordHash` e a porta `Clock` (tempo injetado para testabilidade).

Validações de invariantes ficam aqui (`User` exige nome não vazio, e-mail/hash/role não nulos).

### `application` — casos de uso

- `AuthenticationService` — login, refresh rotativo e logout; emite tokens e registra auditoria.
- `CreateUserUseCase` / `ChangeUserStatusUseCase` / `FindUserByIdUseCase` — casos de uso de usuário.
- `AuditService` — gravação centralizada da trilha de auditoria (classe pura, sem anotação de transação).
- `TokenProvider` (porta de token) e `NotFoundException`.
- As fronteiras transacionais ficam **aqui**: `AuthenticationService`, `CreateUserUseCase` e
  `ChangeUserStatusUseCase` são anotados com `@Transactional` (o `AuditService`, não — ele participa da
  transação do caso de uso que o chama). O `domain` permanece livre de anotações de framework.

### `infrastructure` — adaptadores

- `config`: `ApplicationBeansConfig` (monta os casos de uso como `@Bean`, já que a camada de aplicação não
  usa `@Service`), `SecurityConfig`, `WebConfig` (CORS), `AdminBootstrapRunner`, `SystemClock` e as
  `@ConfigurationProperties` (`JwtProperties`, `CookieProperties`, `CorsProperties`, `BootstrapProperties` —
  registradas por `@ConfigurationPropertiesScan`).
- `persistence`: entidades JPA (`JpaUserEntity`, `JpaRoleEntity`, `JpaRefreshTokenEntity`,
  `JpaAuditLogEntity`), mapeadores (`UserMapper`, `RefreshTokenMapper`, `AuditLogMapper`) e os adaptadores de
  repositório (`JpaUserRepository`, `RefreshTokenJpaRepository`, `AuditLogJpaRepository`, `RoleJpaRepository`).
- `security`: `JwtTokenProvider` (JJWT + SHA-256), `BcryptPasswordEncoder`, `JwtAuthenticationFilter`,
  `HttpErrorWriter` e `AuthenticatedUser` (principal do Spring Security).

### `presentation` — HTTP

- `controllers`: `AuthController` (`/api/auth`) e `UserController` (`/api/users`).
- `dto`: `LoginRequest`, `CreateUserRequest`, `ChangeUserStatusRequest`, `TokenResponse`, `UserResponse`.
- `error`: `ApiError` e `GlobalExceptionHandler` (`@RestControllerAdvice`).

## Fluxo de uma requisição

**Login (`POST /api/auth/login`):**

```
HTTP → AuthController.login
     → AuthenticationService.login (application, @Transactional)
     → UserRepository.findByEmail (porta) → JpaUserRepository (adaptador) → PostgreSQL
     → BcryptPasswordEncoder.matches
     → TokenProvider.issueAccessToken / issueRefreshToken (JwtTokenProvider)
     → RefreshTokenRepository.save (persiste só o SHA-256)
     → AuditService.record(LOGIN)
     ← TokenResponse + Set-Cookie: refresh_token (HttpOnly)
```

**Rota protegida (ex.: `GET /api/auth/me`):**

```
HTTP → JwtAuthenticationFilter (lê "Authorization: Bearer …")
     → TokenProvider.parse → presente?  ── não ──→ 401 JSON (HttpErrorWriter)
     → Autentica o SecurityContext (ROLE_<role>) → SecurityFilterChain
     → @PreAuthorize (quando ADMIN) → Controller
     → Caso de uso → Repositório (porta) → Mapeador JPA → PostgreSQL
     ← DTO de resposta
```

Erros levantados na camada de aplicação/domínio sobem como exceções e são convertidos em JSON pelo
`GlobalExceptionHandler`; erros anteriores ao DispatcherServlet (token ausente/inválido, acesso negado no
filtro) usam o `HttpErrorWriter`.

## Decisões técnicas observadas no código

- **Autenticação stateless com JWT:** `SessionCreationPolicy.STATELESS`, `csrf` desabilitado e
  `securityMatcher("/api/**")`. Nada de sessão em memória.
- **Dois tokens com papéis distintos:** *access token* = **JWT HS256** (`iss = fisioterapia`, `sub` = UUID,
  claims `email`/`role`, TTL `PT15M`); *refresh token* = **valor opaco** aleatório (Base64URL de 32 bytes),
  cuja única representação persistida é o **hash SHA-256** (`refresh_tokens.token_hash`, coluna `UNIQUE`).
- **Rotação de refresh:** a cada `POST /api/auth/refresh` o token usado é revogado (`revoked_at`) e um novo
  par é emitido; `logout` revoga o token corrente. Consequência conhecida: o *access token* **não** é
  invalidado no logout (não há blacklist).
- **Refresh token em cookie `HttpOnly`** (`SameSite=Lax`, `Path=/api`, `Secure` configurável), reduzindo a
  exposição a XSS; o access token é entregue no body para uso via header `Authorization`.
- **Autorização centralizada no backend:** `anyRequest().authenticated()` + `@PreAuthorize("hasRole('ADMIN')")`
  nos endpoints administrativos; as authorities são montadas como `ROLE_<RoleName>` pelo filtro.
- **BCrypt com strength 12** (`$2A`), atrás da porta `PasswordEncoder` do domínio.
- **Flyway como única fonte do schema:** `ddl-auto: none` — o Hibernate nunca cria/altera tabelas; toda
  mudança passa por `db/migration` (`baseline-on-migrate: true`).
- **Domínio sem framework:** entidades e value objects não têm anotação JPA/Spring; o mapeamento é feito por
  entidades `Jpa*` + mapeadores na infraestrutura.
- **Tempo injetado:** a porta `Clock` (`SystemClock` na infraestrutura) evita `Instant.now()` dentro do
  domínio e permite testes determinísticos.
- **Erros uniformes:** `GlobalExceptionHandler` padroniza o corpo `ApiError` e **não** expõe stack trace
  (o detalhe vai para o log do servidor, `500 Error interno` para o cliente).
- **Bootstrap idempotente do ADMIN** por variável de ambiente, com aviso em log se a senha for fraca/ausente
  (`AdminBootstrapRunner`, `LOWEST_PRECEDENCE` = depois das migrations).
- **Auditoria em ações sensíveis** (`audit_logs` com payloads JSONB before/after), sem gravar senhas nem
  tokens — apenas dados não sensíveis, como o e-mail.
- **Configuração externalizada:** `application.yml` usa `${VAR:default}` para todas as variáveis relevantes;
  `app.cookie.*` (nome, path, sameSite) é a única parte fixa.

## Testes

- `AuthenticationFlowIT` — **integração** com `@SpringBootTest` + `MockMvc` + **Testcontainers**
  (`postgresql:16`, `baseline` via Flyway): cobre login, cookie `refresh_token`, rotação, logout com
  revogação, permissões por role (403 para não-ADMIN), validação (400) e gravação em `audit_logs`.
  Suporta o modo "sem Docker" via `TEST_DATABASE_URL`/`TEST_DATABASE_USER`/`TEST_DATABASE_PASSWORD`.
- Testes de unidade: `EmailTest`, `UserTest`, `JwtTokenProviderTest`, `BcryptPasswordEncoderTest`.
- O `maven-surefire-plugin` inclui `*Test.java`, `*Tests.java` e `*IT.java`.

## Pontos A VALIDAR (arquitetura)

- ⚠️ **A VALIDAR:** `LOGOUT` existe em `AuditAction` mas **nunca é gravado** (`AuthenticationService.logout`
  não chama `audit.record`). Decidir se deve ser auditado.
- ⚠️ **A VALIDAR:** `FindUserByIdUseCase` tem o javadoc referindo-se a "`/api/me`", porém o endpoint real é
  `GET /api/auth/me`.
- ⚠️ **A VALIDAR:** `GlobalExceptionHandler` trata `AccessDeniedException` (403 no formato `ApiError`), mas o
  `accessDeniedHandler` do `SecurityConfig` também responde 403 (formato do `HttpErrorWriter`). Existem,
  portanto, **dois formatos de erro** — alinhar antes de o frontend tratar erros.
- ⚠️ **A VALIDAR:** o domínio ainda não modela o negócio da clínica (pacientes, agenda, consultas, tipos,
  horários, clínica, configurações) — não há camadas nem endpoints para isso.
