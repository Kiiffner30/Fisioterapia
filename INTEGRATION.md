# Integração Backend ↔ Frontend — FisioVida

> **Contrato oficial da API** para o time do frontend. Todo o conteúdo foi **extraído do código-fonte** do
> backend (`src/main/java`, `src/main/resources`, `pom.xml`) — nada foi presumido. Quando uma informação não
> existe no código, o valor está marcado como **NÃO IMPLEMENTADO** ou **NÃO ENCONTRADO**.
>
> Commit de referência desta extração: `225fc81e9950c505b5702111f89dc323ee88381c`.
> Documentos relacionados: [`API.md`](API.md) (endpoints e códigos), [`README.md`](README.md) (setup),
> [`ARCHITECTURE.md`](ARCHITECTURE.md) (camadas).

## 1. Informações do servidor

| Item | Valor | Origem no código |
|---|---|---|
| Porta padrão | **8080** | `application.yml` → `server.port: ${SERVER_PORT:8080}` |
| Context path | **NÃO EXISTE** (nenhum `server.servlet.context-path`) | — |
| Prefixo das rotas | **`/api`** (fixado em `@RequestMapping` dos controllers) | `AuthController`, `UserController` |
| Base URL (dev) | **`http://localhost:8080/api`** | — |
| Swagger UI | **NÃO ENCONTRADO** (não há `springdoc-openapi` no `pom.xml`) | `pom.xml` |
| OpenAPI JSON (`/v3/api-docs`) | **NÃO ENCONTRADO** | `pom.xml` |
| Versionamento de API (`/v1`, `/v2`) | **NÃO IMPLEMENTADO** | — |
| Formato | JSON / UTF-8; datas `Instant` em ISO-8601 **UTC** (`2026-01-01T12:00:00Z`) | `application.yml` (`jackson.time-zone: America/Sao_Paulo`) |
| Segurança | Stateless (JWT), `securityMatcher("/api/**")`, CSRF **desabilitado** | `SecurityConfig` |
| Profile padrão | `dev` (`SPRING_PROFILES_ACTIVE`) | `application.yml` |

Observações de configuração:

- O arquivo `.env` da raiz é lido **somente pelo Docker Compose** — o Spring Boot **não** lê `.env`. Ou seja, o
  frontend deve configurar a **própria** base URL (ex.: `.env.local` do repositório do frontend).
- Todas as mensagens de erro/validação da API estão em **espanhol** (idioma do código-fonte).

## 2. CORS

Fonte: `WebConfig` (`WebMvcConfigurer.addCorsMappings`) + `CorsProperties` (`@ConfigurationProperties("app.cors")`).
**Não existe `@CrossOrigin` em nenhum controller** e **nenhuma origem está hardcoded**.

| Item | Valor | Origem |
|---|---|---|
| Paths com CORS | `/api/**` | `registry.addMapping("/api/**")` |
| Origens permitidas | `CORS_ALLOWED_ORIGINS` (lista separada por vírgula) | env → `app.cors.allowed-origins` |
| Default em `dev` | `http://localhost:3000` | `application-dev.yml` |
| Em `prod` | **obrigatória, sem default** (sem a variável a app não sobe) | `application-prod.yml` |
| Métodos permitidos | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | `allowedMethods(...)` |
| Headers permitidos | `*` (inclui `Authorization` e `Content-Type`) | `allowedHeaders("*")` |
| `allowCredentials` | **`true`** | necessário para o cookie de refresh |
| Cache do preflight | `maxAge(3600)` → 1 hora | `.maxAge(3600)` |

**Precisa de `credentials: 'include'` no `fetch`?** **SIM** — em toda chamada que precise enviar/receber o
cookie `refresh_token` (login, refresh, logout). Sem isso o navegador **descarta** o `Set-Cookie`.

> ⚠️ **Nunca use `*` em `CORS_ALLOWED_ORIGINS`**: com `allowCredentials: true` o navegador **rejeita**
> `Access-Control-Allow-Origin: *`. Liste as origens exatas.

### ⚠️ LIMITAÇÃO CRÍTICA — cross-origin direto

`SecurityConfig` **não habilita CORS no Spring Security** (não existe `.cors(...)`) e o `JwtAuthenticationFilter`
executa **antes** do CORS do Spring MVC. Consequência prática (origem `http://localhost:3000` → API `:8080`):

- ✅ **Funciona** cross-origin: `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
  (rotas públicas: o filtro é ignorado e o preflight é respondido pelo CORS do MVC).
- ❌ **Falha** cross-origin: `GET /api/auth/me`, `POST /api/users`, `PATCH /api/users/{id}/status`.
  O **preflight** (`OPTIONS`, que o navegador envia **sem** `Authorization`) cai no filtro → **401 sem headers
  de CORS** → o navegador bloqueia a requisição real.

**Caminho recomendado (sem alterar o backend): proxy same-origin.** Exponha `/api` no host do frontend e faça
proxy para `http://localhost:8080`. Exemplo em Next.js (`next.config.ts`, stack do frontend):

```ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: "http://localhost:8080/api/:path*" }];
  },
};

export default nextConfig;
```

Com o proxy **não há requisição cross-origin** (CORS e preflight deixam de ser problema) e o cookie
`refresh_token` (`Path=/api`) trafega same-origin — assim a base URL do frontend pode ser **relativa**
(`/api/...`), o que também dispensa `NEXT_PUBLIC_API_URL` apontando para outra porta.

**Alternativa que exige alteração no backend (decisão pendente):** adicionar `.cors(Customizer.withDefaults())`
em `SecurityConfig`. **Não faça isso agora** — é um ponto *A VALIDAR* registrado no `README.md`.

## 3. Autenticação

Modelo: **access token (JWT) no body + refresh token opaco em cookie HttpOnly**. Não há sessão no servidor
(`SessionCreationPolicy.STATELESS`) e o CSRF está desabilitado.

### 3.1 `POST /api/auth/login`

**Rota:** `POST /api/auth/login` — **pública**.

| Campo | Tipo | Obrigatório | Validação (`LoginRequest`) |
|---|---|---|---|
| `email` | string | ✅ | `@NotBlank` + `@Email` → **400** se vazio/inválido |
| `password` | string | ✅ | `@NotBlank` (sem regra de tamanho no login) |

**Response 200:**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9....",
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

**Headers da resposta:**
`Set-Cookie: refresh_token=<opaco>; Path=/api; HttpOnly; SameSite=Lax; Max-Age=2592000`
(com o atributo `Secure` presente apenas quando `COOKIE_SECURE=true`).

⚠️ **O refresh token nunca aparece no body.** `TokenResponse` contém **apenas** `accessToken`,
`expiresInSeconds` e `user`.

### 3.2 Cookie do refresh (`AuthController` + `application.yml`)

| Atributo | Valor | Observação |
|---|---|---|
| Nome | `refresh_token` | `app.cookie.name` — fixo no código |
| `HttpOnly` | **`true`** (hardcoded) | invisível para JavaScript |
| `Path` | `/api` | fixo — só é enviada em rotas `/api/**` |
| `SameSite` | `Lax` | fixo em `application.yml` (**não** existe variável de ambiente) |
| `Secure` | `${COOKIE_SECURE:false}` | `true` obrigatório apenas quando a API for servida por HTTPS |
| `Max-Age` | `JWT_REFRESH_EXPIRATION` em **segundos** (`P30D` → `2592000`) | |

### 3.3 `POST /api/auth/refresh`

**Rota:** `POST /api/auth/refresh` — **pública**, **sem body**. A credencial é a própria cookie `HttpOnly`.

**Response 200:** **exatamente o mesmo formato do login** → novo `accessToken`, novo `expiresInSeconds`, novo
`user` e **nova cookie** (`Set-Cookie`).

**Rotação:** ✅ **SIM.** Cada refresh **revoga o token usado** e emite um novo (se o token antigo for
reutilizado → **401** `Sesión expirada`).

**Erros:** `401` `Sesión expirada` (cookie ausente/vazia, token desconhecido, revogado ou expirado);
`401` `Usuario inactivo` (usuário desativado depois de emitir o token).

### 3.4 `POST /api/auth/logout`

**Rota:** `POST /api/auth/logout` — **pública**, **sem body** e **não exige o access token**.

**O que faz:** revoga o refresh token da cookie, grava a auditoria `LOGOUT` e responde **204 No Content** com
`Set-Cookie: refresh_token=; Path=/api; HttpOnly; Max-Age=0` (limpa a cookie).

É **idempotente**: sem cookie (ou com cookie desconhecida) continua respondendo **204**.

⚠️ **Não existe blacklist:** o **access token continua válido até expirar**, mesmo depois do logout. O frontend
deve **descartar o access token da memória imediatamente** ao deslogar.

### 3.5 `GET /api/auth/me`

**Rota canônica:** `GET /api/auth/me` — é o único caminho para obter o usuário da sessão. **Não existe**
`/api/auth/session` nem `/api/auth/profile`.

**Auth:** `Authorization: Bearer <accessToken>` — **qualquer** role autenticada.

**Response 200:** o objeto `user` **direto** (sem envelope — diferente do login):

```json
{
  "id": "3f1c1c1e-1111-2222-3333-444455556666",
  "name": "Administrador",
  "email": "admin@clinica.local",
  "role": "ADMIN",
  "active": true,
  "createdAt": "2026-01-01T12:00:00Z"
}
```

**Erros:** `401` (token ausente/inválido/expirado); `404` `Usuario no encontrado` (o id do token não existe mais).

### 3.6 Detalhes do token

| Item | Valor |
|---|---|
| Formato do access token | JWT **HS256** — `iss: "fisioterapia"`, `sub`: UUID do usuário, claims `email` e `role` |
| Como o frontend envia | Header **`Authorization: Bearer <accessToken>`** |
| Expiração do access token | `JWT_ACCESS_EXPIRATION` (default **`PT15M` = 15 minutos** → `expiresInSeconds: 900`) |
| Expiração do refresh token | `JWT_REFRESH_EXPIRATION` (default **`P30D` = 30 dias**) |
| Refresh token no body? | **NÃO** — apenas cookie `HttpOnly` |
| Refresh token é JWT? | **NÃO** — é opaco (32 bytes aleatórios em Base64URL); o banco guarda só o **SHA-256** |
| Renovação automática (*sliding session*) | **NÃO IMPLEMENTADO** |
| Formato das durações | Sempre **ISO-8601 Duration** (`PT15M`, `P30D`) — **não** são segundos |

## 4. Roles e permissões

O enum `RoleName` (`domain/user/RoleName.java`) define **exatamente 3 perfis**. Na criação de usuário a
comparação é **case-insensitive** e com `trim`.

| Role | O que pode fazer |
|---|---|
| `ADMIN` | Tudo o que um usuário autenticado faz **+** `POST /api/users` **+** `PATCH /api/users/{id}/status` (são as **únicas** rotas anotadas com `@PreAuthorize("hasRole('ADMIN')")`) |
| `ATENDENTE` | Apenas rotas autenticadas comuns → hoje **`GET /api/auth/me`** (200) e **403** nas rotas de ADMIN. Sem casos de uso próprios |
| `FISIOTERAPEUTA` | **Idêntico** a `ATENDENTE` (comportamento confirmado em `AuthenticationFlowIT`) |

### Matriz real de autorização (estado atual do código)

| Rota | ADMIN | ATENDENTE | FISIOTERAPEUTA | Sem token |
|---|---|---|---|---|
| `POST /api/auth/login` | ✅ (pública) | ✅ | ✅ | ✅ |
| `POST /api/auth/refresh` | ✅ (pública, cookie) | ✅ | ✅ | ✅ |
| `POST /api/auth/logout` | ✅ (pública, cookie) | ✅ | ✅ | ✅ |
| `GET /api/auth/me` | ✅ 200 | ✅ 200 | ✅ 200 | ❌ 401 |
| `POST /api/users` | ✅ 201 | ❌ 403 | ❌ 403 | ❌ 401 |
| `PATCH /api/users/{id}/status` | ✅ 200 | ❌ 403 | ❌ 403 | ❌ 401 |

**Como o frontend identifica a role do usuário:** pelo campo **`user.role`** (string em maiúsculas, exatamente
`ADMIN`, `ATENDENTE` ou `FISIOTERAPEUTA`) devolvido por `POST /api/auth/login` e por `GET /api/auth/me`.
A claim `role` também existe dentro do JWT, mas **não é necessário decodificar o token** para isso.

**Não existe** endpoint que liste as roles disponíveis — para montar um `<select>` no formulário de criação de
usuário, use as 3 constantes hardcoded (exatamente como o backend faz).

> A autorização é **sempre** validada no backend. Esconder botões na UI é UX, não segurança.

## 5. Endpoints (lista completa)

O backend expõe **exatamente 6 endpoints**: 4 em `AuthController` (`/api/auth`) e 2 em `UserController`
(`/api/users`). **Não há outros** — nem `GET /api/users`, nem qualquer rota de pacientes/agenda/consultas.

| Método | Rota completa | Auth | Role | Request body | Sucesso | Erros possíveis |
|---|---|---|---|---|---|---|
| `POST` | `/api/auth/login` | pública | — | `{ email, password }` | **200** `{ accessToken, expiresInSeconds, user }` + cookie | 400, 401 |
| `POST` | `/api/auth/refresh` | pública (cookie) | — | — (sem body) | **200** `{ accessToken, expiresInSeconds, user }` + cookie nova | 401 |
| `POST` | `/api/auth/logout` | pública (cookie) | — | — (sem body) | **204** (sem corpo) + cookie limpa | — (sempre 204) |
| `GET` | `/api/auth/me` | `Bearer` | qualquer | — | **200** `UserResponse` | 401, 404 |
| `POST` | `/api/users` | `Bearer` | **ADMIN** | `{ name, email, password, role }` | **201** `UserResponse` | 400, 401, 403, 409 |
| `PATCH` | `/api/users/{id}/status` | `Bearer` | **ADMIN** | `{ active }` | **200** `UserResponse` | 400, 401, 403, 404 |

### `UserResponse` — formato comum (`/api/auth/me`, `POST /api/users`, `PATCH .../status`)

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

> `passwordHash` **nunca** é exposto. O campo `updatedAt` existe no domínio mas **não** faz parte da resposta.

### `POST /api/users` — validações (`CreateUserRequest`)

| Campo | Obrigatório | Regra | Mensagem de erro (400) |
|---|---|---|---|
| `name` | ✅ | `@NotBlank`, máx. **120** caracteres | `name: El nombre es obligatorio` / `name: El nombre no puede superar 120 caracteres` |
| `email` | ✅ | `@NotBlank` + `@Email` | `email: El email es obligatorio` / `email: Formato de email inválido` |
| `password` | ✅ | `@NotBlank`, **8 a 72** caracteres | `password: La contraseña debe tener entre 8 y 72 caracteres` |
| `role` | ✅ | `@NotBlank`; aceitos: `ADMIN`, `ATENDENTE`, `FISIOTERAPEUTA` (case-insensitive, `trim`) | valor inválido → `Perfil inválido: X` |

- O usuário é **sempre** criado com `active: true`.
- **Todas** as falhas de validação vêm juntas no mesmo `message`, separadas por vírgula: `"campo: msg, campo: msg"`.

### `PATCH /api/users/{id}/status`

- Body: `{ "active": false }` — `active` é **obrigatório** (`@NotNull`): ausente/`null` → **400**.
- `{id}` deve ser um **UUID** válido: valor não-UUID → **400** (`Solicitud inválida`).
- Enviar o mesmo status atual **não** gera erro: responde **200** (e ainda grava auditoria).

### Auditoria gravada (não há endpoint para consultá-la)

`LOGIN`, `LOGIN_FAILED`, `REFRESH`, `LOGOUT`, `USER_CREATED`, `USER_ACTIVATED`, `USER_DEACTIVATED` — sempre
**sem** senhas e **sem** tokens no payload (`audit_logs`).

## 6. Formato dos erros

**Há um único formato** (`ApiError`), emitido tanto pelo `GlobalExceptionHandler` (erros de negócio/validação)
quanto pelo `HttpErrorWriter` (erros dos filtros e handlers do Spring Security). O débito antigo de "dois
formatos de erro" foi **resolvido** — o mesmo corpo é usado nos dois caminhos.

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 401,
  "error": "No autenticado",
  "message": "No autenticado",
  "path": "/api/auth/me"
}
```

| Campo | Tipo | Conteúdo |
|---|---|---|
| `timestamp` | string ISO-8601 UTC | momento do erro |
| `status` | number | código HTTP |
| `error` | string | rótulo curto (ver tabela abaixo) |
| `message` | string | detalhe; quando não há detalhe, **repete** o `error` |
| `path` | string | `requestURI` (ex.: `/api/auth/me`); pode vir `""` em erros escritos pelo `HttpErrorWriter` quando a URI é nula |

### Exemplos por status

**400 — validação de campos** (ex.: e-mail inválido no login):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 400, "error": "Solicitud inválida",
  "message": "email: Formato de email inválido", "path": "/api/auth/login" }
```

**400 — body ausente/ilegível, JSON malformado ou tipo errado** (não é erro de campo):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 400, "error": "Solicitud inválida",
  "message": "La solicitud no pudo interpretarse", "path": "/api/auth/login" }
```

**401 — sem token / token inválido ou expirado** (emitido pelo `JwtAuthenticationFilter`):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 401, "error": "No autenticado",
  "message": "No autenticado", "path": "/api/auth/me" }
```

**401 — credenciais inválidas no login** (e-mail inexistente ou senha errada):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 401, "error": "Autenticación fallida",
  "message": "Credenciales inválidas", "path": "/api/auth/login" }
```

**401 — refresh inválido/expirado**:

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 401, "error": "Sesión expirada",
  "message": "Sesión inválida o expirada", "path": "/api/auth/refresh" }
```

**401 — usuário inativo** (`active = false`):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 401, "error": "Usuario inactivo",
  "message": "El usuario está inactivo", "path": "/api/auth/login" }
```

**403 — sem permissão** (role diferente de ADMIN; `ApiError` para negação em `@PreAuthorize` e na filter chain):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 403, "error": "Acceso denegado",
  "message": "Acceso denegado", "path": "/api/users" }
```

**404 — recurso não encontrado** (id inexistente):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 404, "error": "Recurso no encontrado",
  "message": "Usuario no encontrado", "path": "/api/auth/me" }
```

**404 — rota inexistente:** mesmo corpo, com `"message": "La ruta no existe"`.

**409 — e-mail já cadastrado**:

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 409, "error": "Conflicto",
  "message": "Ya existe un usuario con ese email", "path": "/api/users" }
```

**500 — erro interno** (stack trace **nunca** é exposto; é logado no servidor):

```json
{ "timestamp": "2026-01-01T12:00:00Z", "status": 500, "error": "Error interno",
  "message": "Ocurrió un error inesperado", "path": "/api/users" }
```

### Tabela-resumo de erros

| Status | `error` | `message` | Quando acontece |
|---|---|---|---|
| `400` | `Solicitud inválida` | `campo: mensagem` · `La solicitud no pudo interpretarse` · `Perfil inválido: X` | validação, body ilegível, `role` inválido, `active` ausente, `id` não-UUID |
| `401` | `No autenticado` | `No autenticado` | `Authorization` ausente/malformado/token inválido ou expirado |
| `401` | `Autenticación fallida` | `Credenciales inválidas` | e-mail inexistente ou senha incorreta |
| `401` | `Sesión expirada` | `Sesión inválida o expirada` | refresh ausente, desconhecido, revogado ou expirado |
| `401` | `Usuario inactivo` | `El usuario está inactivo` | usuário existente com `active = false` |
| `403` | `Acceso denegado` | `Acceso denegado` | role sem permissão (`@PreAuthorize`); `GlobalExceptionHandler` trata negação em métodos e `accessDeniedHandler` trata negação na filter chain |
| `404` | `Recurso no encontrado` | `Usuario no encontrado` ou `La ruta no existe` | id inexistente / rota desconhecida |
| `409` | `Conflicto` | `Ya existe un usuario con ese email` | e-mail duplicado |
| `500` | `Error interno` | `Ocurrió un error inesperado` | exceção não tratada |

> ℹ️ Existe também a mensagem `La contraseña debe tener al menos 8 caracteres` (`CreateUserUseCase`), mas ela é
> **defesa em profundidade**: o DTO (`CreateUserRequest`) já rejeita senhas com menos de 8 caracteres antes de o
> caso de uso ser executado — na prática o frontend não a verá via HTTP.

> ℹ️ O **403 não tem corpo alternativo**: negações de `@PreAuthorize` são tratadas pelo `GlobalExceptionHandler`; negações
> na filter chain são tratadas pelo `accessDeniedHandler`. Ambos retornam o mesmo `ApiError`.

## 7. Fluxo completo de login (passo a passo real)

1. **Login:** `POST /api/auth/login` com `{ email, password }` e **`credentials: 'include'`**.
2. O backend responde **200** com `{ accessToken, expiresInSeconds, user }` **e** o header
   `Set-Cookie: refresh_token=...; HttpOnly; Path=/api; SameSite=Lax`. O `refresh_token` **não** aparece no body.
3. **Guarde o `accessToken` somente em memória** (estado do app / React Context / store em memória). **Não** use
   `localStorage` — o refresh token já é `HttpOnly`; expor o access token no `localStorage` amplia o risco de XSS.
   Guarde também o `user` (inclusive `user.role`) para montar menu/permissões da UI.
4. **Chamadas protegidas:** envie **`Authorization: Bearer <accessToken>`** (e `credentials: 'include'`).
5. **Quando o access token expirar**, a API responde **401** `No autenticado` → chame `POST /api/auth/refresh`
   **sem body** e com `credentials: 'include'` (o navegador envia a cookie automaticamente).
6. **Refresh OK (200):** você recebe um **novo `accessToken`** (+ `expiresInSeconds` e `user`) e uma **nova
   cookie**; o refresh token anterior foi **revogado** (rotação). Substitua o token em memória e **repita a
   requisição original**.
7. **Refresh falhou (401 `Sesión expirada`):** a sessão terminou → limpe o estado e redirecione para o login.
   ⚠️ Se **duas** chamadas dispararem refresh em paralelo, a segunda usará um token **já revogado** e falhará —
   implemente uma **fila/mutex** (uma única `Promise` de refresh compartilhada) no seu interceptor de `fetch`.
8. **Logout:** `POST /api/auth/logout` (com `credentials: 'include'`) → **204** e cookie limpa. Descarte o
   `accessToken` da memória — ele **continua válido até expirar** (não há blacklist no servidor).

**Temporização sugerida:** renove **proativamente** ~60 s antes de `expiresInSeconds` (evita 401 no meio de um
fluxo) e trate o 401 como *fallback* reativo no interceptor.

## 8. Exemplos de código (`fetch` + TypeScript)

Base URL recomendada: **relativa** (`/api`), via proxy same-origin. Se você chamar a porta 8080 diretamente,
veja antes a **limitação cross-origin** da seção 2 (só login/refresh/logout funcionam direto).

```ts
const API_BASE = "/api"; // via proxy same-origin (alternativa: "http://localhost:8080/api")
```

### Exemplo A — Login (access token guardado em memória)

```ts
export type Role = "ADMIN" | "ATENDENTE" | "FISIOTERAPEUTA";

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string; // ISO-8601 UTC
}

export interface TokenResponse {
  accessToken: string;
  expiresInSeconds: number; // 900 por default (PT15M)
  user: UserResponse;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // ⚠️ obrigatório: sem isso o cookie refresh_token é descartado
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.message); // 401: "Credenciales inválidas" / "El usuario está inactivo"
  }

  return response.json(); // mantenha accessToken SOMENTE em memória
}
```

### Exemplo B — Chamada autenticada (`GET /api/auth/me`)

```ts
export async function me(accessToken: string): Promise<UserResponse> {
  const response = await fetch(`${API_BASE}/auth/me`, {
    method: "GET",
    headers: { Authorization: `Bearer ${accessToken}` },
    credentials: "include",
  });

  if (response.status === 401) {
    throw new Error("NAO_AUTENTICADO"); // tente POST /api/auth/refresh (uma vez só, com mutex)
  }
  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.message); // 404: "Usuario no encontrado"
  }

  // Resposta é o objeto do usuário DIRETO (não vem dentro de { user: ... })
  return response.json();
}
```

### Exemplo C — Refresh (com mutex) e logout

```ts
let refreshPromise: Promise<string> | null = null;

/** Renova o access token. A cookie refresh_token vai automaticamente. */
export function refreshAccessToken(): Promise<string> {
  // Uma única requisição compartilhada: evita refresh concorrente com token já revogado (rotação)
  refreshPromise ??= (async () => {
    try {
      const response = await fetch(`${API_BASE}/auth/refresh`, {
        method: "POST",
        credentials: "include",
      });
      if (!response.ok) {
        // 401 "Sesión inválida o expirada" -> sessão terminou
        throw new Error("SESSAO_EXPIRADA");
      }
      const data: TokenResponse = await response.json(); // mesmo formato do login
      return data.accessToken;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/auth/logout`, { method: "POST", credentials: "include" }); // 204, sem corpo
}
```

### Exemplo D — Criar usuário (ADMIN) — atenção ao **201**

```ts
export async function createUser(
  accessToken: string,
  body: { name: string; email: string; password: string; role: Role },
): Promise<UserResponse> {
  const response = await fetch(`${API_BASE}/users`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    credentials: "include",
    body: JSON.stringify(body),
  });

  if (response.status === 201) return response.json(); // sucesso é 201 Created (não 200)

  const error: ApiError = await response.json();
  // 409: "Ya existe un usuario con ese email" | 403: "Acceso denegado"
  // 400: "name: El nombre es obligatorio, email: Formato de email inválido, ..."
  throw new Error(`${response.status}: ${error.message}`);
}

export async function changeUserStatus(
  accessToken: string,
  userId: string,
  active: boolean,
): Promise<UserResponse> {
  const response = await fetch(`${API_BASE}/users/${userId}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    credentials: "include",
    body: JSON.stringify({ active }), // 200 OK com o usuário atualizado
  });

  if (!response.ok) {
    const error: ApiError = await response.json(); // 400 | 403 | 404
    throw new Error(`${response.status}: ${error.message}`);
  }
  return response.json();
}
```

## 9. Observações e cuidados

1. **`SameSite=Lax` em `localhost` (cross-origin) funciona.** `SameSite` compara **site**, não origem:
   `localhost:3000` → `localhost:8080` são o **mesmo site** (`localhost`), então a cookie é enviada. Cenários
   realmente **cross-site** (ex.: frontend em `192.168.x.x` → API em `localhost`) **não** funcionariam: exigiriam
   `SameSite=None` + `Secure=true` + HTTPS. Como `app.cookie.same-site` está **fixo em `lax`** no
   `application.yml` (sem variável de ambiente), mudar esse cenário exige alteração no backend — **decisão
   pendente do time**.
2. **CORS já permite credenciais** (`allowCredentials: true`) — portanto **`credentials: 'include'` é
   obrigatório** e a origem precisa estar **exatamente** listada em `CORS_ALLOWED_ORIGINS`.
3. **Cookie em `Path=/api`:** ela só é enviada em chamadas cujo caminho começa com `/api`. Requisições a outros
   paths não levam a cookie (esperado).
4. **Proxy same-origin é o caminho mais seguro** (seção 2): contorna a limitação de preflight e funciona com
   `SameSite=Lax` sem exigir HTTPS.
5. **O logout não revoga o access token.** Requisições com o token antigo seguem válidas até o `exp`
   (~15 min). Descarte o token em memória imediatamente.
6. **Mensagens em espanhol.** Não faça *matching* por texto para decidir lógica de UI: use o **`status` HTTP**
   e, quando precisar distinguir, o campo **`error`** (ex.: `No autenticado` × `Sesión expirada`).
7. **Status codes que confundem:** `POST /api/users` responde **201** (não 200) e `POST /api/auth/logout`
   responde **204** sem corpo.
8. **`GET /api/auth/me` devolve o usuário direto** (sem `{ user: ... }`), diferente do login que devolve
   `{ accessToken, expiresInSeconds, user }`.
9. **Sem rate limiting / lockout.** `LOGIN_FAILED` só é auditado — não há bloqueio por tentativas; trate
   mensagens de erro genéricas na UI sem sugerir "conta bloqueada".
10. **`expiresInSeconds`** reflete `jwt.access-expiration` (default `PT15M` → `900`). Não há endpoint de
    "server time": use o instante local com margem de segurança.
11. **Um único formato de erro** (seção 6) — não é preciso tratar dois formatos diferentes.
12. **Datas** vêm em **ISO-8601 UTC** (`2026-01-01T12:00:00Z`). O backend usa `America/Sao_Paulo` para o
    Jackson, mas `Instant` é sempre serializado em UTC — converta para o fuso do usuário no frontend.
13. **`createdAt` é o único campo de data exposto** (não há `updatedAt` nem `lastLoginAt` na resposta).
14. **`user.active`** já vem na resposta de login/`me`: um usuário desativado recebe **401**
    `Usuario inactivo` no login e no refresh; o frontend pode tratar isso como "conta desativada".

## 10. O que ainda **NÃO** está implementado

Consuma **apenas** o que está na seção 5. Tudo abaixo **não existe** no backend (sem controller, DTO, rota ou
tabela) — não adianta chamar esses caminhos:

| Recurso esperado | Status |
|---|---|
| **Pacientes** (`/api/patients`, lista, detalhe, dados clínicos) | **NÃO IMPLEMENTADO** |
| **Agenda** de atendimentos / horários de atendimento | **NÃO IMPLEMENTADO** |
| **Consultas / atendimentos** (criar, remarcar, cancelar) | **NÃO IMPLEMENTADO** |
| **Tipos de consulta** | **NÃO IMPLEMENTADO** |
| **Dados da clínica / configurações do sistema** | **NÃO IMPLEMENTADO** |
| **Listagem/consulta de usuários** (`GET /api/users`, `GET /api/users/{id}`) | **NÃO IMPLEMENTADO** |
| **Edição de usuário** (nome/e-mail) e **troca/redefinição de senha** | **NÃO IMPLEMENTADO** |
| **Recuperação de senha** ("esqueci minha senha") | **NÃO IMPLEMENTADO** |
| **Consulta da trilha de auditoria** (`GET /api/audit`) — os registros são gravados, mas **não há endpoint** | **NÃO IMPLEMENTADO** |
| **Logout de todas as sessões** ("sair de todos os dispositivos") | **NÃO IMPLEMENTADO** |
| **Refresh token no body** e **`GET /api/auth/refresh`** (o refresh é `POST` e usa cookie) | **NÃO IMPLEMENTADO** |
| **Swagger / OpenAPI / Swagger UI** (`/v3/api-docs`, `/swagger-ui`) | **NÃO ENCONTRADO** (sem `springdoc` no `pom.xml`) |
| **Versionamento da API** (`/v1`) | **NÃO IMPLEMENTADO** |
| **Endpoint que liste as roles/perfis** disponíveis | **NÃO IMPLEMENTADO** |
| **Rate limiting / lockout** por tentativas de login | **NÃO IMPLEMENTADO** |
| **Rota de sessão alternativa** (`/api/auth/session`, `/api/auth/profile`) | **NÃO EXISTE** (a canônica é `GET /api/auth/me`) |

### Débitos técnicos conhecidos que afetam o frontend

| Débito | Impacto no frontend | Contorno |
|---|---|---|
| `SecurityConfig` sem `.cors(...)` → preflight **401** nas rotas autenticadas | ❌ cross-origin direto não funciona em `/api/auth/me`, `/api/users`, `/api/users/{id}/status` | Use **proxy same-origin** (seção 2) |
| Access token sem *blacklist* | token segue válido após o logout até expirar (~15 min) | Descarte o token em memória no logout |
| Sem renovação automática (*sliding session*) | o access token expira em 15 min e a API responde 401 | Renove proativamente ~60 s antes do `exp` (seção 7) |
| Sem `GET /api/users` | a tela "Usuários" ainda não pode listar nada | Aguarde a próxima parte do backend |
| Mensagens de erro em espanhol | textos prontos para UI em pt-BR | Mapeie por `status`/`error` no frontend |
| Sem Swagger/OpenAPI | não há "Try it out"/geração automática de client | Use este documento + [`API.md`](API.md) |

---

*Documento gerado a partir do código do backend que vive na **raiz deste repositório** (o frontend está em
repositório separado). É o contrato oficial com o time do frontend: **se algo mudar no código, atualize este
arquivo**. Nenhuma linha de código Java, migration Flyway, `pom.xml` ou configuração de profile foi alterada
para produzi-lo.*
