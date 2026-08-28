# Backend

KTor REST API backend with Exposed ORM, backed by PostgreSQL.

- **Language**: Kotlin 2.4.0
- **Framework**: KTor 3.5.1
- **ORM**: Exposed 1.3.1 — imports live under `org.jetbrains.exposed.v1.*` (the post-1.0 package layout, not the older `org.jetbrains.exposed.sql.*` paths shown in older tutorials)
- **Database**: PostgreSQL 18
- **Build**: Maven, JDK 25

See the [root README](../../README.md) for the one-command `docker-compose up --build` quickstart and repo-wide concerns (environment variables, CI, dependency guardrails). Every `docker`/`docker-compose` command on this page works the same with `podman`/`podman-compose` — see the root README's [Container Runtime](../../README.md#container-runtime) section.

## Running

**Via Docker/Podman** (from the repo root): `docker-compose up --build backend` — needs `postgres` running too; `docker-compose up --build postgres backend` starts both.

**Locally, with hot reload** (recommended for development):

```bash
# Start just the database in Docker, publishing 5432 to the host
docker-compose up -d postgres   # from the repo root

cd apps/backend
mvn compile exec:java
```

Or from the repo root, without `cd`-ing in: `pnpm run dev:backend` — same command, just a shortcut.

No env vars needed — `DB_URL` defaults to `localhost:5432` when nothing overrides it. See "Database access" below for exactly how that works.

This runs via Ktor's `EngineMain` in development mode (`-Dio.ktor.development=true`, set by `exec-maven-plugin` in `pom.xml`). Server config — port, host, which module to load, and the database connection — all live in `src/main/resources/application.yaml`, not in `Application.kt`. Must be `.yaml`, not `.yml` — see Troubleshooting.

Once it's up, edits to `.kt` files take effect on the *next request* as soon as they're recompiled — no restart of the running server needed:

```bash
mvn compile
```

To trigger that automatically on save instead of running it by hand: enable "Build project automatically" in IntelliJ, or use a file watcher —

```bash
sudo apt install entr   # one-time
find src -name '*.kt' | entr -r mvn -q compile
```

Caveats: only route/module logic reloads cleanly this way. Top-level `val`s (like the `database` connection) get reinitialized on every reload since they live in the same reloaded class. Changes to `main()` itself, or new dependencies, still need you to stop and re-run `mvn compile exec:java`.

**Locally, packaged jar** (closer to how Docker actually runs it):

```bash
docker-compose up -d postgres   # from the repo root

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

Login exchanges an email and password for an **opaque session token**, delivered as a cookie. The cookie carries the token and nothing else — no user id, role, or expiry — because the `sessions` table is the authority on all of that. That is what makes logout, suspension, and role changes take effect on the *next request* rather than whenever a self-describing token would have expired.

```bash
# Log in, keeping the cookie jar
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"your-password"}'

curl -b cookies.txt http://localhost:8080/api/auth/me
curl -b cookies.txt -X POST http://localhost:8080/api/auth/logout
```

**Cookie**: named `angora_session`, `HttpOnly` (so page scripts can't read it), `SameSite=Strict`, `Path=/`, 7-day lifetime. It is marked `Secure` when `COOKIE_SECURE=true` — required in any deployment with TLS, and off by default because local dev runs on plain HTTP where a `Secure` cookie would never be sent. No CORS configuration is needed for any setup this repo ships: nginx serves the frontend on the API's own origin in Docker Compose, and `pnpm run dev:frontend` proxies `/api` to the backend from the Vite dev server (`apps/frontend/vite.config.ts`), so the browser is same-origin there too. `CORS_ALLOWED_ORIGINS` is an escape hatch for a deployment that genuinely puts the frontend on another origin, and it ships empty deliberately — the cookie makes every request credentialed, so a default value would grant that origin authenticated access anywhere the variable isn't overridden. Wildcards are rejected because credentialed requests require exact origins.

**Identifying the user**: login takes an email and password with no company selector. `users` is still unique on `(company_id, email)`, but `V7` adds a global unique index on `lower(email)` (for non-soft-deleted rows), which makes email alone a valid lookup key — the right trade for a self-hosted install that is normally one company. Lookups are case-insensitive.

**Two hashing schemes, deliberately**:

| Secret | Algorithm | Why |
| --- | --- | --- |
| Passwords | Argon2id (m=19456 KiB, t=2, p=1), via Bouncy Castle | User-chosen and low-entropy, so it must be slow to attack |
| Session & service tokens | SHA-256 | 256-bit `SecureRandom` values with nothing to guess; hashing only stops a leaked database yielding live credentials. Verified on every request, where a slow hash would tax the whole API for no gain |

Password hashes are stored as PHC strings (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`), so the parameters travel with each hash. Raising the cost later needs no migration and doesn't invalidate existing passwords.

**Every failed login returns the same response** — `401` with code `invalid_credentials` — whether the email is unknown, the password is wrong, or the account is locked, suspended, invited, or deactivated. Distinguishing them would tell an attacker which addresses have accounts. For the same reason, a login for an unknown email still runs a throwaway Argon2 hash, so a missing account doesn't answer measurably faster. The real reason is logged server-side against the request id, so operators can still diagnose it:

```
[reqId=8a45bc19-...] c.angora.service.AuthServiceImpl - Login failed: status is 'suspended' (user a15c328a-...)
```

**Two defenses against guessing**, covering different attacks: `users.failed_login_attempts` locks a single account for 15 minutes after 5 consecutive failures, and a rate limiter caps `/api/auth/login` at 10 requests/minute so one client can't work through many accounts. A throttled request returns `429` in the standard error envelope with code `rate_limited`.

**Service tokens** authenticate machine callers (the bots), which hold no user identity and no role. Hashes live in `service_tokens`; the backend registers the Discord bot's at startup from `SERVICE_TOKEN_DISCORD_BOT`, and the bot sends the raw value as `Authorization: Bearer <token>`. Both services must see the same value. The two realms are fully separate — a session cookie is rejected on a service route and a service token is rejected on a user route. Revoking a token (setting `revoked_at`) takes effect immediately; *rotating* one currently means changing the configured value and restarting, until an admin endpoint to issue and revoke them lands with user management.

**Roles** come from the `roles` table (`owner`, `admin`, `member`, `customer`, seeded by `V2`). The role name rides on `UserPrincipal` and is read fresh from the database on every request rather than cached in the cookie, so a role change takes effect immediately. Nothing gates on it yet — every authenticated user can reach the CRM routes, and adding per-role checks is follow-on work.

**Not yet implemented**: signup, invitations, password reset, email verification, and TOTP two-factor. The `users.two_factor_*` and `email_verified_at` columns are reserved for those and currently unused. There is also no admin UI for creating users, so the first one has to be inserted directly (see [Seeding the first user](#seeding-the-first-user)).

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

Every 4xx/5xx response returns the same JSON envelope:

```json
{
  "error": {
    "code": "server_not_found",
    "message": "Server not found",
    "requestId": "3fa2c1e0-3b5a-4b7a-9f2e-1a2b3c4d5e6f"
  }
}
```

This is produced by `StatusPages` (`src/plugins/ErrorHandling.kt`), and every error response — including authentication challenges — goes through the same `call.respondError(...)` helper in `src/error/ErrorResponses.kt`, so the shape can't drift. It handles five cases:

- **`ApiException`** (`src/error/ApiException.kt`) — the convention for expected error conditions. Routes/services throw `ApiException(statusCode, code, message)`; StatusPages catches it and builds the envelope with that status/code/message. This is how every new endpoint (auth, tickets, channels, ...) should signal a 4xx, instead of manually calling `call.respond(status, someAdHocBody)`.
- **`RequestValidationException`** — thrown by Ktor's `RequestValidation` plugin when incoming DTO payload rules fail. StatusPages catches it and returns `400 Bad Request` with `code: "validation_error"` and a combined reason message.
- **`BadRequestException` / `SerializationException`** — thrown by Ktor/kotlinx.serialization when request JSON is malformed or invalid. StatusPages catches it and returns `400 Bad Request` with `code: "bad_request"` or `code: "invalid_json"` rather than falling through to a 500 error.
- **A malformed request body** — absent, unparseable, or sent without a usable `Content-Type`. Ktor raises two unrelated exception types for these (`BadRequestException`, and `ContentTransformationException` whose subclass covers the header case); both map to `400` with `code: "invalid_request_body"`. Without handling both, one of them falls through to the `Throwable` branch and a client mistake gets reported as a server error.
- **Any other `Throwable`** — logged server-side, mapped to a generic `500` with `code: "internal_error"` so unexpected exceptions never leak a stack trace to the client.
- **Unmatched routes** — Ktor's default 404 is replaced with the same envelope (`code: "not_found"`) instead of a plain-text response.
- **Rate-limited requests** — `RateLimit` rejects before any handler runs, so `429` is mapped explicitly (`code: "rate_limited"`); otherwise it would be the one response in the API with no envelope.

**Request IDs**: `CallId` (installed alongside `CallLogging`/`StatusPages`) accepts an inbound `X-Request-Id` header, or generates a UUID if the client didn't send one, and echoes it back on the response via the same header. That id is also what populates the `requestId` field in every error envelope.

**Logs**: `CallLogging` puts the request id into SLF4J's MDC under the key `requestId` for the duration of each call, and `src/main/resources/logback.xml` includes `%X{requestId:-none}` in the log pattern — so every log line emitted while handling a request (including from `service`/`repository` code via their own `LoggerFactory.getLogger(...)`) is tagged `[reqId=<id>]`, making a single request's logs traceable across layers.

## Database access

The backend connects via Exposed, reading the connection details from Ktor's config (`environment.config`) rather than `System.getenv()` directly:

```kotlin
val database = Database.connect(
    url = environment.config.property("database.url").getString(),
    driver = "org.postgresql.Driver",
    user = environment.config.property("database.user").getString(),
    password = environment.config.property("database.password").getString()
)
```

All config — including the database block — lives in the one `application.yaml`:

```yaml
database:
  url: "${DB_URL:jdbc:postgresql://localhost:5432/angora}"
  user: "${DB_USER:angora}"
  password: "${DB_PASSWORD:angora}"
```

`${VAR:default}` is Ktor's own environment-variable substitution (no extra library). The rule is always the same, everywhere this file is read: **if a real `DB_URL`/`DB_USER`/`DB_PASSWORD` environment variable is set, use it; otherwise fall back to the literal default shown.** There's no per-environment file and no `-config=` flag to remember — the same `application.yaml` produces different actual values only because different launch contexts set different real env vars:

| Context | Real env vars set? | Resolved `DB_URL` |
| --- | --- | --- |
| Local (`mvn compile exec:java` / bare `java -jar`) | No | Falls through to the default: `localhost:5432` |
| `docker-compose up` (dev) | Yes — `docker-compose.yml` sets `DB_URL=postgres:5432` explicitly | `postgres:5432` (from the env var, overriding the default) |
| `docker-compose --env-file .env.production up` | Yes — same `docker-compose.yml`, but `POSTGRES_PASSWORD` (etc.) now comes from `.env.production` | `postgres:5432`, with the production password |

The last two rows use the exact same `application.yaml` and the exact same Docker image — the dev/production split happens entirely at the `docker-compose`/`.env` layer (see the root README), one level above this file. `application.yaml` never needs to know which one it's in; it just reads whatever's in its environment.

### Environment variables

| Variable      | Default                                    | Notes                                                                                     |
| -------------- | ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `DB_URL`      | `jdbc:postgresql://localhost:5432/angora`  | Docker Compose overrides this to `postgres:5432` for the containerized backend |
| `DB_USER`     | `angora`                                    |                                                                                                |
| `DB_PASSWORD` | `angora`                                    |                                                                                                |
| `SERVICE_TOKEN_DISCORD_BOT` | _(unset)_                     | Shared secret the Discord bot authenticates with. Registered into `service_tokens` at startup; must match the bot's own value. Unset means the bot-sync route rejects every caller, and a warning is logged |
| `COOKIE_SECURE` | `false`                                   | Marks the session cookie `Secure`. Must be `true` wherever TLS terminates in front of the backend; `false` locally, since a `Secure` cookie is never sent over plain HTTP |
| `CORS_ALLOWED_ORIGINS` | _(empty)_                          | Comma-separated exact origins allowed to send credentialed cross-origin requests. Empty is correct in every setup this repo ships — see [Authentication](#authentication). No default origin is baked in on purpose: a value grants that origin authenticated access, and a default would do so everywhere the variable isn't overridden. Wildcards are not accepted — Ktor refuses to combine one with credentials |

In Docker Compose the database values are set from `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` — see the root README's [Environment Variables](../../README.md#environment-variables) section. That's also where the actual dev-vs-production credential split happens (`.env` vs `.env.production`) — this module doesn't participate in that choice at all, it just reads whatever ends up in its environment.

The three database variables are never read directly via `System.getenv()` in Kotlin — `Application.kt` reads them through `environment.config.property(...)`, which resolves the substitution in `application.yaml`. The auth and Discord variables above are read with `System.getenv()` instead, since they aren't part of that config block.

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

**Maven build fails**: check the JDK version in `Dockerfile` matches the Kotlin version; verify dependency versions are compatible; check Maven Central for latest versions.

**A `dependency-reduced-pom.xml` file appears in `apps/backend/` after building**: this is a normal byproduct of the Maven Shade Plugin (used to build `target/backend.jar`) — it's gitignored, safe to ignore.

**`java -jar target/backend.jar` fails with "Neither port nor sslPort specified"**: `application.yaml` isn't being found. Two known causes, both already fixed in this repo but worth knowing if they resurface: (1) the file must be named `application.yaml`, not `application.yml` — Ktor's automatic config-file discovery only recognizes the former in a packaged jar (`.yml` happens to work under `mvn compile exec:java`'s raw classpath, which masks the problem in dev); (2) `maven-shade-plugin` must include a `ServicesResourceTransformer` — without it, only one of the two `META-INF/services/io.ktor.server.config.ConfigLoader` providers (HOCON's and YAML's, contributed by different dependency jars) survives shading, silently dropping the other.

**`java -jar target/backend.jar` fails with "Invalid signature file digest for Manifest main attributes"**: a dependency ships a *signed* jar, and shading repacked its contents, invalidating the signature — the JVM then refuses to load the fat jar at all. Bouncy Castle is the one signed jar in the current tree, and `maven-shade-plugin` has a `<filters>` block stripping `META-INF/*.SF`, `*.DSA`, and `*.RSA` **from `org.bouncycastle` only**.

If you hit this after adding a *different* dependency, that scoping is working as intended — decide deliberately rather than widening the filter to `*:*`. Stripping is safe when the library is used through its plain classes, as BC is here (`Argon2BytesGenerator`), because the fat jar is unsigned as a whole anyway and download-time integrity is enforced by Maven's checksum verification, not these files. It is **not** safe if the library is registered as a JCE provider via `Security.addProvider()` — JCE requires an intact signature and will reject a stripped provider at runtime with "JCE cannot authenticate the provider".

Note this failure appears **only in the packaged jar** — `mvn test` and `mvn compile exec:java` use the plain classpath and pass regardless, so a green test run doesn't rule it out.

**Login fails with the correct password**: check the user's `status` is `active` (the column defaults to `invited`, which cannot log in) and that `locked_until` is null or past — 5 consecutive failures lock an account for 15 minutes. Both cases deliberately return the same `invalid_credentials` response as a wrong password, so the distinguishing detail is in the server log: grep for `Login failed:` and match on the request id.

**Every login returns 429**: the login rate limiter allows 10 requests/minute. Scripted testing hits this quickly; wait a minute or restart the backend.

**Ktor dependency added but nothing works**: double check it uses the `-jvm`-suffixed artifact coordinate (e.g. `ktor-server-config-yaml-jvm`, not `ktor-server-config-yaml`) — see the note in the AGENTS.md file for this module. The non-suffixed coordinate for Kotlin-multiplatform Ktor modules resolves under plain Maven to a metadata-only stub with zero real classes; it fails silently rather than erroring, which makes this easy to miss.

**Backend won't start**:

- Check PostgreSQL is healthy: `docker-compose logs postgres`
- Verify the database connection: `docker-compose logs backend`
- Test manually: `curl http://localhost:8080/api/health`
- Make sure `DB_URL` uses `postgres` as the hostname (not `localhost`) when running inside Docker

**Backend fails on startup with a Flyway error**: check the exact message in `docker-compose logs backend` first.

- `FlywayValidateException` / checksum mismatch: an already-applied migration file was edited after the fact. Flyway hashes each migration and refuses to proceed if a previously-run file no longer matches what was recorded — revert the edit and add a new migration instead, or (local dev database only, never shared/production data) drop the database and let Flyway recreate it from scratch.
- Any actual SQL error (bad syntax, FK violation, etc.) in a new migration: fix the `.sql` file and restart — Flyway hasn't recorded that version as applied, so it'll retry it cleanly next launch.

**`mvn test` fails with `Could not find a valid Docker environment`**: Testcontainers (see Testing above) needs a working Docker or Podman socket to start its ephemeral Postgres container.

- Docker: works out of the box, nothing to configure.
- Podman: start the user socket once with `systemctl --user start podman.socket`, then point Testcontainers at it: `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock mvn test`. Under rootless Podman, Testcontainers' Ryuk sidecar (which reaps containers if the JVM crashes) sometimes can't start — add `TESTCONTAINERS_RYUK_DISABLED=true` if you see it fail; containers still get stopped normally on a clean JVM exit either way.
- CI needs none of this — GitHub's `ubuntu-latest` runners have Docker preinstalled and running natively.
