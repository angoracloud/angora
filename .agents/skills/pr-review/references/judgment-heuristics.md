# Judgment heuristics

The two dimensions that degrade into useless advice without a rubric, plus the gates every
finding must clear.

## Tests

The question is never "are there tests." It is **"which assertion fails if I delete the new
code?"** Seven probes:

**1. The deletion test.** For each new behavior in the diff — a route, a service method, each new
`if`/`when` branch, each new error path — name the added lines and ask which existing or new
assertion goes red if you empty them. If none does, that is the finding. Name the specific
untested branch (`DiscordServiceImpl.softDelete` lines 47–52), never "this needs more tests."

**2. Assertion strength.** Flag tests whose only assertions are `assertNotNull`,
`assertTrue(true)`, `assertDoesNotThrow`, or `expect(x).toBeDefined()`. A test that asserts
nothing about *values* asserts nothing.

**3. Mutation sanity.** Would the test still pass with a comparison inverted, a filter dropped, or
a constant changed? The highest-yield instance in this repo: a soft-delete test that only asserts
the row got a `deleted_at` still passes when the *list* query forgets `deletedAt IS NULL`. Demand
the negative case — after the delete, the list no longer returns it.

**4. Fixture realism.** Backend repository tests must extend
`cloud.angora.testsupport.PostgresRepositoryTest` and run real queries against the real
Flyway-migrated schema — a new test that mocks the repository defeats the point, per
`apps/backend/AGENTS.md` §Common tasks → *Add a repository test*. That section also requires a
per-test `@AfterEach` `deleteAll()` for isolation, and forbids annotating the subclass
`@Testcontainers`/`@Container`.

**5. Placeholder detection.** A new `*.test.ts` that imports nothing from `src/` is not coverage.
If the PR body claims tests were added and they are placeholders, that gap is the finding.

**6. Security-invariant tests.** For an auth-adjacent diff, look for a test asserting the
*identical* 401 / `invalid_credentials` body across the unknown-email and wrong-password paths.
That invariant is load-bearing (it is what stops the endpoint being an account-enumeration
oracle) and it is cheaply testable, so its absence is a real gap rather than a nit.

**7. Calibrate to repo reality.** The backend has genuine Testcontainers coverage. The frontend
and all three bots have only `src/placeholder.test.ts` and **no test infrastructure at all** —
`README.md` §Limitations says so outright. Demanding React component tests on every frontend PR
is noise. So: for frontend/bot changes, note the untested slice at **Info** citing that section,
and escalate to Major only when the PR introduces genuinely testable non-UI logic (a pure
function in `services/`, a template in `constants.ts`) or the linked issue asked for tests.

## Readability

Report only what you can name a concrete fix for. Triggers:

- A new function over ~50 lines, or over 3 levels of nesting, in **added** code.
- **A name that contradicts behavior** — a `deleteServer` that soft-deletes. Propose the rename
  or the clarifying doc line.
- A boolean or positional parameter whose meaning is unreadable at the call site.
- A block duplicated 3+ times within the diff, or duplicating a helper that already exists —
  **cite the existing helper's path, or drop the finding.**
- A comment that restates the code, or that is now stale relative to the line beneath it.
- Dead code, an unused export, or an abstraction introduced with exactly one caller "for future
  use."
- An inline literal that belongs in a constants module. Report this **once**, as a conformance
  finding, not twice.

Do not report:

- Formatting, spacing, or import order. Prettier and ESLint own those and CI enforces them; a
  style comment is either already handled or a disagreement with the shared config, which belongs
  in `packages/config`, not a PR comment.
- Preference-only renames ("I'd call this `x`").
- "Consider extracting a helper" without naming the extraction and its call sites.
- Anything in a file whose diff is only whitespace or import reordering.

**Cap: 5 readability findings per PR**, ranked by value. Readability is never a Blocker.

## Verification gates

A candidate finding must clear **all seven** or be downgraded to `Question` or dropped.

1. **Read the whole changed file**, not just the hunk. Hunks hide the import, the guard clause,
   the existing null check, the comment explaining why.
2. **Grep before claiming absence.** "Constant X doesn't exist", "this duplicates nothing",
   "there's no test for it" — each needs `grep -rn` evidence you actually ran.
3. **Conformance findings need a quote** from the live doc, as `file §section`. Cannot find one →
   `Question`, or drop. This is also what surfaces doc drift as "I couldn't find a rule" instead
   of a confident falsehood.
4. **Correctness findings need a failure scenario**: concrete inputs → concrete wrong
   output/state. Cannot construct one → drop.
5. **Check CI first.** If the claim is "won't compile / lints badly / a test fails" and
   `gh pr checks` is green, you are wrong. Re-examine or drop.
6. **Check for documented intent.** This repo is unusually full of "don't do X, here's why."
   `git log -S'<symbol>' --oneline -5` and read the surrounding comments. An oddity with a written
   rationale is not a finding.
7. **Don't invent requirements.** If it is not in the linked issue, `AGENTS.md`, or `README.md`,
   it is a `Question` at most.

## Scoping

- Always three-dot (`origin/main...HEAD` or `gh pr diff`). Two-dot reviews `main`'s own commits.
- Report only on `+` lines and the behavior they change.
- Pre-existing problems in touched files get **one aggregated Info line** — "3 pre-existing issues
  in the touched files, out of scope; say the word and I'll list them" — never per-instance
  findings. Sole exception: pre-existing code the diff *newly depends on* in a broken way.

## The stale-branch aggregation rule

When the merge-base is old and conventions moved underneath the branch, do **not** emit ten
separate convention violations. Emit **one** Major:

> This branch forked N commits / ~M weeks behind `main`. Since then `main` migrated frontend data
> fetching to TanStack Query (`src/hooks/discordQueries.ts`) and UI text to `src/strings.ts`.
> `useDiscordServers.ts` no longer exists on `main`. Rebase before per-file review is useful; the
> findings below assume post-rebase code.

Every one of those ten findings is technically true and none is actionable — the author's fix is a
single rebase. A wall of them buries the findings that survive it.
