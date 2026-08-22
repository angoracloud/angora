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

Dependencies are wired in `src/Application.kt`.

## API Endpoints


| Method | Endpoint                        | Description                                      | Response                                      |
| ------ | ------------------------------- | ------------------------------------------------ | ---------------------------------------------- |
| GET    | `/api/health`                   | Health check with database connectivity          | `{"status": "ok", "database": "connected"}`   |
| GET    | `/api/discord/servers`          | List all tracked Discord servers                 | `[{"id": "...", "guildId": "...", ...}]`       |
| DELETE | `/api/discord/servers/{id}`     | Disconnect bot from server (marks left & leaves) | `{"status": "updated", "guildId": "...", ...}` |
| POST   | `/api/discord/bot/sync`         | Sync guild info from Discord Bot gateway         | `{"status": "synced"}`                         |
| GET    | `/api/discord/bot/invite`       | Get Discord bot OAuth invitation URL             | `{"clientId": "...", "inviteUrl": "..."}`      |

Test health: `curl http://localhost:8080/api/health`  
Test servers: `curl http://localhost:8080/api/discord/servers`

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

In Docker Compose these are set from `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` — see the root README's [Environment Variables](../../README.md#environment-variables) section. That's also where the actual dev-vs-production credential split happens (`.env` vs `.env.production`) — this module doesn't participate in that choice at all, it just reads whatever ends up in its environment.

None of this is read directly via `System.getenv()` in Kotlin — `Application.kt` only ever reads `environment.config.property(...)`, which resolves through the substitution above.

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

`src/Tables.kt` holds the matching Exposed `Table`/`UUIDTable` definitions used to query these from Kotlin — kept in sync with the SQL migrations by hand, since Flyway's migrations (not Exposed) are the source of truth for the actual schema.

## Troubleshooting

**Maven build fails**: check the JDK version in `Dockerfile` matches the Kotlin version; verify dependency versions are compatible; check Maven Central for latest versions.

**A `dependency-reduced-pom.xml` file appears in `apps/backend/` after building**: this is a normal byproduct of the Maven Shade Plugin (used to build `target/backend.jar`) — it's gitignored, safe to ignore.

**`java -jar target/backend.jar` fails with "Neither port nor sslPort specified"**: `application.yaml` isn't being found. Two known causes, both already fixed in this repo but worth knowing if they resurface: (1) the file must be named `application.yaml`, not `application.yml` — Ktor's automatic config-file discovery only recognizes the former in a packaged jar (`.yml` happens to work under `mvn compile exec:java`'s raw classpath, which masks the problem in dev); (2) `maven-shade-plugin` must include a `ServicesResourceTransformer` — without it, only one of the two `META-INF/services/io.ktor.server.config.ConfigLoader` providers (HOCON's and YAML's, contributed by different dependency jars) survives shading, silently dropping the other.

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
