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
- `src/dto/` — Request/response DTOs and models
- `src/Application.kt` — KTor plugins, dependency wiring, and route mounting
- `src/Tables.kt` — Exposed `Table`/`UUIDTable` definitions, kept in sync with `src/main/resources/db/migration/`
- `src/main/resources/application.yaml` — Ktor deployment config and database connection settings
- `src/main/resources/db/migration/` — Flyway SQL migrations
- `test/kotlin/` — Kotlin test sources (JUnit 5 + Testcontainers), see Testing above
- `pom.xml` — Dependencies and plugins
- `Dockerfile`, `.dockerignore` — Container configuration

## Forbidden changes

- Don't remove the health endpoint (`/api/health`)
- Don't bypass the N-tier architecture (routes must only call services, services must call repositories for DB changes)
- Don't change the port without updating both `application.yaml`'s `ktor.deployment.port` and `docker-compose.yml` — it's not hardcoded in `Application.kt` anymore
- Don't remove Exposed ORM unless explicitly requested
- If `maven-shade-plugin`'s config changes, keep the `ServicesResourceTransformer` — without it, only one of the two `META-INF/services/io.ktor.server.config.ConfigLoader` providers (HOCON's, from `ktor-server-core-jvm`, and YAML's, from `ktor-server-config-yaml-jvm`) survives shading into `target/backend.jar`, silently breaking config loading in the packaged jar (though not under `mvn exec:java`, which masks it)

## Common tasks

### Add a new API endpoint

1. Define DTOs in `apps/backend/src/dto/`
2. Define repository interface and Exposed implementation in `apps/backend/src/repository/`
3. Define service interface and business logic implementation in `apps/backend/src/service/`
4. Add the route handler in `apps/backend/src/routes/` calling the service
5. Wire the repository, service, and routes in `apps/backend/src/Application.kt`
6. Test with `docker-compose up --build backend`, or faster: `pnpm run dev:backend` (or `mvn compile exec:java` from `apps/backend/`) against `docker-compose up -d postgres` — hot-reloads on `mvn compile`, no restart needed. See `apps/backend/README.md`'s "Locally, with hot reload" section.


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
