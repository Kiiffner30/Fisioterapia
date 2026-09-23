# FisioVida — Backend

## Escopo deste repositório

Este repositório contém **apenas o backend** do FisioVida.
O frontend está em um repositório separado.

## Objetivo

API REST do sistema de gestão interna da clínica de fisioterapia **FisioVida**. Nesta etapa o backend cobre
**autenticação** (login com JWT, refresh rotativo, logout e sessão atual), **gestão de usuários do sistema**
(exclusivo de `ADMIN`) e **auditoria** das ações sensíveis. As entidades de negócio (pacientes, agenda,
consultas, horários) **ainda não existem no código**.

## Stack

Extraída de `pom.xml` e de `src/main/resources/application.yml`:

| Item | Versão / Configuração |
|------|----------------------|
| Java | 21 (`<java.version>21</java.version>`, `release` 21) |
| Spring Boot | 3.5.3 (`spring-boot-starter-parent`) |
| Web / Validação | `spring-boot-starter-web`, `spring-boot-starter-validation` |
| Segurança | `spring-boot-starter-security` (stateless, `securityMatcher("/api/**")`) |
| JWT | JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) — assinatura **HS256** |
| Hash de senha | BCrypt (`BCryptPasswordEncoder`, versão `$2A`, strength **12**) |
| Persistência | Spring Data JPA / Hibernate — `ddl-auto: none`, `open-in-view: false` |
| Migrations | Flyway 11.7.2 (`flyway-database-postgresql`) |
| Banco | PostgreSQL 16 (driver `org.postgresql:postgresql`, escopo `runtime`) |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Testes | `spring-boot-starter-test`, `spring-security-test`, Testcontainers 1.20.6 (`postgresql`, `junit-jupiter`) |
| Jackson | `time-zone: America/Sao_Paulo`, `locale: pt-BR` |

## Arquitetura

**DDD + Clean Architecture**, com o domínio isolado de frameworks (as entidades de domínio não possuem
anotações JPA nem dependências de Spring; JPA é mapeado em classes `Jpa*` na infraestrutura).

Camadas (pacote base `com.clinica.fisioterapia`):

- **`domain`** — entidades e regras puras (`User`, `RefreshToken`, `AuditLog`), value objects (`Email`,
  `PasswordHash`), enums (`RoleName`, `AuditAction`), porta `Clock`, portas de repositório
  (`UserRepository`, `RefreshTokenRepository`, `AuditLogRepository`) e a porta `PasswordEncoder`.
- **`application`** — casos de uso e serviços (`AuthenticationService`, `CreateUserUseCase`,
  `ChangeUserStatusUseCase`, `FindUserByIdUseCase`, `AuditService`), a porta `TokenProvider` e
  `NotFoundException`. É a camada anotada com `@Transactional`.
- **`infrastructure`** — adaptadores: `JwtTokenProvider`, `BcryptPasswordEncoder`, `JwtAuthenticationFilter`,
  `HttpErrorWriter`, mapeadores/entidades JPA e repositórios (`*JpaRepository`, `*Mapper`) e configurações
  (`SecurityConfig`, `WebConfig`, `ApplicationBeansConfig`, `*Properties`, `SystemClock`,
  `AdminBootstrapRunner`).
- **`presentation`** — API REST: `AuthController`, `UserController`, DTOs de request/response e o
  `GlobalExceptionHandler` (formato de erro `ApiError` para as exceções de negócio/validação).

Documentação detalhada em [`ARCHITECTURE.md`](ARCHITECTURE.md) e contrato HTTP em [`API.md`](API.md).

## Estrutura de pastas

```
FisioVida/                        (raiz do repositorio)
├── mvnw, mvnw.cmd, pom.xml
├── .mvn/wrapper/maven-wrapper.properties
├── docker-compose.yml            (PostgreSQL 16)
├── .env.example
├── README.md, API.md, ARCHITECTURE.md
└── src
    ├── main
    │   ├── java/com/clinica/fisioterapia
    │   │   ├── FisioterapiaApplication.java
    │   │   ├── application/        (auth, user, audit, NotFoundException)
    │   │   ├── domain/             (user, auth, audit, common)
    │   │   ├── infrastructure/     (config, persistence, security)
    │   │   └── presentation/rest/  (controllers, dto, error)
    │   └── resources
    │       ├── application.yml     (comum, sem credenciais)
    │       ├── application-dev.yml (defaults ficticios de desenvolvimento)
    │       ├── application-prod.yml(sem defaults: exige variaveis de ambiente)
    │       └── db/migration/       (V1 … V5)
    └── test/java/com/clinica/fisioterapia
        ├── domain/                 (EmailTest, UserTest)
        └── infrastructure/security (AuthenticationFlowIT, BcryptPasswordEncoderTest, JwtTokenProviderTest)
```

## Pré-requisitos

- **Java 21** (compatível com `<java.version>21</java.version>` do `pom.xml`)
- **Docker + Docker Compose** (para o PostgreSQL e para os testes de integração via Testcontainers)
- **Maven Wrapper** — já incluído no repositório (`mvnw` / `mvnw.cmd`); não é necessário instalar Maven
- Portas livres: **5432** (PostgreSQL) e **8080** (backend)

## JDK 21 (desenvolvimento local)

Este projeto requer **Java 21**. Se o seu ambiente tem apenas um Java anterior (ex.: Java 8) ou um JDK com
versão superior, **não é necessário mexer no Java global da máquina**: baixe uma versão portátil (por
exemplo, [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21), `.zip` para Windows x64),
extraia em `.jdk/jdk-21/` e use apenas nesta sessão de terminal. A pasta `/.jdk/` já está no `.gitignore`.

```powershell
$env:JAVA_HOME = "$PWD\.jdk\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version    # deve exibir 21.x
```

Com o `JAVA_HOME` ajustado, os comandos da raiz funcionam normalmente
(`.\mvnw.cmd clean package -DskipTests`, `.\mvnw.cmd test`, `.\mvnw.cmd spring-boot:run`).

> ℹ️ O `pom.xml` fixa `<release>21</release>`; se você compilar com um JDK mais novo (ex.: 25), o
> `maven-compiler-plugin` gerará bytecode 21 — mas esteja ciente de que o Spring Boot 3.5.3 **não** é
> homologado para JDKs acima de 24. Prefira o JDK 21.

## Como subir o PostgreSQL

O `docker-compose.yml` fica na **raiz do repositório**:

```bash
docker compose up -d
```

Container: `fisioterapia-db` (imagem `postgres:16`, volume `fisioterapia_pgdata`, healthcheck `pg_isready`).
Para derrubar: `docker compose down` (mantém os dados) ou `docker compose down -v` (apaga o volume).

## Como rodar o backend

> Todos os comandos `./mvnw …` são executados na **raiz do repositório**.

```bash
./mvnw spring-boot:run
```

No Windows (PowerShell/CMD): `.\mvnw.cmd spring-boot:run`. A API sobe em `http://localhost:8080/api`.

## Como rodar os testes

```bash
./mvnw test
```

O `AuthenticationFlowIT` sobe um PostgreSQL real via **Testcontainers**, portanto o **Docker precisa estar
rodando**. Alternativa sem Testcontainers: exportar `TEST_DATABASE_URL`, `TEST_DATABASE_USER` e
`TEST_DATABASE_PASSWORD` apontando para um PostgreSQL já em execução.

## Como buildar

```bash
./mvnw clean package
```

## Variáveis de ambiente

Nomes **exatamente** como lidos em `application.yml` / `docker-compose.yml`. Os valores da coluna "Exemplo"
são placeholders de desenvolvimento — **nunca versione credenciais reais**. Ver também `.env.example`.

### Perfis de configuração

A configuração está separada por profile (arquivos em `src/main/resources`):

| Arquivo | Conteúdo | Quando é carregado |
|---------|----------|--------------------|
| `application.yml` | Configuração comum: aplicação, JPA, Flyway, Jackson, porta, cookie e expirações JWT — **sem credenciais** | sempre |
| `application-dev.yml` | Defaults **fictícios** de desenvolvimento: datasource, `JWT_SECRET`, CORS e bootstrap do ADMIN | profile `dev` (**padrão**) |
| `application-prod.yml` | **Sem defaults**: apenas `${VARIAVEL}` | profile `prod` |

O profile ativo é definido por `SPRING_PROFILES_ACTIVE` (padrão: `dev`).

> ⚠️ **Importante:** no profile `prod`, as variáveis `DATABASE_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` e
> `BOOTSTRAP_ADMIN_*` são **obrigatórias** — sem elas a aplicação **não sobe** (placeholder não resolvido).
> `SERVER_PORT`, `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION` e `COOKIE_SECURE` permanecem com default e
> podem ser sobrescritas por variável de ambiente.

### Banco de dados

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `DATABASE_HOST` | Host do PostgreSQL usado pelo backend | `localhost` |
| `DATABASE_PORT` | Porta do PostgreSQL | `5432` |
| `DATABASE_NAME` | Nome do banco | `fisioterapia` |
| `DATABASE_USERNAME` | Usuário do banco | `fisioterapia` |
| `DATABASE_PASSWORD` | Senha do banco | `change-me` |

O `spring.datasource.url` é montado como `jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_NAME}`.

### Servidor

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `SERVER_PORT` | Porta HTTP do backend | `8080` |

### JWT / sessão

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `JWT_SECRET` | Segredo HS256. Precisa ter no mínimo **32 caracteres/bytes** (exigência do HS256) | `change-me-to-a-long-random-secret` |
| `JWT_ACCESS_EXPIRATION` | Validade do access token em **ISO-8601 Duration** (não é segundos!) | `PT15M` |
| `JWT_REFRESH_EXPIRATION` | Validade do refresh token em ISO-8601 Duration | `P30D` |

> **Importante:** as durações JWT usam o formato **ISO-8601** (ex.: `PT15M` = 15 minutos, `P30D` = 30 dias).
> **Não** são segundos.

### CORS / cookie de refresh

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `CORS_ALLOWED_ORIGINS` | Origens permitidas em `/api/**` (`allowCredentials: true`) | `http://localhost:3000` |
| `COOKIE_SECURE` | Marca a cookie `refresh_token` como `Secure` (usar `true` em HTTPS) | `false` |

> O nome (`refresh_token`), o `path` (`/api`) e o `sameSite` (`lax`) da cookie estão **fixos em
> `application.yml`** (`app.cookie.*`) e não possuem variável de ambiente.

### Bootstrap do ADMIN

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `BOOTSTRAP_ADMIN_EMAIL` | E-mail do administrador inicial | `admin@fisiovida.local` |
| `BOOTSTRAP_ADMIN_PASSWORD` | Senha do administrador inicial (mínimo 8 caracteres) | `change-me` |

### Docker Compose (consumidas pelo serviço `db`)

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `POSTGRES_DB` | Banco criado pelo container | `fisioterapia` |
| `POSTGRES_USER` | Usuário criado pelo container | `fisioterapia` |
| `POSTGRES_PASSWORD` | Senha do usuário do container | `change-me` |
| `POSTGRES_PORT` | Porta publicada no host | `5432` |

> ⚠️ `POSTGRES_*` (Docker) e `DATABASE_*` (backend) são **dois conjuntos independentes**. Mantenha-os
> consistentes manualmente.

### Testes de integração (opcionais)

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `TEST_DATABASE_URL` | JDBC URL de um PostgreSQL já em execução (dispensa o Testcontainers) | `jdbc:postgresql://localhost:5432/fisioterapia` |
| `TEST_DATABASE_USER` | Usuário desse banco de testes | `fisioterapia` |
| `TEST_DATABASE_PASSWORD` | Senha desse banco de testes | `change-me` |

> ⚠️ **Como as variáveis chegam na aplicação:** o Spring Boot **não lê arquivos `.env`**. O `.env` da raiz é
> consumido **apenas pelo Docker Compose**; para o backend, exporte as variáveis no shell ou configure-as na
> *Run Configuration* da IDE.

## Bootstrap do ADMIN

Na inicialização, o `AdminBootstrapRunner` (`CommandLineRunner`) verifica se já existe um usuário com o
e-mail de `BOOTSTRAP_ADMIN_EMAIL`. Se **não** existir, cria um usuário `ADMIN` com nome `Administrador` e a
senha de `BOOTSTRAP_ADMIN_PASSWORD` (hash BCrypt). Regras observadas no código:

- Se o e-mail já existir, **nada é feito** (idempotente).
- Se a senha estiver ausente ou tiver **menos de 8 caracteres**, o usuário **não** é criado e um `WARN` é
  registrado no log.
- O runner tem `Ordered.LOWEST_PRECEDENCE`, ou seja, executa **depois** das migrations Flyway.
- Nenhuma senha é armazenada em texto plano: apenas o hash BCrypt (`users.password_hash`).

Fora de ambiente local, defina `BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_PASSWORD` por variável de ambiente
(não versione esses valores). Os defaults embutidos em `application.yml` são **apenas para desenvolvimento**.

## Migrations Flyway

Localizadas em `src/main/resources/db/migration` (Flyway com `baseline-on-migrate: true`):

| Arquivo | O que faz |
|---------|-----------|
| `V1__create_roles.sql` | Cria a tabela `roles` (`id BIGSERIAL`, `name VARCHAR(50) UNIQUE`). |
| `V2__create_users.sql` | Cria `users` (`id UUID`, `name`, `email` único, `password_hash`, FK `role_id`, `active`, `created_at`, `updated_at`) + índices `idx_users_email`, `idx_users_role`. |
| `V3__create_audit_logs.sql` | Cria `audit_logs` (UUID, `user_id` nullable, `action`, `entity`, `entity_id`, `payload_before`/`payload_after` JSONB, `created_at`) + índices por usuário, ação e data. |
| `V4__seed_roles.sql` | Insere os perfis `ADMIN`, `ATENDENTE`, `FISIOTERAPEUTA` (`ON CONFLICT DO NOTHING`). O admin inicial **não** é seedado aqui (vem do bootstrap por env). |
| `V5__create_refresh_tokens.sql` | Cria `refresh_tokens` (`id UUID`, FK `user_id`, `token_hash` único (SHA-256), `expires_at`, `revoked_at`, `created_at`, `version`) + índices por usuário e por hash. |

## Roles

Perfis definidos em `RoleName` e semeados por `V4__seed_roles.sql`:

- **ADMIN** — única role com autorização explícita hoje (`@PreAuthorize("hasRole('ADMIN')")`): criar usuários
  (`POST /api/users`) e ativar/desativar usuários (`PATCH /api/users/{id}/status`).
- **ATENDENTE** — autentica e acessa as rotas autenticadas (`GET /api/auth/me`), mas recebe **403** nas rotas
  exclusivas de ADMIN. Não possui casos de uso próprios ainda.
- **FISIOTERAPEUTA** — mesma situação de `ATENDENTE` (comportamento coberto por `AuthenticationFlowIT`).

A autoridade no `SecurityContext` é montada como `ROLE_<RoleName>` pelo `JwtAuthenticationFilter`; a
autorização é **sempre** validada no backend.

## Próximos passos

Ainda **não implementado** (não há código, endpoints nem tabelas para isso):

- Cadastro de **pacientes** (lista, detalhe, dados clínicos).
- **Agenda** de atendimentos e **consultas** (criar/remarcar/cancelar).
- **Tipos de consulta** e **horários** de atendimento.
- Dados da **clínica** e **configurações** do sistema.
- Listagem/consulta de usuários (`GET /api/users`) e edição/troca de senha.
- Recuperação de senha e exposição da trilha de **auditoria** via API (hoje os registros só são gravados).

## Notas, débitos técnicos e pontos A VALIDAR

**Resolvido nesta fase de documentação:**

- **Escopo:** este repositório contém **apenas o backend**; o frontend está em repositório separado e não é
  documentado aqui.
- **Endpoint de sessão:** o caminho canônico é **`GET /api/auth/me`** (ver [`API.md`](API.md)).
- **Variáveis de ambiente:** os nomes são os **reais do código** (`DATABASE_*`, `SERVER_PORT`, `JWT_*`,
  `CORS_ALLOWED_ORIGINS`, `COOKIE_SECURE`, `BOOTSTRAP_ADMIN_*`, `POSTGRES_*`) e o `.env.example` contém
  **apenas** variáveis do backend.
- ✅ **Resolvido:** configuração separada por profile (`application-dev.yml` / `application-prod.yml`), sem
  credenciais no `application.yml` principal.
- ✅ **Resolvido:** o código do backend agora vive na **raiz do repositório** (movido com `git mv`, histórico
  preservado).
- ✅ **Resolvido:** a pasta `frontend/` foi **removida do tracking** do Git (continua no disco, ignorada pelo
  `.gitignore`).

**Idioma:** a documentação está em **pt-BR**. O código-fonte pode conter termos em espanhol em algumas
classes (javadocs e mensagens de validação) — padronização futura; **nenhum código foi alterado** nesta etapa.

**Débitos técnicos conhecidos** (correção prevista para a Parte 5 — detalhados em
[`ARCHITECTURE.md`](ARCHITECTURE.md) e [`API.md`](API.md)):

- `AuditAction.LOGOUT` existe mas **não** é gravado por `AuthenticationService.logout`.
- `FindUserByIdUseCase` tem javadoc citando "`/api/me`" em vez de `GET /api/auth/me`.
- **Dois formatos de erro** (`ApiError` × `HttpErrorWriter`) e duplicidade no tratamento de 403.
- Domínio de negócio da clínica (pacientes, agenda, consultas, tipos, horários, clínica, configurações) ainda
  **não existe**.

**Pontos A VALIDAR (decisões pendentes):**

- ⚠️ **A VALIDAR:** a pasta `backend/target/` (artefato de build **não rastreado**, anterior à reorganização)
  permanece no disco. Decidir se deve ser removida — na nova estrutura o build gera `target/` na raiz.
- ⚠️ **A VALIDAR:** existe um `package-lock.json` **órfão** na raiz (sem `package.json` correspondente,
  resquício do scaffold do frontend). Decidir se deve ser removido deste repositório backend-only.
