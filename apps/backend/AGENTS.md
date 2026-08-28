# AI Agent Guidelines — Backend

Scoped to `apps/backend/`. See the [root AGENTS.md](../../AGENTS.md) for repo-wide rules (general rules, testing commands, dependency guardrails, CI).

- **Language**: Kotlin 2.4.0
- **Framework**: KTor 3.5.1
- **ORM**: Exposed 1.3.1 — imports are `org.jetbrains.exposed.v1.jdbc.*`, **not** the pre-1.0 `org.jetbrains.exposed.sql.*` paths shown in older tutorials/blog posts. Table objects (`src/Tables.kt`) use `UUIDTable` from `org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable` — not `org.jetbrains.exposed.v1.core.dao.id.UuidTable`, which is a different, Kotlin-native-`Uuid`-typed class with a deceptively similar name/casing.
- **Migrations**: Flyway 12.11.0 (`flyway-core` + `flyway-database-postgresql`, the latter required separately since Flyway 10 split DB-specific support out of core). SQL files live in `src/main/resources/db/migration/`, run automatically via `Flyway.configure().dataSource(...).load().migrate()` in `Application.kt` on every startup, before the Exposed `Database.connect(...)` call.
- **Build**: Maven with JDK 25
- **Dependencies**: Use the `-jvm` suffix for all KTor artifacts — the non-suffixed coordinate for a Kotlin-multiplatform Ktor module resolves under plain Maven to a metadata-only stub with zero real classes. It compiles fine and fails silently/confusingly at runtime, so this is easy to get wrong and hard to notice.
- **Config**: `src/main/resources/application.yaml` (must be `.yaml`, not `.yml` — Ktor's packaged-jar config auto-discovery doesn't recognize `.yml`) holds Ktor's own deployment/module config *and* the `database.*` block (`DB_URL`/`DB_USER`/`DB_PASSWORD`, each `${VAR:default}`-substituted against real env vars, no separate per-environment file). `Application.kt` reads it via `environment.config.property(...)`, not `System.getenv()` directly.
- **Testing**: JUnit 5 + Testcontainers (Postgres module), test-scoped in `pom.xml`. Test sources live in `test/kotlin/` — a **sibling** of `src/`, not `src/test/kotlin` — because `kotlin-maven-plugin`'s main `compile` execution scans `<sourceDirectory>src</sourceDirectory>` recursively; a nested test dir would get swept into that non-test-scoped compile. Repository tests extend `PostgresRepositoryTest` (`test/kotlin/testsupport/PostgresRepositoryTest.kt`), which starts one `postgres:18-alpine` Testcontainers container per JVM (the "singleton container" pattern — never annotate a subclass with `@Testcontainers`/`@Container`, that restarts the container per class) and runs the real Flyway migrations against it before handing subclasses a connected Exposed `Database`. See `test/kotlin/repository/DiscordRepositoryImplTest.kt` for the pattern: construct the repository with the inherited `database`, and add a plain `@AfterEach` that deletes that test's own rows — there's no generic multi-table cleanup helper, each repository test owns its own cleanup.

## Allowed changes

- `src/routes/` — KTor route definitions (API layer: handles HTTP request/response and calls service layer only)
- `src/service/` — Business logic and orchestration (Service layer: calls repository layer for data operations)
- `src/repository/` — Exposed ORM data access (Repository layer: handles database transactions and queries)
- `src/auth/` — Principals (`UserPrincipal`, `ServicePrincipal`), the session cookie type (`AngoraSession`), and the `requireUser()` route helper. See Authentication conventions below before changing anything here
- `src/dto/` — Request/response DTOs and models, including the shared `ApiError`/`ApiErrorEnvelope` in `src/dto/ErrorDto.kt`
- `src/validation/` — Request validation rules, DTO validators, and Ktor `RequestValidation` plugin configuration
- `src/constants/Constants.kt` — Backend constants (`BackendConstants`): route paths, defaults, error codes/messages, and validation limits/patterns/messages
- `src/error/ApiException.kt` — the shared exception type StatusPages maps to the error envelope; throw this from routes/services for expected 4xx/5xx conditions, see `apps/backend/README.md`'s "Error Handling & Request Logging" section
- `src/Application.kt` — KTor plugins, dependency wiring, and route mounting
- `src/error/` — `ApiException.kt`, the shared exception type StatusPages maps to the error envelope (throw it from routes/services for expected 4xx/5xx conditions), and `ErrorResponses.kt`, the `call.respondError(status, code, message)` helper every error response goes through. See `apps/backend/README.md`'s "Error Handling & Request Logging" section
- `src/plugins/` — one `Application.configureX()` per concern: `Monitoring.kt` (CallId/CallLogging), `ErrorHandling.kt` (StatusPages), `Http.kt` (ContentNegotiation/CORS), `Security.kt` (Sessions/Authentication/RateLimit). Add a new file here rather than growing `Application.kt` back into a single long function
- `src/Dependencies.kt` — builds repositories and services for one `Database`. Exposes **only services**: application setup and routes must not reach a repository directly
- `src/Application.kt` — the module entry point: connect the database, build `Dependencies`, call the `configureX()` functions, mount routes. Keep it a readable outline; the detail belongs in `src/plugins/`
- `src/Tables.kt` — Exposed `Table`/`UUIDTable` definitions, kept in sync with `src/main/resources/db/migration/`
- `src/main/resources/application.yaml` — Ktor deployment config and database connection settings
- `src/main/resources/logback.xml` — Logback config; keep the `%X{requestId}` MDC pattern so every log line stays traceable to a request
- `src/main/resources/db/migration/` — Flyway SQL migrations
- `test/kotlin/` — Kotlin test sources (JUnit 5 + Testcontainers), see Testing above
- `pom.xml` — Dependencies and plugins
- `Dockerfile`, `.dockerignore` — Container configuration

## Forbidden changes

- Don't remove the health endpoint (`/api/health`)
- Don't bypass the N-tier architecture (routes must only call services, services must call repositories for DB changes)
- Don't change the port without updating both `application.yaml`'s `ktor.deployment.port` and `docker-compose.yml` — it's not hardcoded in `Application.kt` anymore
- Don't remove Exposed ORM unless explicitly requested
- Don't move `COPY src ./src` above the `dependency:go-offline` step in the `Dockerfile`. The split is what keeps Maven dependencies in a layer cached against `pom.xml` alone; collapsing it makes every source edit re-download the entire dependency tree from Maven Central, turning a seconds-long rebuild into a multi-minute one. For the same reason, don't add `-o` to the `package` step — `go-offline` doesn't fetch every plugin dependency, and offline mode would turn a short top-up download into a hard failure.
- If `maven-shade-plugin`'s config changes, keep the `ServicesResourceTransformer` — without it, only one of the two `META-INF/services/io.ktor.server.config.ConfigLoader` providers (HOCON's, from `ktor-server-core-jvm`, and YAML's, from `ktor-server-config-yaml-jvm`) survives shading into `target/backend.jar`, silently breaking config loading in the packaged jar (though not under `mvn exec:java`, which masks it)
- Don't hardcode string literals, regexes, numerical limits, or error messages in validation/route logic — all constants belong in `src/constants/Constants.kt` (`BackendConstants`)

## Authentication conventions

Full behavior is documented in [`README.md`](README.md#authentication); these are the rules that are easy to break by accident.

1. **Every failed login returns the identical response** — `401` / `invalid_credentials` — for an unknown email, a wrong password, and a locked, suspended, invited, or deactivated account alike. Do not add a more "helpful" message such as "no account for that email" or "your account is suspended" to any of these paths: the uniformity is what stops the endpoint being used to discover which addresses have accounts. Log the real reason instead (`AuthServiceImpl` already does), where the `requestId` MDC makes it traceable.

2. **Keep the dummy hash on the unknown-email path.** `PasswordService.dummyVerify()` exists so that a login for a nonexistent account costs about the same as one with a wrong password. Deleting it as dead code turns response latency into the same enumeration oracle that rule 1 closes.

3. **Two hashing schemes, not one.** Passwords use Argon2id (slow, deliberately). Session and service tokens use SHA-256, because they are 256-bit random values with nothing to brute-force and are verified on every single request. Do not "improve consistency" by moving tokens to Argon2 — that taxes the whole API for no security gain — and never move passwords to SHA-256.

4. **New routes are authenticated unless there's a reason.** Wrap them in `authenticate(BackendConstants.Auth.USER_PROVIDER)` for human callers or `SERVICE_PROVIDER` for machine ones, and reach the caller with `call.requireUser()`. `/api/health` is deliberately public because container healthchecks poll it; don't gate it.

5. **The session cookie carries an opaque token and nothing else.** Don't add the user id, role, or permissions to `AngoraSession` as an optimization — the `sessions` table being the single authority is what makes logout and suspension take effect on the next request. `AngoraSession` must stay `@Serializable`; Ktor's cookie serializer resolves a kotlinx serializer at install time and the app fails to start without one.

6. **Don't reintroduce `anyHost()` in the CORS config.** Ktor rejects a wildcard origin combined with `allowCredentials = true`, and the cookie makes every request credentialed. Origins come from `CORS_ALLOWED_ORIGINS` and must be exact.

7. **Keep the shade plugin's `<filters>` block, and keep it scoped to `org.bouncycastle:*`.** Bouncy Castle ships a signed jar; shading invalidates the signature and the packaged jar then refuses to load entirely. Don't widen the filter to `*:*` — the narrow scope is what makes a *future* signed dependency fail the build loudly instead of being silently stripped, which matters because stripping is only safe for libraries used through their plain classes (as BC is here) and breaks anything registered as a JCE provider. This failure is invisible to `mvn test`; see the README's Troubleshooting section.

8. **Adding a signed or crypto dependency**: check the license first (the root AGENTS.md's Licensing section). `de.mkammerer:argon2-jvm` is LGPL-3.0 and was rejected for that reason; Bouncy Castle's `bcprov-jdk18on` is MIT. Note `bc-kotlin` is not a substitute — it wraps PKI/certificate APIs, not crypto primitives, and isn't published to Maven Central.

## Constants discipline

**No hardcoded literals in routes, services, repositories, or plugins — full stop.** Everything below belongs in `src/constants/Constants.kt` under `BackendConstants`, even when it currently appears exactly once, and this applies to every change, not just to adding an endpoint. The frontend holds the same rule for its own literals (see [`apps/frontend/AGENTS.md`](../frontend/AGENTS.md)); this is the backend half of it.

What that covers today, and the group each belongs in:

- **`Routes`** — every path segment (`AUTH_BASE`, `AUTH_LOGIN`, …). Routes are mounted from these constants, never from a string typed at the `route(...)` call.
- **`Paths`** — path literals with no single owner. `ROOT` (`"/"`) lives here rather than being retyped as a cookie path, a healthcheck target, or a redirect.
- **`Auth`** — provider names, cookie attributes, TTLs and sweep intervals, token sizes, hash algorithm names, the `Argon2` parameter block, lockout and rate-limit settings, and the env var names they are read from (`COOKIE_SECURE_ENV`, `CORS_ALLOWED_ORIGINS_ENV`, …). Read env vars as `System.getenv(BackendConstants.Auth.X_ENV)`, never with the name inline.
- **`Errors`** — the `code`/`message` pair for every `ApiException` and `respondError` call, as a `_CODE`/`_MESSAGE` pair. These are the API's error contract; other endpoints end up needing the same code.
- **API response status literals** — values a DTO carries as part of the contract, e.g. `Auth.LogoutStatus.CURRENT_SESSION` / `ALL_SESSIONS` for `LogoutResponse.status`.
- **Server-side log reason strings** — `Errors.LoginFailureReasons`. Templated ones are `String.format` patterns applied at the call site (`ACCOUNT_LOCKED.format(user.lockedUntil, user.id)`), not string interpolation.

Two things that are *not* covered, so don't move them: SLF4J log message templates with `{}` placeholders (`logger.info("Login succeeded for user {}", id)`) stay at the call site where the format and its arguments are read together, and so do exception messages for genuinely internal invariant failures.

**`Errors.LoginFailureReasons` is log-only.** Its values name why a login really failed, for the operator. They must never reach a caller — every failed-login path answers with `INVALID_CREDENTIALS_CODE`/`INVALID_CREDENTIALS_MESSAGE`, per rule 1 of the Authentication conventions above. Don't pass one to an `ApiException`.

## Common tasks

### Add a new API endpoint

1. Define DTOs in `apps/backend/src/dto/`
2. Define repository interface and Exposed implementation in `apps/backend/src/repository/`
3. Define service interface and business logic implementation in `apps/backend/src/service/`
4. Add the route handler in `apps/backend/src/routes/` calling the service — for expected error conditions (validation, not-found, etc.), `throw ApiException(statusCode, code, message)` rather than manually building an error response; StatusPages converts it to the standard envelope. See `apps/backend/README.md`'s "Error Handling & Request Logging" section. The route path and the `code`/`message` pair are constants, not literals — see [Constants discipline](#constants-discipline) above.
5. Wire the repository, service, and routes in `apps/backend/src/Application.kt`
6. If the endpoint accepts a request body DTO, add validation (see "Add request validation for a new DTO" below).
7. Test with `docker-compose up --build backend`, or faster: `pnpm run dev:backend` (or `mvn compile exec:java` from `apps/backend/`) against `docker-compose up -d postgres` — hot-reloads on `mvn compile`, no restart needed. See `apps/backend/README.md`'s "Locally, with hot reload" section.

### Add request validation for a new DTO

1. Define all validation constraints, field names, length limits, regex patterns, and message formatters in `src/constants/Constants.kt` under `BackendConstants.Validation` (and `BackendConstants.Errors` if new error codes are needed). **Never hardcode string literals, regex patterns, or numerical limits directly in validator functions.**
2. Implement a validator function in `src/validation/` using the reusable helpers in `ValidationRules` (`requireNonBlank`, `requireMaxLength`, `requireMinLength`, `requireEmail`, `requireUuid`, `requirePositiveOrZero`, `requireRange`, `requireUrl`).
3. Register the validator in `RequestValidationConfig.configureRequestValidation()` in `src/validation/RequestValidation.kt`.
4. Write unit tests for the validator in `test/kotlin/validation/` and integration tests for route error mapping in `test/kotlin/routes/`.


### Add a new migration

1. Add a new file to `src/main/resources/db/migration/`, named `V{n}__description.sql` where `{n}` is the next unused integer (Flyway applies them in numeric order, tracks what's already run, and refuses to re-run or reorder an applied one — never edit an already-applied migration in place, add a new one instead).
2. Respect FK dependency order: a table referencing another must come after it.
3. If the table needs an `updated_at` column, reuse the shared `set_updated_at()` trigger function created in `V1__create_companies_table.sql` rather than redefining it.
4. Update `src/Tables.kt` with a matching Exposed `Table`/`UUIDTable` definition — the SQL migration is the source of truth, `Tables.kt` should always mirror it exactly.
5. Migrations run automatically on the next backend startup (`Application.kt` calls `Flyway...migrate()` before `Database.connect(...)`) — no manual migration command needed. Test with `docker-compose up -d postgres` + `mvn compile exec:java`, then confirm with `docker-compose exec postgres psql -U angora -d angora -c '\dt'`.

### Add a repository test

1. Create `test/kotlin/repository/<Name>RepositoryImplTest.kt`, package `cloud.angora.repository`, extending `cloud.angora.testsupport.PostgresRepositoryTest`.
2. Construct the repository under test with the inherited `database` property — no mocking, the point is to run real queries against the real (ephemeral, Flyway-migrated) schema.
3. Add a plain `@AfterEach` that deletes that test's own table's rows via `transaction(database) { <Table>.deleteAll() }` for isolation between tests — copy the pattern from `DiscordRepositoryImplTest.kt` rather than inventing a shared cleanup helper.
4. Run with `mvn -f apps/backend/pom.xml test` — needs a working Docker (or Podman) socket, since the base class starts a real container. See Troubleshooting below if it can't find one.

## Dependencies

Maven has no automatic supply-chain age gate (unlike the pnpm side). After adding or bumping any dependency/plugin in `pom.xml`, run the audit script from the repo root before considering the task done:

```bash
node scripts/check-dependency-age.ts
# or: pnpm run check:dep-age
```

If it reports a violation, pick an older version of that artifact — check the real publish date via `curl -sI https://repo1.maven.org/maven2/<group-path>/<artifact>/<version>/<artifact>-<version>.pom | grep -i last-modified` (Maven Central's Solr search index lags/misses recent releases, so don't trust `search.maven.org` for this). See the root AGENTS.md's [Dependency Pinning & Guardrails](../../AGENTS.md#dependency-pinning--guardrails) for the full policy.

Also check the license of any new Maven dependency/plugin, not just its age — see the root AGENTS.md's [Licensing](../../AGENTS.md#licensing) section. Watch especially for dual-licensed "open-core" JVM libraries (free for Postgres/open-source databases, paid for commercial ones).

## Troubleshooting

- **Maven build fails**: check the JDK version in `Dockerfile` matches the Kotlin version; verify dependency versions are compatible; check Maven Central for latest versions.
- **Database connection fails**: verify PostgreSQL is running (`docker ps`), check `docker-compose logs postgres`, ensure `DB_URL` uses `postgres` as hostname (not `localhost`) inside Docker.
- **`mvn test` fails with `Could not find a valid Docker environment`**: Testcontainers (used by `PostgresRepositoryTest`) needs a working Docker or Podman socket. On a host with Docker, this works with no extra setup. On Podman-only hosts: `systemctl --user start podman.socket`, then run tests with `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock mvn -f apps/backend/pom.xml test` (add `TESTCONTAINERS_RYUK_DISABLED=true` too if Ryuk, the container-reaper sidecar, can't start under rootless Podman — Testcontainers still stops containers cleanly on JVM exit without it, just without the extra safety net for crashed JVMs). CI doesn't need any of this — GitHub's `ubuntu-latest` runners have Docker preinstalled and running natively.
- **`java -jar target/backend.jar` fails with "Neither port nor sslPort specified"**: `application.yaml` isn't being found — see `apps/backend/README.md`'s Troubleshooting section for the two known causes (wrong file extension, missing shade-plugin transformer) and how they were fixed here.
