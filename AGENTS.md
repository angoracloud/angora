# AI Agent Guidelines for Angora

This document provides repo-wide instructions and constraints for AI agents working with this project. Each module also has its own scoped `AGENTS.md` — read the one for whatever you're actually touching, in addition to this one:

| Module | AGENTS.md |
| -------- | ----------- |
| Backend | [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md) |
| Frontend | [`apps/frontend/AGENTS.md`](apps/frontend/AGENTS.md) |
| Bots (slack/discord/email — identical rules) | [`apps/bots/AGENTS.md`](apps/bots/AGENTS.md) |
| Shared config (`@angora/config`) | [`packages/config/AGENTS.md`](packages/config/AGENTS.md) |
| Shared secrets loader (`@angora/secrets`) | [`packages/secrets/AGENTS.md`](packages/secrets/AGENTS.md) |

## Project Overview

This is **Angora**, a self-hosted CRM/support system, built as a starter monorepo with the following architecture:

- **Backend**: KTor 3.5.1 + Kotlin 2.4.0 + Exposed ORM 1.3.1 + PostgreSQL 18
- **Frontend**: React 19 + TypeScript 7 + Vite 8
- **Bots**: Node.js 24+ + TypeScript 7 (Slack, Discord, Email)
- **Containerization**: Docker + Docker Compose

All components run in Docker containers and the entire stack starts with:

```bash
docker-compose up --build
```

See [README.md](README.md) for the full quickstart, service list, and project structure.

## General Rules

1. **Always use a container runtime**: Never assume host tools (JDK, Node.js, pnpm, Maven) are installed. All development and testing must happen inside containers, via Docker or Podman — see the root [README's Container Runtime section](README.md#container-runtime). `docker`/`docker-compose` commands shown anywhere in this repo's docs work identically with `podman`/`podman-compose`; don't assume Docker specifically is installed on the host.

2. **Read before editing**: Never edit a file without reading it first in the current session.

3. **Minimal changes**: Only modify files explicitly requested or necessary to fulfill the task. Don't touch unrelated files.

4. **Verify with a container build**: After any changes, validate with:
   ```bash
   docker-compose build && docker-compose up --build
   # or, with Podman:
   podman-compose build && podman-compose up --build
   ```

## Infrastructure Files

- **`docker-compose.yml`**: Service orchestration. `frontend`, `slack-bot`, `discord-bot`, `email-bot` build with `context: .` (repo root) + an explicit `dockerfile:` path — required so their builds can see `packages/config`. Only `backend` still uses `context: ./apps/backend`. Don't revert the four to a per-app context; that would break `@angora/config` resolution inside the image. Database credentials and host ports are `${VAR:-default}` interpolations reading from `.env` — see Environment Variables below.
- **`pnpm-workspace.yaml`**: Workspace packages (including `packages/config` and `packages/secrets`), the shared version `catalog:`, and the `minimumReleaseAge` supply-chain policy — see Dependency Pinning & Guardrails below before touching this file
- **`package.json`** (repo root): `packageManager` pin, the `check:dep-age`/`lint`/`typecheck`/`test`/`format`/`format:check`/`prepare`/`dev:frontend`/`dev:backend` scripts, and `husky` + `prettier` + `@angora/config` as devDependencies; not a workspace package itself. `dev:frontend`/`dev:backend` just shell out to each service's own local-dev command (see `apps/frontend/README.md` / `apps/backend/README.md`) — they exist purely so you don't have to `cd` in first, they don't add new behavior.
- **`prettier.config.ts`** / **`.prettierignore`** (repo root): The one Prettier config for the whole repo — don't add per-package Prettier configs
- **`.dockerignore`** (repo root): Used by the four root-context builds above; `apps/backend/.dockerignore` is separate and still used by backend's own context
- **`.env.example`** / **`.env.production.example`** (repo root): Templates for `.env`/`.env.production`, which are gitignored. Keep these in sync with whatever variables `docker-compose.yml` actually reads — if you add a new `${VAR:-default}` to docker-compose.yml, add the variable (with its default) to `.env.example` too, and to `.env.production.example` if it's something a real deployment should set explicitly (e.g. a password).
- **`scripts/infisical-env.ts`**: Resolves secrets from Infisical on the *host* and writes an env file for `docker-compose --env-file`. Exists because `postgres` is a third-party image whose `POSTGRES_PASSWORD` is only read at initdb — no in-process lookup can reach it, so compose's own interpolation has to be fed instead. Zero dependencies, run directly with `node`. It duplicates the two REST calls in `packages/secrets/src/infisical.ts` on purpose (Node doesn't map a `.js` specifier onto a `.ts` file, so it can't import them) — keep the two in sync.
- **`scripts/check-dependency-age.ts`**: Maven + npm dependency-age audit; keep it in sync if `pom.xml`'s structure or the catalog format changes. It reads every `package.json` in the workspace (root, `packages/config`, `packages/secrets`, `apps/frontend`, each bot) — add new manifests to its `manifests` list if you add a new workspace package. Runs directly via `node scripts/check-dependency-age.ts` — Node 24 executes `.ts` natively, no build step or `ts-node` needed.
- **`.gitignore`**: Standard ignore patterns. Do not add a bare `Dockerfile` entry — that previously matched every file named `Dockerfile` in the repo and silently kept all five of them out of git history. The `.env` block uses `.env` / `.env.*` with `!.env.example` / `!.env.*.example` negations — if you add a new env file pattern, make sure real files stay ignored and `.example` templates stay tracked.
- **`README.md`** / module `README.md`s: Documentation only.

## Environment Variables

`docker-compose.yml` sources its configurable values from environment variables, each with a `:-default` fallback matching the original hardcoded values — so `docker-compose up --build` still works with zero setup even if no `.env` file exists. The variables: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `BACKEND_PORT`, `FRONTEND_PORT`, `DISCORD_BOT_TOKEN`, `DISCORD_CLIENT_ID`, `SERVICE_TOKEN_DISCORD_BOT`, `COOKIE_SECURE`, `CORS_ALLOWED_ORIGINS`. Plus the Infisical block: `INFISICAL_ENABLED`, `INFISICAL_DOMAIN`, `INFISICAL_PROJECT_ID`, `INFISICAL_ENV`, `INFISICAL_SECRET_PATH`, `INFISICAL_CLIENT_ID`, `INFISICAL_CLIENT_SECRET`, `INFISICAL_TOKEN` — see below. `.env.example` documents all of them; `.env.production.example` is the same set with placeholder secrets that must be replaced.

### Infisical

Every service can source its secrets from Infisical instead of the environment, behind `INFISICAL_ENABLED` (default `false`). With the flag off, nothing changes: each service reads plain env vars exactly as before. With it on, the four services we build resolve their own secrets at startup — `apps/backend/src/config/` in Kotlin, [`@angora/secrets`](packages/secrets/README.md) for the bots — preferring Infisical values and falling back to the environment.

Three rules that are easy to break:

1. **Enabled + unreachable must stay fatal.** Services throw at startup rather than falling back, so a misconfiguration can't quietly boot the stack on the `angora`/`angora` dev credentials in `docker-compose.yml`. Don't add a "graceful degradation" fallback.
2. **Postgres is the exception.** It's a third-party image and only reads `POSTGRES_PASSWORD` at initdb, before any of our code runs. Sourcing it from Infisical means feeding compose itself — `node scripts/infisical-env.ts && docker-compose --env-file .env.infisical up -d --build`.
3. **The variable names live in three places** — `BackendConstants.Infisical`, `packages/secrets/src/constants.ts`, and `scripts/infisical-env.ts`. Change one, check the other two.

`SERVICE_TOKEN_DISCORD_BOT` is read by **two** services (`backend` and `discord-bot`) and they must see the same value — the backend registers its hash, the bot presents the raw token. Changing it in one place only breaks guild syncing with a 401.

- `.env` is auto-loaded by docker-compose from the project root (local dev, optional).
- `.env.production` is **not** auto-loaded — it must be passed explicitly with `docker-compose --env-file .env.production up -d --build`. That's intentional: a production run should never happen by accident.
- Both are gitignored; only the `.example` templates are tracked. If you add a new configurable value to docker-compose.yml, add it to both `.example` files too (see Infrastructure Files above).

**Important**: When accessing services from within the Docker network, use the service name as hostname (`postgres:5432`, `backend:8080`, `frontend:3000`) — never `localhost`/`127.0.0.1` for inter-service communication. Backend-specific variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`) are documented in [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md).

## API Communication

- Frontend → Backend: Use relative path `/api/...` (proxied via nginx). Same-origin, so the session cookie is sent automatically — no `Authorization` header and no CORS config needed.
- Bot → Backend: Use `http://backend:8080/...` (Docker network DNS), with `Authorization: Bearer ${SERVICE_TOKEN_DISCORD_BOT}`. Being on the Docker network is no longer sufficient on its own.
- External → Backend: Use `http://localhost:8080/...`

Backend routes are authenticated by default — see [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md#authentication-conventions) for which provider to wrap a new route in, and the rules around login responses that are easy to break by accident. `/api/health` is deliberately public because container healthchecks poll it.

## Testing Commands

After making changes, always verify with (substitute `podman`/`podman-compose` for `docker`/`docker-compose` if that's the available runtime — see [General Rules](#general-rules)):

```bash
# Full stack test
docker-compose down
docker-compose up --build

# Check containers
docker ps

# Test backend health
curl http://localhost:8080/api/health

# Test frontend
curl http://localhost:3000

# View logs
docker-compose logs -f backend
```

For frontend/bot changes, also run from the repo root before considering the task done:

```bash
pnpm install                 # re-verifies the age guardrail too
pnpm run lint                 # all packages
pnpm run typecheck            # all packages
pnpm run test                 # all packages (Vitest — currently placeholder tests, see README's Limitations)
pnpm run format:check         # or `pnpm run format` to fix
```

See each module's own `AGENTS.md` for `--filter`-scoped equivalents and module-specific extra checks (e.g. the frontend's second `tsconfig.node.json` typecheck pass).

## CI, hooks, and branch protection

- **Pre-commit** (`.husky/pre-commit`): runs `pnpm run lint` + `pnpm run format:check` on every commit, check-only (no auto-fix). Set up automatically by `pnpm install` via the root `prepare` script.
- **Branch protection**: the repo is public and `main` is protected server-side by GitHub — 1 required PR approval (stale reviews dismissed on new commits), the `Backend (Maven)`, `Frontend & bots (lint, typecheck, test, build)`, and `Dependency age guardrail` status checks must pass, conversations must be resolved, admins are included, and force-pushes/deletion are blocked. There is no local `pre-push` hook anymore — it was a stand-in for this and was removed once real protection went live.
- **Issue/branch naming**: every change traces back to an issue. Issues get auto-prefixed `ANGORA-<number>` on open (`.github/workflows/issue-title-prefix.yml`). Branches must be named `ANGORA-<issue-number>-short-description` — a repository ruleset rejects any new branch that isn't `main` or `ANGORA-*`, so always create branches with this prefix (GitHub's own "Create a branch" button does not generate it for you; type it explicitly).
- **CI** (`.github/workflows/ci.yml`): runs on every PR and every push to `main` — `backend` (`mvn test`), `frontend-bots` (lint, format:check, typecheck, test, build), `guardrails` (`check:dep-age`). All third-party Actions are pinned to commit SHA, not floating tags.
- **Deploy** (`.github/workflows/deploy.yml`): manual `workflow_dispatch` only, with TODO placeholder steps — intentionally inert until a real deploy target exists. Don't change its trigger to run automatically without filling in the real steps first.
- **Reviewing a PR**: follow [`.agents/skills/pr-review/SKILL.md`](.agents/skills/pr-review/SKILL.md) — a read-only, step-by-step review procedure covering requirements traceability, test quality, readability, dependency licensing, security, and conformance with these guidelines. It reads the rules from these files at review time rather than restating them, so it can't go stale. Claude Code picks it up automatically as a skill via the `.claude/skills/pr-review` symlink; other agents should read the file directly. It never approves a PR — the required human approval above is deliberately outside its reach.

## Success Criteria

A task is complete when:

1. ✅ All relevant tests pass (container build succeeds, via Docker or Podman)
2. ✅ The code runs and produces expected output
3. ✅ User's explicit acceptance criterion is met
4. ✅ No new warnings or errors in logs

## Version Constraints

| Component | Version | Notes |
| ----------- | --------- | ------- |
| Kotlin | 2.4.0 | Newest version that clears the 7-day age guardrail (2.4.10 is newer but too recent); required for KTor 3.5.1 + Exposed 1.3.1 |
| KTor | 3.5.1 | Latest stable |
| Exposed | 1.3.1 | Latest stable; post-1.0 `org.jetbrains.exposed.v1.*` package layout |
| Flyway | 12.11.0 | `flyway-core` + `flyway-database-postgresql` (split from core since Flyway 10); newest version clearing the 7-day age guardrail as of this pinning |
| PostgreSQL JDBC | 42.7.13 | Latest stable |
| PostgreSQL (server) | 18.x | `docker-compose.yml` uses `postgres:18-alpine`; volume mounts at `/var/lib/postgresql`, not `/var/lib/postgresql/data`; also the image `apps/backend`'s Testcontainers-based repository tests provision |
| Testcontainers | 2.0.5 | `testcontainers-bom` imported in `apps/backend/pom.xml`'s `dependencyManagement`; test-only. Module artifacts got a `testcontainers-` prefix in the 2.0 line (e.g. `org.testcontainers:testcontainers-postgresql`, not the pre-2.0 `org.testcontainers:postgresql`) — don't revert to the old unprefixed coordinates |
| JUnit Jupiter | 6.1.3 | `org.junit.jupiter:junit-jupiter` + `org.junit.platform:junit-platform-launcher` (the latter isn't pulled in transitively by the former as of JUnit 6 — Surefire needs it explicitly on the test classpath), both test-only in `apps/backend/pom.xml` |
| Node.js | 24.x | Active LTS; used for frontend and bots |
| @types/node | 24.13.3 | Matches the Node 24 runtime pinned everywhere above; shared via the pnpm catalog by `apps/frontend`, `packages/config`, and all three bots (frontend/`packages/config` don't execute Node code themselves, but need it so `vite`/`vitest`'s optional peer dependency resolves to one consistent version instead of floating) |
| React | 19.x | Latest stable |
| @tanstack/react-router | 1.170.29 | Frontend routing (replaced `react-router`). Code-based routing — a single route tree in `apps/frontend/src/router.tsx`, not file-based/codegen (no `@tanstack/router-plugin`) — revisit only if the route count grows a lot. Uses the **data router** (`createRouter`/`RouterProvider`), since `TopBar` reads route `staticData` via `useMatches()` |
| @tanstack/react-query | 5.101.4 | Frontend data-fetching/caching for any async server state — see `apps/frontend/src/hooks/discordQueries.ts` for the pattern. Don't hand-roll fetch/poll/loading-state logic (`setInterval` + focus listeners + `isMounted` guards) — use `useQuery`'s `refetchInterval`/`refetchOnWindowFocus` and let the cache dedupe across components instead of prop-drilling |
| babel-plugin-react-compiler | 1.0.0 | React Compiler, wired into `apps/frontend/vite.config.ts` via `@vitejs/plugin-react@6`'s `reactCompilerPreset()` + `@rolldown/plugin-babel` (its own internal Babel support was dropped in v6, so the plugin's old `babel.plugins` option no longer works). Runs in the default `compilationMode: 'infer'`; don't switch to `'all'` — see the comment above the `plugins` array in `vite.config.ts` for the upstream bug it hits and what would justify revisiting it |
| lucide-react | 1.31.0 | Frontend icon set; newest version clearing the 7-day age guardrail as of this pinning |
| TypeScript | 7.x | Latest stable (Go-based compiler); dropped `baseUrl` and `moduleResolution: "node"` |
| Vite | 8.x | Latest stable, for frontend |
| Vitest | 4.x | Test runner for frontend + all 3 bots — see each module's README for current test coverage status |
| ESLint | 10.x | Flat config (`eslint.config.mjs`) only, no legacy `.eslintrc`; the config files stay `.mjs`, not `.ts` — see `packages/config/AGENTS.md` |
| typescript-eslint | 8.63.0 | Not just "peer range caps under TS 7" — it hard-crashes on import against TypeScript 7 (confirmed through 8.64.0, the newest available). `packages/config` works around this with its own isolated `typescript@5.9.3` pin; see `packages/config/AGENTS.md`. Don't bump this without re-verifying against whatever TypeScript is pinned at the time |
| Prettier | 3.x | One config for the whole repo (`.ts`), see `packages/config/AGENTS.md` |
| jiti | — | **Not a dependency anywhere in this repo, intentionally.** It's what ESLint would need to load a `.ts` flat config, and installing it is what surfaced the typescript-eslint crash above. Don't add it back as a way to convert `eslint.config.mjs` to `.ts`. |
| Docker | Latest | Container runtime |

## Dependency Pinning & Guardrails

1. **Always pin exact versions.** No `^`/`~`/range prefixes in any `package.json`. No version ranges or `LATEST`/`RELEASE` in `pom.xml`. If you add a dependency, write the specific version you resolved, not a range.

2. **Shared JS/TS versions live in the pnpm catalog, not in each `package.json`.** `pnpm-workspace.yaml` has a `catalog:` block; `typescript` (used by the frontend and all three bots) is defined there once and referenced as `"typescript": "catalog:"`. If you add a new dependency that's used by more than one package in `apps/`, add it to the catalog instead of pinning the same version four times. The backend is a single Maven module, so this doesn't apply there — its versions live directly in `apps/backend/pom.xml`.

3. **A 7-day minimum release age is enforced — don't work around it.** `pnpm-workspace.yaml` sets `minimumReleaseAge: 10080` (minutes) with `minimumReleaseAgeStrict: true`, so `pnpm install`/`pnpm add` will hard-fail if a resolved version (direct or transitive) was published in the last 7 days. **This is expected behavior, not a bug**: if you hit `ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION`, pin the dependency to the next older version instead of lowering/removing `minimumReleaseAge`. Never edit `minimumReleaseAge`, `minimumReleaseAgeStrict`, or add entries to `minimumReleaseAgeExclude` to make a failing install pass, unless the user explicitly asks you to change the policy itself.

4. **Maven has no equivalent automatic gate**, so after editing `apps/backend/pom.xml` (adding or bumping any dependency or plugin), run the audit script before considering the task done — see [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md).

## Licensing

This project is headed toward a public, self-hosted release — every new dependency or tool should assume an external user needs to freely use, modify, and redistribute the resulting software, without having to think about it.

1. **Prefer permissive open-source licenses.** MIT, Apache 2.0, BSD (2-/3-clause), and ISC are all safe defaults — no reciprocal disclosure/redistribution obligations, no friction for commercial or closed-source use by whoever self-hosts this.
2. **Be cautious with copyleft licenses (GPL/LGPL/AGPL).** These can impose obligations on the whole application depending on how the library is linked/used — AGPL in particular extends its "conveying" trigger to network use, which matters directly for a self-hosted server product like this one. Don't pull one in as a direct dependency without flagging it for review first.
3. **Watch for dual-licensed / "open-core" tools.** Some libraries are free for certain contexts but require a paid commercial license for others — e.g. jOOQ is Apache 2.0 for open-source databases like PostgreSQL, but needs a commercial license for closed-source databases like Oracle or SQL Server. A currently-free dependency can flip to a paid one if a later infra choice changes what it's paired with — re-check when that happens.
4. **When choosing between comparable tools, all else equal, prefer the more permissively-licensed one**, and don't assume a popular tool is automatically fine — check.
5. This is a judgment call, not an automated gate (unlike [Dependency Pinning & Guardrails](#dependency-pinning--guardrails) above) — nothing in CI checks license compatibility, so it needs a deliberate look whenever something new is added.

## Shared Tooling Configs

TypeScript, ESLint, Prettier, and Vite configuration for `apps/frontend` and the three bots all come from `packages/config` (`@angora/config`) — see [`packages/config/README.md`](packages/config/README.md) for what's shared and why, and [`packages/config/AGENTS.md`](packages/config/AGENTS.md) for the editing rules. The short version: don't inline a rule/option in an app when the shared base already covers it or could cover it for more than one package, Prettier has exactly one config in the whole repo, and every app still needs its own direct `eslint`/`typescript` devDependency even though the config content comes from `@angora/config`.

## Common Tasks

### Add a new service

New API endpoints go in `apps/backend` — see [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md#common-tasks). Adding a whole new bot is documented in [`apps/bots/AGENTS.md`](apps/bots/AGENTS.md#common-tasks).

### Update dependencies

1. Edit `apps/backend/pom.xml` for backend, or the relevant `package.json` for frontend/bots — pin the exact new version, never a range
2. If the dependency is shared across frontend + bots, bump it once in the `catalog:` block of `pnpm-workspace.yaml` instead of each `package.json`
3. Use `-jvm` suffix for KTor artifacts
4. Verify versions are compatible
5. `pnpm install` — if it fails with `ERR_PNPM_MINIMUM_RELEASE_AGE_VIOLATION`, pick an older version (see Dependency Pinning & Guardrails above), don't relax the policy
6. For backend changes, run `node scripts/check-dependency-age.ts` and fix any violation the same way
7. Test with Docker build

## Security Notes

- Never commit secrets to the repository
- Use environment variables for sensitive data
- Database credentials are in docker-compose.yml (for development only)
- For production, once an Infisical project is configured, set `INFISICAL_ENABLED=true` and keep the real values there rather than in `.env.production` — see [Infisical](#infisical) above. `.env.production.example` ships with the flag off so a copied file still boots; flipping it on is a deliberate step, not a default

## Style Guidelines

- Match existing code style
- **No hardcoded literals** — each app keeps its strings, paths, and tuning values in its own constants module: backend `src/constants/Constants.kt`, frontend `src/strings.ts` (UI text) + `src/constants.ts` (config and templates). The rule applies to any change, not just to new features; see the Constants discipline sections in [`apps/backend/AGENTS.md`](apps/backend/AGENTS.md#constants-discipline) and [`apps/frontend/AGENTS.md`](apps/frontend/AGENTS.md) for what each covers
- Use Kotlin idiomatic patterns
- TypeScript: Use strict mode
- Docker: Use multi-stage builds where appropriate
- Documentation: Keep it concise and accurate — module-specific detail belongs in that module's own README/AGENTS.md, not duplicated here

## Contact

For questions about this project's agent configuration, refer to [README.md](README.md) or the relevant module's own `AGENTS.md`.
