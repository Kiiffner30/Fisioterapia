# API — FisioVida Backend

Documentação dos endpoints **existentes** no código (lidos de `AuthController` e `UserController`).
Não há versionamento de API (`/v1`) e **não há Swagger/OpenAPI** configurado (não existe `springdoc` no
`pom.xml`).

## Base URL

`http://localhost:8080/api`

(porta configurável por `SERVER_PORT`; o path `/api` está fixado em `@RequestMapping` dos controllers).

## Autenticação

Fluxo implementado (`AuthController` + `AuthenticationService` + `JwtTokenProvider`):

1. **Login** (`POST /api/auth/login`): o **access token** volta no **body** (`accessToken`,
   `expiresInSeconds`, `user`) e o **refresh token** volta em uma **cookie `HttpOnly`** — ele **nunca**
   aparece no body.
2. **Cookie do refresh:** nome `refresh_token`, `HttpOnly`, `Path=/api`, `SameSite=Lax`,
   `Secure=${COOKIE_SECURE:false}`, `Max-Age` igual a `JWT_REFRESH_EXPIRATION` (default `P30D`).
3. **Requisições protegidas:** envie `Authorization: Bearer <accessToken>`. Sem esse header (ou com token
   expirado/inválido) o `JwtAuthenticationFilter` responde **401** antes de chegar ao controller.
4. **Access token** é um **JWT HS256** com `iss: "fisioterapia"`, `sub` = UUID do usuário e as claims
   `email` e `role`. Não existe *blacklist*: ao fazer logout o access token continua válido até expirar.
5. **Refresh token** é **opaco** (não é JWT): 32 bytes aleatórios em Base64URL; no banco (`refresh_tokens`)
   só é persistido o **SHA-256**. A cada refresh o token anterior é **revogado** (rotação).
6. Esteja atento à *cookie*: como ela vive no path `/api`, as chamadas que enviam credenciais (cookies)
   precisam respeitar esse path. O `WebConfig` habilita CORS com `allowCredentials: true` para as origens de
   `CORS_ALLOWED_ORIGINS`.

## Roles e permissões

- **ADMIN** — autentica e é o **único** perfil com `@PreAuthorize("hasRole('ADMIN')")`: pode
  `POST /api/users` e `PATCH /api/users/{id}/status`.
- **ATENDENTE** — autentica e acessa rotas autenticadas (`GET /api/auth/me`); recebe **403** nas rotas de
  ADMIN. Sem casos de uso próprios implementados.
- **FISIOTERAPEUTA** — mesmo comportamento de `ATENDENTE` (validado em `AuthenticationFlowIT`).

## CORS e integração com o frontend (repositório separado)

### Configuração verificada no código

Fonte: `WebConfig` (`WebMvcConfigurer.addCorsMappings`) + `CorsProperties` (`@ConfigurationProperties("app.cors")`).
**Não existe `@CrossOrigin` em nenhum controller e nenhuma origem está *hardcoded*.**

| Item | Valor | Origem |
|------|-------|--------|
| Origens | `CORS_ALLOWED_ORIGINS` (lista separada por vírgula) | env → `app.cors.allowed-origins` |
| Default em `dev` | `http://localhost:3000` | `application-dev.yml` |
| Em `prod` | **obrigatória** (sem default) | `application-prod.yml` |
| Paths | `/api/**` | `registry.addMapping("/api/**")` |
| Métodos | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | `allowedMethods(...)` |
| Headers | `*` (inclui `Authorization` e `Content-Type`) | `allowedHeaders("*")` |
| Credenciais | `allowCredentials(true)` | necessário para a cookie `refresh_token` |
| Cache do preflight | `maxAge(3600)` (1 hora) | `registry...maxAge(3600)` |

> ⚠️ **Não use `*` em `CORS_ALLOWED_ORIGINS`:** com `allowCredentials: true` o navegador **rejeita**
> `Access-Control-Allow-Origin: *` em requisições com credenciais. Liste as origens exatas.

### ✅ Caminho recomendado: proxy same-origin (dispensa CORS)

O javadoc do próprio `WebConfig` documenta o desenho pretendido: **em desenvolvimento o frontend expõe `/api`
no próprio host e faz proxy para `http://localhost:8080`** (ex.: `rewrites` no `next.config.ts`). Nesse modo:

- não há requisição cross-origin → **CORS e preflight deixam de ser problema**;
- a cookie `refresh_token` (`HttpOnly`, `Path=/api`) viaja como **same-origin**, sem depender de `SameSite`.

### ⚠️ Análise estática: chamada cross-origin direta (`localhost:3000` → `localhost:8080`)

Conclusão obtida **lendo o código** (não executada: nesta máquina não há PostgreSQL/Docker, então o app não sobe):

- `SecurityConfig` **não habilita CORS no Spring Security** (não há `.cors(...)`), e o
  `JwtAuthenticationFilter` executa **antes** do tratamento de CORS do Spring MVC.
- O filtro ignora apenas `PUBLIC_PATHS` = `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`.
  Em qualquer outra rota, requisição sem `Authorization` → **401**.
- O **preflight** (`OPTIONS`, e o navegador **nunca** envia credenciais no preflight) das rotas
  `/api/auth/me`, `/api/users` e `/api/users/{id}/status` cai no filtro → **401 sem headers de CORS** →
  o navegador bloqueia a requisição real.
- Logo: cross-origin direto **funciona** em `login`/`refresh`/`logout` (rotas públicas, cujo preflight o CORS
  do MVC responde) e **falha** nas rotas autenticadas.

**Alternativa (exige alteração de código, NÃO feita nesta parte):** habilitar CORS no Spring Security com
`.cors(Customizer.withDefaults())` em `SecurityConfig` — decisão pendente (ver `README.md` → "Pontos A VALIDAR").

### Observação sobre a cookie em cenário cross-origin

`SameSite=Lax` bloqueia cookies em requisições **cross-site**, não cross-origin: `localhost:3000` →
`localhost:8080` contam como **mesmo site** (`localhost`), então a cookie tende a ser enviada normalmente.
Cenários com hosts diferentes (ex.: frontend em `192.168.x.x` e API em `localhost`) seriam **cross-site** e a
cookie **não** seguiria, exigindo `SameSite=None` + `Secure=true` + HTTPS. Como `app.cookie.same-site` está
**fixo em `lax`** no `application.yml` (sem variável de ambiente), esse cenário exigiria mudança de
configuração/código — **decisão pendente do time**.

## Endpoints

### POST /api/auth/login

**Descrição:** autentica por e-mail e senha e inicia uma sessão (access token + cookie de refresh).
**Auth:** pública.
**Request:**

```json
{ "email": "admin@clinica.local", "password": "***" }
```

**Response 200:**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresInSeconds": 900,
  "user": {
    "id": "3f1c1c1e-1111-2222-3333-444455556666",
    "name": "Administrador",
    "email": "admin@clinica.local",
    "role": "ADMIN",
    "active": true,
    "createdAt": "2026-01-01T12:00:00Z"
  }
}
```

**Header:** `Set-Cookie: refresh_token=<opaco>; Path=/api; HttpOnly; SameSite=Lax; Max-Age=2592000`

**Erros:**
- `400` — body inválido/ausente ou `email`/`password` em branco.
- `401` — `Autenticación fallida` (e-mail inexistente ou senha incorreta).
- `401` — `Usuario inactivo` (usuário existente com `active = false`).

**Auditoria:** grava `LOGIN` no sucesso e `LOGIN_FAILED` quando a senha não confere (payload só com o
e-mail, nunca a senha). Não grava log quando o e-mail não existe nem quando o usuário está inativo.

### POST /api/auth/refresh

**Descrição:** rotaciona a sessão. Valida a cookie `refresh_token`, **revoga** o token usado e emite um
novo access token + nova cookie.

**Auth:** pública (a credencial é a própria cookie `HttpOnly`).
**Request:** sem body — a cookie é obrigatória.
**Response 200:** mesmo formato do login.
**Erros:**
- `401` — `Sesión expirada` (cookie ausente/em branco, token desconhecido, revogado ou expirado).
- `401` — `Usuario inactivo` (usuário desativado após emitir o token).

**Auditoria:** grava `REFRESH`.

### POST /api/auth/logout

**Descrição:** revoga a sessão atual (o refresh token da cookie) e limpa a cookie.
**Auth:** pública.
**Request:** sem body.
**Response 204:** sem corpo. Header `Set-Cookie: refresh_token=; Path=/api; HttpOnly; Max-Age=0`.
**Erros:** nenhum previsto — sem cookie (ou cookie desconhecida) ainda responde **204** (idempotente).
**Auditoria:** **não** registra ação de logout (o `AuditAction.LOGOUT` existe no enum, mas não é usado).

### GET /api/auth/me

**Descrição:** retorna o usuário autenticado da sessão corrente.
**Auth:** `Authorization: Bearer <accessToken>` (qualquer role autenticada).
**Request:** sem body.
**Response 200:** objeto `user` (mesmo formato de `user` do login).
**Erros:**
- `401` — header `Authorization` ausente/malformado ou token inválido/expirado.
- `404` — `Usuario no encontrado` (identificador do token não existe mais na base).

**Auditoria:** não registra.

### POST /api/users

**Descrição:** cria um usuário do sistema.
**Auth:** `Authorization: Bearer <accessToken>` + role **ADMIN** (`@PreAuthorize("hasRole('ADMIN')")`).
**Request:**

```json
{
  "name": "Maria Souza",
  "email": "maria@clinica.local",
  "password": "minha-senha-123",
  "role": "FISIOTERAPEUTA"
}
```

Regras de validação: `name` obrigatório e ≤ 120 caracteres; `email` obrigatório e válido; `password`
obrigatório entre **8 e 72** caracteres; `role` obrigatório (`ADMIN`, `ATENDENTE` ou `FISIOTERAPEUTA` —
comparação **case-insensitive**, com `trim`). O usuário é criado com `active = true`.

**Response 201:**

```json
{
  "id": "9a8b7c6d-1111-2222-3333-444455556666",
  "name": "Maria Souza",
  "email": "maria@clinica.local",
  "role": "FISIOTERAPEUTA",
  "active": true,
  "createdAt": "2026-01-01T12:00:00Z"
}
```

**Erros:**
- `400` — falha de validação (`campo: mensagem`), body ausente/ilegível ou `role` inexistente
  (`Perfil inválido: X`).
- `401` — sem token válido.
- `403` — `No tiene permisos para esta acción` (role diferente de ADMIN).
- `409` — `Ya existe un usuario con ese email`.

**Auditoria:** grava `USER_CREATED` (payload com e-mail, role e `active`; **sem senha**).

### PATCH /api/users/{id}/status

**Descrição:** ativa ou desativa um usuário.
**Auth:** `Authorization: Bearer <accessToken>` + role **ADMIN**.
**Path param:** `id` (UUID do usuário).
**Request:**

```json
{ "active": false }
```

**Response 200:** objeto `user` atualizado (mesmo formato do `POST /api/users`).
**Erros:**
- `400` — `active` ausente/`null` ou `id` que não é um UUID válido.
- `401` — sem token válido.
- `403` — role diferente de ADMIN.
- `404` — `Usuario no encontrado`.

**Auditoria:** grava `USER_ACTIVATED` ou `USER_DEACTIVATED` com `payload_before`/`payload_after`. O registro
é gravado mesmo quando o status enviado já é o atual (nesse caso o domínio não altera `updated_at`).

> Não existem outros endpoints. Em particular **não há** `GET /api/users`, `GET /api/users/{id}`,
> alteração de dados do usuário, troca/redefinição de senha nem endpoint para consultar a auditoria.

## Códigos de erro padronizados

Existem **dois formatos** de erro — inconsistência conhecida do código atual, registrada aqui como
**débito técnico** (correção prevista para a Parte 5):

**1) Erros de negócio/validação (Spring MVC) — `ApiError` via `GlobalExceptionHandler`:**

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Solicitud inválida",
  "message": "email: Formato de email inválido",
  "path": "/api/users"
}
```

**2) Erros emitidos pelos filtros/handlers de segurança — `HttpErrorWriter` (sem `timestamp`/`message`):**

```json
{ "status": 401, "error": "No autenticado", "path": "/api/auth/me" }
```

| Status | `error` | Quando acontece |
|--------|---------|-----------------|
| `400` | `Solicitud inválida` | Validação de campos (`campo: mensagem`), body ilegível, `role` inválido, `active` ausente, `id` não-UUID |
| `401` | `No autenticado` | `Authorization` ausente/inválido/expirado (formato do `HttpErrorWriter`) |
| `401` | `Autenticación fallida` | Credenciais inválidas no login |
| `401` | `Sesión expirada` | Refresh token ausente, desconhecido, revogado ou expirado |
| `401` | `Usuario inactivo` | Usuário desativado |
| `403` | `Acceso denegado` / `No tiene permisos para esta acción` | Role sem permissão (`@PreAuthorize`) |
| `404` | `Recurso no encontrado` | Usuário inexistente (`Usuario no encontrado`) ou rota desconhecida (`La ruta no existe`) |
| `409` | `Conflicto` | `Ya existe un usuario con ese email` |
| `500` | `Error interno` | Exceção não tratada (stack trace **não** é exposto; é logado no servidor) |

## Observações

- **Nota:** o endpoint canônico é `/api/auth/me`. O frontend deve consumir este caminho.
- **Nota:** este documento cobre **apenas o backend**; o frontend está em repositório separado.
- **CORS:** a configuração verificada, a análise de *preflight* em chamadas cross-origin e a recomendação de
  **proxy same-origin** estão na seção
  ["CORS e integração com o frontend"](#cors-e-integração-com-o-frontend-repositório-separado).
- **Datas:** `createdAt` é um `Instant` serializado em ISO-8601 UTC (`2026-01-01T12:00:00Z`). O Jackson está
  configurado com `time-zone: America/Sao_Paulo` e `locale: pt-BR`.
- **Mensagens:** todas as mensagens de erro/sucesso de validação estão em **espanhol** (idioma do código).
- **Rate limiting / bloqueio por tentativas:** não implementado. `LOGIN_FAILED` é apenas auditado.
- **Sem `GET /api/auth/refresh`:** o refresh é **POST** (o token vai na cookie, não na URL).
