# Backend

KTor REST API backend with Exposed ORM, backed by PostgreSQL.

- **Language**: Kotlin 2.4.0
- **Framework**: KTor 3.5.1
- **ORM**: Exposed 1.3.1 — imports live under `org.jetbrains.exposed.v1.*` (the post-1.0 package layout, not the older `org.jetbrains.exposed.sql.*` paths shown in older tutorials)
- **Database**: PostgreSQL 18
- **Build**: Maven, JDK 25

See the [root README](../../README.md) for the one-command `docker-compose up --build` quickstart and repo-wide concerns (environment variables, CI, dependency guardrails). Every `docker`/`docker-compose` command on this page works the same with `podman`/`podman-compose` — see the root README's [Container Runtime](../../README.md#container-runtime) section.

## Running

**In containers**, from the repo root: `docker-compose up --build postgres backend`.

**Locally, with hot reload:**

```bash
docker-compose up -d postgres    # from the repo root
cd apps/backend && mvn compile exec:java
```

`pnpm run dev:backend` from the root is a shortcut for the same thing. No env vars needed — `DB_URL` defaults to `localhost:5432`.

This runs Ktor's `EngineMain` in development mode. Port, host, module and database config live in `src/main/resources/application.yaml` — which must be `.yaml`, not `.yml` (see Troubleshooting).

Edits take effect on the next request once recompiled, with no server restart:

```bash
mvn compile
# or, automatically: find src -name '*.kt' | entr -r mvn -q compile
```

Only route and module logic reloads cleanly. Top-level `val`s reinitialize on every reload, and changes to `main()` or to dependencies need a full restart.

**Locally, packaged** (closest to how Docker runs it):

```bash
cd apps/backend
mvn clean package -DskipTests    # requires JDK 25
java -jar target/backend.jar
```

## Testing

```bash
mvn test
```

Repository tests run against a real, ephemeral PostgreSQL instance via [Testcontainers](https://testcontainers.com/) — no mocked database, no shared test DB to pollute. `test/kotlin/testsupport/PostgresRepositoryTest.kt` starts one `postgres:18-alpine` container per JVM run (the "singleton container" pattern — cheaper than restarting per test class), runs the real Flyway migrations against it, and hands subclasses a connected Exposed `Database`. See `test/kotlin/repository/DiscordRepositoryImplTest.kt` for the pattern to follow when adding a new repository test.

Test sources live in `test/kotlin/` (a sibling of `src/`), not the conventional `src/test/kotlin` — `kotlin-maven-plugin`'s main `compile` execution scans `<sourceDirectory>src</sourceDirectory>` recursively, so a nested test directory would get compiled into the main (non-test) artifact too.

Since this spins up a real container, it needs a working Docker (or Podman) socket — same requirement as `docker-compose up`, just triggered by Maven instead. If `mvn test` fails with `Could not find a valid Docker environment`, see Troubleshooting below.

## Architecture (N-Tier)

The backend follows a clean N-Tier architecture with strict layer separation:

1. **API / Routes Layer (`src/routes/`)**: Defines KTor HTTP route endpoints, parses request bodies/parameters, calls the appropriate service method, and returns HTTP responses with DTOs. Does not perform direct database queries or transactions.
2. **Service Layer (`src/service/`)**: Contains business logic, validation, third-party notifications (e.g. Discord bot notifications), and coordinates operations by calling the repository layer.
3. **Repository Layer (`src/repository/`)**: Encapsulates Exposed ORM database transactions, CRUD operations, SQL queries, and mapping database rows to DTOs/models.
4. **DTO / Data Layer (`src/dto/`, `src/Tables.kt`)**: Defines data transfer objects for API contracts and Exposed table schema definitions.
5. **Validation Layer (`src/validation/`)**: Encapsulates DTO validation rules and integration with Ktor's `RequestValidation` plugin. Reusable validation helpers live in `ValidationRules.kt`.
6. **Constants (`src/constants/Constants.kt`)**: Centralized object `BackendConstants` containing route paths, default settings, error codes/messages, and validation limits/patterns/messages. String literals, regexes, and numerical bounds are defined here rather than hardcoded in application logic.

Repositories and services are wired in `src/Dependencies.kt`, which exposes only services — application setup and routes never reach a repository directly. `src/Application.kt` is the entry point: connect the database, build `Dependencies`, apply the `configureX()` functions from `src/plugins/`, mount routes.

## API Endpoints


The **Auth** column says what a request must present: *public* (nothing), *session* (the login cookie, see [Authentication](#authentication)), or *service* (a bearer service token).

| Method | Endpoint                        | Auth    | Description                                      | Response                                      |
| ------ | ------------------------------- | ------- | ------------------------------------------------ | ---------------------------------------------- |
| GET    | `/api/health`                   | public  | Health check with database connectivity          | `{"status": "ok", "database": "connected"}`   |
| POST   | `/api/auth/login`               | public  | Exchange email + password for a session cookie   | `{"id": "...", "email": "...", "role": "..."}` |
| GET    | `/api/auth/me`                  | session | The signed-in user and their role                | `{"id": "...", "email": "...", "role": "..."}` |
| POST   | `/api/auth/logout`              | session | End the current session                          | `{"status": "logged_out"}`                     |
| POST   | `/api/auth/logout-all`          | session | End every session for the current user           | `{"status": "logged_out_everywhere"}`          |
| GET    | `/api/discord/servers`          | session | List all tracked Discord servers                 | `[{"id": "...", "guildId": "...", ...}]`       |
| POST   | `/api/discord/servers/{id}/leave` | session | Disconnect bot from server (marks left & leaves) | `{"status": "updated", "guildId": "...", ...}` |
| DELETE | `/api/discord/servers/{id}`     | session | Soft-deletes server and disconnects bot          | `{"status": "deleted", "guildId": "...", ...}` |
| GET    | `/api/discord/bot/invite`       | session | Get Discord bot OAuth invitation URL             | `{"clientId": "...", "inviteUrl": "..."}`      |
| POST   | `/api/discord/bot/sync`         | service | Sync guild info from Discord Bot gateway         | `{"status": "synced"}`                         |

`/api/health` stays public on purpose — the container healthchecks poll it.

Test health: `curl http://localhost:8080/api/health`  
Test servers: `curl -b cookies.txt http://localhost:8080/api/discord/servers` (after logging in below)

## Authentication

Login exchanges email and password for an **opaque session token**, delivered as a cookie. The cookie carries the token and nothing else — no user id, role, or expiry — because the `sessions` table is the authority. That's what makes logout, suspension and role changes take effect on the *next request*.

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"your-password"}'

curl -b cookies.txt http://localhost:8080/api/auth/me
curl -b cookies.txt -X POST http://localhost:8080/api/auth/logout
```

**Cookie**: `angora_session`, `HttpOnly`, `SameSite=Strict`, `Path=/`, 7 days. Marked `Secure` when `COOKIE_SECURE=true` — required behind TLS, off by default because a `Secure` cookie is never sent over plain-HTTP dev.

No CORS config is needed for anything this repo ships: nginx serves the frontend on the API's origin, and `pnpm run dev:frontend` proxies `/api`, so both are same-origin. `CORS_ALLOWED_ORIGINS` is an escape hatch for a genuinely cross-origin deployment, and ships empty on purpose — the cookie makes every request credentialed, so a default would grant that origin authenticated access. Wildcards are rejected.

**Identifying the user**: no company selector at login. `users` stays unique on `(company_id, email)`, but `V7` adds a global unique index on `lower(email)` for non-deleted rows, making email alone a valid, case-insensitive lookup key — the right trade for a self-hosted install that is normally one company.

**Two hashing schemes, deliberately**:

| Secret | Algorithm | Why |
| --- | --- | --- |
| Passwords | Argon2id (m=19456 KiB, t=2, p=1), via Bouncy Castle | User-chosen and low-entropy, so it must be slow to attack |
| Session & service tokens | SHA-256 | 256-bit `SecureRandom` values with nothing to guess. Hashing only stops a leaked database yielding live credentials, and these are verified on every request |

Password hashes are PHC strings (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`), so parameters travel with the hash. Raising the cost later needs no migration.

**Every failed login returns the same `401` / `invalid_credentials`** — unknown email, wrong password, locked, suspended, invited or deactivated alike. Anything else would tell an attacker which addresses have accounts. An unknown email still runs a throwaway Argon2 hash so it doesn't answer measurably faster. The real reason is logged against the request id:

```
[reqId=8a45bc19-...] c.angora.service.AuthServiceImpl - Login failed: status is 'suspended' (user a15c328a-...)
```

**Two defenses against guessing**: `users.failed_login_attempts` locks one account for 15 minutes after 5 consecutive failures; a rate limiter caps `/api/auth/login` at 10/minute so one client can't sweep many accounts. Throttled requests return `429` with code `rate_limited`.

**Service tokens** authenticate the bots, which have no user identity or role. Hashes live in `service_tokens`; the backend registers the Discord bot's at startup from `SERVICE_TOKEN_DISCORD_BOT`, and the bot sends the raw value as a bearer token. The realms are separate — a cookie is rejected on a service route and vice versa. Revoking (`revoked_at`) is immediate; rotating means changing the value and restarting, until an admin endpoint lands.

**Roles** come from the `roles` table (`owner`, `admin`, `member`, `customer`, seeded by `V2`), read fresh from the database per request rather than cached in the cookie. Nothing gates on them yet — every authenticated user reaches every CRM route.

**Not yet implemented**: signup, invitations, password reset, email verification, TOTP. The `users.two_factor_*` and `email_verified_at` columns are reserved. There's no admin UI either, so the first user is inserted by hand.

### Seeding the first user

Generate a hash with the real hashing service, then insert a company and user:

```bash
# From apps/backend, after `mvn package -DskipTests`
cat > /tmp/Hash.java <<'EOF'
public class Hash {
  public static void main(String[] args) {
    System.out.println(new cloud.angora.service.PasswordServiceImpl().hash(args[0]));
  }
}
EOF
java -cp target/backend.jar:/tmp /tmp/Hash.java 'your-password'
```

```sql
-- docker-compose exec -T postgres psql -U angora -d angora
INSERT INTO companies (name, slug) VALUES ('Acme', 'acme');

INSERT INTO users (company_id, role_id, full_name, email, password_hash, status)
SELECT c.id, r.id, 'Admin User', 'admin@example.com', '<paste the hash>', 'active'
FROM companies c, roles r
WHERE c.slug = 'acme' AND r.name = 'owner' AND r.company_id IS NULL;
```

The `status` must be `active`: a user left at the default `invited` cannot log in.

## Error Handling & Request Logging

Every 4xx/5xx response returns the same envelope:

```json
{
  "error": {
    "code": "server_not_found",
    "message": "Server not found",
    "requestId": "3fa2c1e0-3b5a-4b7a-9f2e-1a2b3c4d5e6f"
  }
}
```

`StatusPages` (`src/plugins/ErrorHandling.kt`) produces it, and every error path — authentication challenges included — goes through `call.respondError(...)` in `src/error/ErrorResponses.kt`, so the shape can't drift. Cases handled:

| Cause | Result |
| ----- | ------ |
| `ApiException` | The status/code/message it carries. This is how routes and services should signal any 4xx, rather than an ad-hoc `call.respond` |
| `RequestValidationException` | `400`, `validation_error` |
| `BadRequestException` / `SerializationException` | `400`, `bad_request` or `invalid_json` |
| Absent or unparseable body, or a missing `Content-Type` | `400`, `invalid_request_body`. Ktor raises two unrelated types here; both are mapped, or one falls through and a client mistake reads as a server error |
| Any other `Throwable` | `500`, `internal_error`, logged server-side so no stack trace leaks |
| Unmatched route | `404`, `not_found`, replacing Ktor's plain-text default |
| Rate limited | `429`, `rate_limited`. Mapped explicitly because `RateLimit` rejects before any handler runs |

**Request IDs**: `CallId` accepts an inbound `X-Request-Id` or generates a UUID, echoes it on the response, and fills the envelope's `requestId`. `CallLogging` puts it in SLF4J's MDC, and `logback.xml` renders `%X{requestId:-none}`, so every line emitted during a request is tagged `[reqId=<id>]` across all layers.

## Database access

Every configurable value goes through `SecretsProvider` (`src/config/`), built once at the top of `Application.module()`. The three database settings check the provider first, then fall back to Ktor's config:

```kotlin
fun setting(secretName: String, configKey: String): String =
    secrets.get(secretName) ?: environment.config.property(configKey).getString()
```

That fallback resolves `application.yaml`, which holds the only config block:

```yaml
database:
  url: "${DB_URL:jdbc:postgresql://localhost:5432/angora}"
  user: "${DB_USER:angora}"
  password: "${DB_PASSWORD:angora}"
```

`${VAR:default}` is Ktor's own substitution: if the real env var is set, use it, otherwise the literal default. There's no per-environment file and no `-config=` flag — the same `application.yaml` yields different values only because launch contexts set different env vars:

| Context | Env vars set? | Resolved `DB_URL` |
| --- | --- | --- |
| Local (`exec:java`, bare `java -jar`) | No | `localhost:5432` (the default) |
| `docker-compose up` | Yes, compose sets `DB_URL` | `postgres:5432` |
| `docker-compose --env-file .env.production up` | Same, with production credentials | `postgres:5432`, production password |

The last two use an identical image and `application.yaml`; the dev/production split happens entirely at the compose/`.env` layer.

The provider indirection is also what lets these come from Infisical instead, behind `INFISICAL_ENABLED` (off by default — see the root README's [Secret Management](../../README.md#secret-management-infisical)). With the flag off, behavior is identical to plain `System.getenv`. With it on, the backend fetches at startup and refuses to start if Infisical is unreachable.

### Environment variables

| Variable | Default | Notes |
| -------- | ------- | ----- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/angora` | Compose overrides this to `postgres:5432` |
| `DB_USER` | `angora` | |
| `DB_PASSWORD` | `angora` | |
| `SERVICE_TOKEN_DISCORD_BOT` | _(unset)_ | Registered into `service_tokens` at startup; must match the bot's value. Unset means the bot-sync route rejects everyone, with a warning logged |
| `COOKIE_SECURE` | `false` | Must be `true` behind TLS; `false` locally |
| `CORS_ALLOWED_ORIGINS` | _(empty)_ | Comma-separated exact origins. Empty is correct for everything this repo ships — see [Authentication](#authentication). Wildcards are rejected |

In Compose these come from `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` — see the root README's [Environment Variables](../../README.md#environment-variables). The dev/production split lives there, not here.

## Database Schema

Migrations are plain SQL files in `src/main/resources/db/migration/`, applied by [Flyway](https://flywaydb.org/) — `Application.kt` calls `Flyway.configure().dataSource(...).load().migrate()` before opening the Exposed connection, so the schema is always brought up to date automatically on every backend startup (dev and Docker alike). No manual migration command to remember. Files are named `V{n}__description.sql` and applied in order; Flyway tracks what's already run and won't re-apply or reorder anything, so an already-applied migration is never edited — a schema change is always a new file.

Current tables (all UUID-keyed, all scoped by `company_id` where relevant — see `apps/backend/AGENTS.md` for the "Add a new migration" steps):

| Table | Purpose |
| --- | --- |
| `companies` | The org(s) using this instance — one row for a self-hosted install, potentially many when the deployment is shared multi-tenant |
| `roles` | Global system roles (`owner`/`admin`/`member`/`customer`, seeded by `V2`) plus optional per-company custom roles |
| `users` | Login/auth — both internal staff and customer portal logins share this table, distinguished by `role_id` |
| `accounts` | Customer/prospect organizations a company does business with |
| `contacts` | People at those accounts; a contact only gets a portal login once linked to a `users` row |
| `discord_servers` | Connected Discord servers, tracking `guild_id`, `name`, `icon_url`, `owner_id`, `member_count`, and `bot_joined` status |
| `sessions` | Live login sessions (`V8`) — SHA-256 of the cookie token, expiry, revocation, plus IP/user-agent. The authority behind every authenticated request, which is what makes logout and suspension immediate |
| `user_identities` | How a user authenticates (`V9`). Only `provider = 'local'` today; it exists so SSO can later be a new provider row rather than a new auth architecture |
| `service_tokens` | Credentials for machine callers like the bots (`V10`) — SHA-256 of the token, revocation, last-used. No user, no role |

`src/Tables.kt` holds the matching Exposed `Table`/`UUIDTable` definitions used to query these from Kotlin — kept in sync with the SQL migrations by hand, since Flyway's migrations (not Exposed) are the source of truth for the actual schema.

## Troubleshooting

**Maven build fails**: check the JDK version in `Dockerfile` matches the Kotlin version, and that dependency versions are compatible.

**`dependency-reduced-pom.xml` appears after building**: a normal Shade Plugin byproduct. Gitignored, ignore it.

**`java -jar target/backend.jar` fails with "Neither port nor sslPort specified"**: `application.yaml` isn't being found. Two causes, both already fixed here but worth knowing if they resurface — the file must be `.yaml`, not `.yml` (Ktor's packaged-jar discovery only recognizes the former; `.yml` works under `exec:java`, which masks it in dev), and `maven-shade-plugin` needs a `ServicesResourceTransformer`, or only one of the two `ConfigLoader` service providers survives shading.

**"Invalid signature file digest for Manifest main attributes"**: a signed jar was repacked by shading, so the JVM refuses the fat jar. Bouncy Castle is the only signed jar here, and `<filters>` strips `META-INF/*.SF`/`*.DSA`/`*.RSA` **from `org.bouncycastle` only**.

If a *different* dependency hits this, that scoping is working — decide deliberately rather than widening to `*:*`. Stripping is safe for a library used through plain classes (as BC is, via `Argon2BytesGenerator`), since the fat jar is unsigned anyway and integrity comes from Maven's checksums. It is **not** safe for anything registered via `Security.addProvider()`: JCE rejects a stripped provider with "JCE cannot authenticate the provider".

This only shows up in the packaged jar — `mvn test` and `exec:java` use the plain classpath and pass regardless.

**Login fails with the correct password**: check `status` is `active` (it defaults to `invited`, which can't log in) and `locked_until` is null or past — 5 failures lock the account for 15 minutes. Both return the same `invalid_credentials` as a wrong password by design, so grep the server log for `Login failed:` and match the request id.

**Every login returns 429**: the limiter allows 10/minute. Wait, or restart the backend.

**Ktor dependency added but nothing works**: use the `-jvm` coordinate (`ktor-server-config-yaml-jvm`). The non-suffixed one resolves to a metadata-only stub with no classes, and fails silently.

**Backend won't start**: check `docker-compose logs postgres` and `logs backend`, then `curl http://localhost:8080/api/health`. Inside Docker, `DB_URL` must use `postgres` as the host, not `localhost`.

**Flyway error on startup**:

- Checksum mismatch / `FlywayValidateException` — an already-applied migration was edited. Revert it and add a new migration instead. On a local database only, dropping it and letting Flyway rebuild also works.
- A SQL error in a new migration — fix the `.sql` and restart. Flyway never recorded it as applied, so it retries cleanly.

**`mvn test` fails with `Could not find a valid Docker environment`**: Testcontainers needs a working Docker or Podman socket.

- Docker: works as-is.
- Podman: `systemctl --user start podman.socket`, then `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock mvn test`. If the Ryuk sidecar fails to start under rootless Podman, add `TESTCONTAINERS_RYUK_DISABLED=true`; containers are still cleaned up on a clean JVM exit.
- CI needs none of this.
