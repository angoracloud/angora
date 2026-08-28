# Rule sources — routing table

This file contains **pointers and detection commands, never restated rules.** The rules live in
`AGENTS.md`/`README.md` and change often; a copy here would go stale silently and you would
assert a wrong rule with confidence. A grep is not a rule — it surfaces *candidates* that you
then adjudicate by reading the live doc.

Read root [`AGENTS.md`](../../../../AGENTS.md) for every review. Then open only the rows below
whose paths appear in the diff.

Note on citation accuracy: some rules are `## headings`, others are **bold bullets** in a file's
intro list. Cite what actually exists — e.g. the frontend constants rule is the
*"Constants discipline"* bullet, not a section. Quote the sentence you are relying on.

## Backend — `apps/backend/`

| Changed path | Read | Probes |
|---|---|---|
| `src/routes/**` | backend `AGENTS.md` §Forbidden changes, §Authentication conventions, §Constants discipline | `grep -n 'Repository' <file>` — routes must reach services only; `grep -n '\bauthenticate(' <file>`; route paths must come from `BackendConstants.Routes`, never a string at the `route(...)` call |
| `src/**/*.kt` | backend `AGENTS.md` §Constants discipline | `grep -nE '"[^"]{3,}"' <file>` — then **subtract** SLF4J `{}` templates and internal invariant exception messages, which that section explicitly exempts |
| `src/service/**`, `src/repository/**` | backend `AGENTS.md` §Forbidden changes; `apps/backend/README.md` §Architecture (N-Tier) | layering intact; `src/Dependencies.kt` still exposes only services |
| `src/main/resources/db/migration/**` | backend `AGENTS.md` §Common tasks → *Add a new migration* | `git diff --name-status origin/main...HEAD -- '*db/migration/*'` — any `M` on an already-applied `V*.sql` is a **Blocker**; `ls` the dir for `V{n}` collisions; require a matching `src/Tables.kt` change in the same diff; a new `updated_at` column should reuse the shared `set_updated_at()` trigger rather than redefining it |
| `src/error/**`, `src/dto/ErrorDto.kt` | `apps/backend/README.md` §Error Handling & Request Logging | every error path goes through `call.respondError(...)`/`ApiException`; nothing escapes the documented envelope |
| `src/main/resources/application.yaml` | backend `AGENTS.md` intro (Config bullet), §Forbidden changes | extension is `.yaml`; a port change must also land in `docker-compose.yml` |
| `pom.xml` | root §Version Constraints, §Dependency Pinning & Guardrails, §Licensing; backend §Dependencies | Ktor artifacts carry the `-jvm` suffix; `grep -n '<version>' pom.xml \| grep -viE '\$\{\|LATEST\|RELEASE'` for literal pins; shade `<filters>` still scoped to `org.bouncycastle:*` (not widened to `*:*`); `ServicesResourceTransformer` still present |
| `Dockerfile` | backend `AGENTS.md` §Forbidden changes | line number of `dependency:go-offline` must be **less than** that of `COPY src ./src`; no `-o` added to the `package` step |
| `test/kotlin/**` | backend `AGENTS.md` §Common tasks → *Add a repository test* | extends `cloud.angora.testsupport.PostgresRepositoryTest`; **not** annotated `@Testcontainers`/`@Container`; has its own `@AfterEach` `deleteAll()`; lives in `test/kotlin/`, never `src/test/kotlin` |

## Frontend — `apps/frontend/`

`apps/frontend/AGENTS.md` states most of its rules as bold bullets in the intro list rather than
under headings. Cite the bullet by its lead phrase.

| Changed path | Read | Probes |
|---|---|---|
| `src/**/*.tsx` | *"Constants discipline"* bullet | literal text in JSX, and in `placeholder` / `aria-label` / `title`. The bullet splits the destination three ways — static screen text → `src/strings.ts` (`<SCREEN>_STRINGS`), parameterized templates and config → `src/constants.ts`, paths → `src/routes.ts`. Before reporting, `grep -rn '<WORD>' src/strings.ts src/constants.ts` — the bullet also requires **reusing** an existing constant rather than retyping the same word |
| `src/**/*.tsx` | *"React Compiler is enabled"* bullet | `grep -n 'useMemo\|useCallback\|React\.memo' <file>` — hand-written memoization needs a profiled, documented reason; also flag a switch to `compilationMode: 'all'` |
| `src/hooks/**`, `src/services/**` | *"Data fetching"* bullet | `grep -n 'setInterval\|addEventListener(.focus\|isMounted' <file>` — hand-rolled polling/focus-refetch belongs in TanStack Query; the pattern lives in `src/hooks/discordQueries.ts` |
| `src/router.tsx`, `src/routes.ts` | *"Routing"* bullet | a non-root `createRoute({ path: ... })` must use `ROUTE_SEGMENTS.*`, never a `ROUTES.*` full path (double-prefix bug) and never a function call (widens the literal type and degrades every `Link to=`) |
| `src/index.css`, any non-module `.css` | *"Design system"* bullet | new global class selectors; components must be `Name.tsx` + `Name.module.css` pairs referenced via `styles.foo` |
| any `.ts`/`.tsx` | *"The `@/*` path alias is not wired into Vite"* bullet | `grep -n "from '@/" <file>` — type-checks but 404s at runtime |
| `vite.config.ts`, `Dockerfile`, `nginx.conf` | frontend §Forbidden changes | port 3000 unchanged; `/api` proxy intact; `nginx.conf` still present |
| `package.json` version | *"Sidebar version marker"* bullet | version bumps belong in `package.json`, not typed into `Sidebar.tsx` |

## Bots — `apps/bots/`

| Changed path | Read | Probes |
|---|---|---|
| `*/src/**` | bots `AGENTS.md` §Forbidden changes | the `node:http` `/health` server stays unconditional and answers **GET and HEAD**; ports unchanged (discord 3001, slack 3002, email 3003) |
| `*/package.json`, `*/Dockerfile` | bots `AGENTS.md` §Forbidden changes | start command still `node dist/index.js`; `"type": "module"` retained |
| a whole new bot directory | bots `AGENTS.md` §Common tasks → *Add a new bot* | walk that numbered list as a checklist; also add the new manifest to the `manifests` list in `scripts/check-dependency-age.ts` |

## Shared config — `packages/config/`

| Changed path | Read | Probes |
|---|---|---|
| `eslint/**` | config `AGENTS.md` §Forbidden changes | files stay `.mjs` |
| `package.json` | config `AGENTS.md` §Forbidden changes | `typescript` stays pinned there rather than moving to `catalog:`; `grep -rn 'jiti' --include=package.json .` must stay empty |

## Repo-wide

| Changed path | Read | Probes |
|---|---|---|
| `**/package.json`, `pnpm-workspace.yaml` | root §Dependency Pinning & Guardrails; `README.md` §Dependency Management | `grep -nE '":\s*"[\^~]'` → **Blocker**; a version now used by >1 package but not `catalog:` → Major; **any** diff to `minimumReleaseAge`, `minimumReleaseAgeStrict`, or `minimumReleaseAgeExclude` → **Blocker** unless changing that policy is the PR's stated purpose |
| `docker-compose.yml` | root §Infrastructure Files, §Environment Variables | for each added `${VAR:-...}`: `grep -n '^VAR=' .env.example .env.production.example` — both must be updated |
| `.github/workflows/**` | root §CI, hooks, and branch protection | `grep -nE 'uses: .*@(v[0-9]+\|main\|master)$'` — third-party actions are SHA-pinned |
| any pinned version bump | root §Version Constraints | that table is hand-maintained; a bump whose artifact has a row there and was not updated → Major |
| `AGENTS.md`, `README.md` | the file itself | review the doc change for accuracy against the code in the same diff, and treat the PR's version as the rules-of-record for the rest of the review |

## Docs-sync checks

The PR template's *"Docs (README/AGENTS.md) updated if behavior or setup changed"* checkbox, made
mechanical:

- new env var → `.env.example` **and** root `AGENTS.md` §Environment Variables
- new/changed endpoint → `apps/backend/README.md` §API Endpoints
- new bot → the numbered list in `apps/bots/AGENTS.md`
- new workspace manifest → the `manifests` list in `scripts/check-dependency-age.ts`
- new dependency → root `AGENTS.md` §Version Constraints table, if the artifact has a row

## Security

Documented invariants worth a direct check on any auth-adjacent diff. All live in
`apps/backend/AGENTS.md` §Authentication conventions (numbered 1–8) and §Constants discipline —
read them, quote the number you are citing:

- every failed-login path still answers identically (rule 1) — an added "helpful" message is a Blocker
- `PasswordService.dummyVerify()` still on the unknown-email path (rule 2)
- Argon2id for passwords / SHA-256 for tokens, not swapped (rule 3)
- new routes wrapped in `authenticate(...)` with `call.requireUser()` (rule 4)
- `AngoraSession` still opaque and `@Serializable` (rule 5)
- no `anyHost()` in CORS (rule 6)
- shade `<filters>` kept and still scoped to `org.bouncycastle:*` (rule 7)
- a new crypto/signed dependency's license checked (rule 8)
- `Errors.LoginFailureReasons` values are log-only and must never reach an `ApiException`
  (§Constants discipline, final paragraph)

Plus the generic sweep in [`evidence-commands.md`](evidence-commands.md) §5.
