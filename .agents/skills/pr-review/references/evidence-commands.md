# Evidence commands

Repo is `angoracloud/angora`; default branch `main`.

**`jq` is not installed** on the primary dev host. Use `gh`'s built-in `--jq` / `--template`
instead of piping to `jq`. Do not add a dependency on it.

**Do not run builds or test suites** (`mvn`, `pnpm build`, `pnpm test`, container builds). CI
runs them and a cold build costs minutes; read `gh pr checks` instead. The one exception is
`node scripts/check-dependency-age.ts` (§4).

## 1. Context

```bash
PR=52

gh pr view "$PR" --json number,title,body,headRefName,baseRefName,author,isDraft,url,\
additions,deletions,files,reviewDecision,mergeStateStatus

gh pr diff "$PR"                 # the review substrate
gh pr diff "$PR" --name-only     # drives the rule routing table
gh pr checks "$PR"               # the 3 required checks
gh pr view "$PR" --json comments --jq '.comments[] | "\(.author.login): \(.body)"'
gh pr view "$PR" --json reviews  --jq '.reviews[] | "\(.author.login) \(.state): \(.body)"'
```

Linked issue — use the same GraphQL mechanism the repo's own
`.github/workflows/pr-move-linked-issue-to-review.yml` uses, not a `Closes #` regex, so you match
what GitHub itself considers linked:

```bash
gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){
  pullRequest(number:$n){closingIssuesReferences(first:20){nodes{number title body}}}}}' \
  -f o=angoracloud -f r=angora -F n="$PR" \
  --jq '.data.repository.pullRequest.closingIssuesReferences.nodes[] | "#\(.number) \(.title)\n\(.body)"'
```

Branch mode (no PR). **Three dots** — two dots would review `main`'s own commits as if they were
the author's:

```bash
git fetch origin main
git diff --name-only origin/main...HEAD
git diff origin/main...HEAD
```

Merge-base staleness — drives the aggregation rule in `judgment-heuristics.md`:

```bash
BASE=$(git merge-base origin/main HEAD)
git rev-list --count "$BASE"..origin/main     # commits on main since this branch forked
git log -1 --format=%cr "$BASE"               # how long ago it forked
git log --oneline "$BASE"..origin/main        # what changed underneath it
```

## 2. Requirements

```bash
# Scope creep: which top-level areas the diff touches, vs. the issue's "Affected component"
gh pr diff "$PR" --name-only | cut -d/ -f1-2 | sort -u
```

Issues filed with `.github/ISSUE_TEMPLATE/new_feature.yml` have three parseable blocks in the
body: what is being built, the implementation approach, and an affected-component dropdown
(backend / frontend / slack-bot / discord-bot / email-bot). Use those as the criteria list.

## 3. Diff slicing

```bash
gh pr diff "$PR" | grep '^+' | grep -v '^+++'        # added lines only
git diff origin/main...HEAD -- '**/package.json' | grep '^+'
git diff --name-status origin/main...HEAD -- '*db/migration/*'   # M on an applied V*.sql = Blocker

# Documented intent — this repo is full of "don't do X, here's why". Check before calling
# something a mistake; an oddity with a written rationale is not a finding.
git log -S'<symbol>' --oneline -5
git log -1 --format='%H %s' -- <path>
```

Always read the **whole** changed file before reporting on a hunk.

## 4. Dependencies & licensing

Gate the whole lane on a manifest actually having changed:

```bash
gh pr diff "$PR" --name-only \
  | grep -E '(^|/)(package\.json|pnpm-workspace\.yaml|pom\.xml|Dockerfile)$|\.github/workflows/'
```

**npm** — resolve the real license; never infer it from the package name:

```bash
npm view <name>@<version> license repository.url
```

If that is empty, `UNKNOWN`, or `SEE LICENSE IN ...`, WebFetch the repository's LICENSE file and
read it.

**Maven** — fetch the POM from the raw repo. Maven Central's Solr index lags and misses recent
releases, so do not use `search.maven.org` (same lesson `scripts/check-dependency-age.ts`
encodes):

```bash
curl -s https://repo1.maven.org/maven2/<group/as/path>/<artifact>/<ver>/<artifact>-<ver>.pom \
  | grep -A3 '<licenses>'
```

Many POMs have no `<licenses>` block. Fallback ladder, in order: read the POM's `<parent>`
coordinates and repeat there → the project's GitHub LICENSE via WebFetch → report the license as
**undetermined** in a `Question` addressed to the author. Never a silent pass.

Also license-check what people forget:

```bash
grep -n '^FROM' <changed Dockerfile>                          # base images
grep -nE '^\s+uses:' <changed workflow>                       # actions
```

**Decision ladder** — apply root `AGENTS.md` §Licensing and cite the numbered rule you are using:

| Resolved license | Outcome |
|---|---|
| MIT, Apache-2.0, BSD-2/3, ISC | Pass. State it in one line so the check is visible. |
| GPL / LGPL / AGPL | **Blocker**, rule 2. AGPL carries extra weight — its network-conveying trigger bites a self-hosted server product. Precedent: `de.mkammerer:argon2-jvm` was rejected as LGPL-3.0 and replaced with BouncyCastle (MIT) — `apps/backend/AGENTS.md` §Authentication conventions rule 8. |
| Dual-licensed / open-core (BSL, SSPL, Elastic, "free for OSS databases") | **Blocker or Question**, rule 3, which gives the jOOQ example. |
| Unknown / custom / undeterminable | **Question**, rule 5 — this is a deliberate human judgment call precisely because CI does not check it. |

If a comparable, more permissively licensed alternative exists, name it (rule 4).

**Age guardrail.** Maven has no CI gate, so after any `pom.xml` dependency or plugin change run
the audit and report its exact output:

```bash
node scripts/check-dependency-age.ts     # or: pnpm run check:dep-age
```

For npm-side additions, `pnpm install --frozen-lockfile` in CI already enforces the 7-day
minimum — trust the green *Dependency age guardrail* check rather than running an install.

## 5. Security sweep

```bash
gh pr diff "$PR" | grep -inE '^\+.*(password|secret|token|api[_-]?key|BEGIN [A-Z ]*PRIVATE KEY)\s*[:=]'
gh pr diff "$PR" --name-only | grep -E '^\.env'      # .env is gitignored; a committed one is a Blocker
```

Then the documented auth invariants in [`rule-sources.md`](rule-sources.md) §Security.

## 6. Posting

Write the body to the scratchpad, never into the worktree — `git status` must stay clean:

```bash
gh pr comment "$PR" --body-file "$SCRATCH/pr-review-$PR.md"
```

`--body-file` avoids the shell mangling backticks and quotes in the report. Print the returned
comment URL afterwards.
