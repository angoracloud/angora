---
name: pr-review
description: >-
  Full-spectrum review of a pull request or branch in this repo — correctness, requirements vs.
  the linked issue, test quality, readability, dependency licensing and pinning, security, and
  conformance with AGENTS.md/README rules — producing a severity-ranked findings report, then
  optionally posting it as a single PR comment after explicit confirmation. Use when asked to
  review a PR, review a branch before merge, check a diff against repo conventions, do a
  pre-merge check, or when given a PR number or URL to look over.
allowed-tools: Read, Grep, Glob, Bash, WebFetch
---

# PR Review

> **This procedure is READ-ONLY.** Never edit, create, or delete a file in the repo. Never
> `git commit`, `git push`, `gh pr review`, `gh pr merge`, or resolve a conversation. The only
> write action permitted is posting **one** PR comment, and only after the user says yes in
> Step 11. The `allowed-tools` header enforces part of this in Claude Code; agents that ignore
> that header are still bound by this paragraph.

This skill is vendor-neutral. Its canonical home is `.agents/skills/pr-review/`;
`.claude/skills/pr-review` is a symlink to it. If you are reading this through a symlink that
did not resolve (you got a one-line file containing a path), open that path instead.

## Step 0 — Resolve the target

| Argument | Mode |
|---|---|
| number (`52`) or PR URL | PR mode |
| branch name | branch mode against `origin/main` |
| nothing | branch mode on the current branch |

A draft PR, an empty diff, or a PR whose CI has not run yet → report **Cannot review** and say
which. Do not review `main` against itself.

## Step 1 — Gather context

Commands in [`references/evidence-commands.md`](references/evidence-commands.md) §1. Collect: PR
metadata, the **three-dot** diff, the changed-path list, `gh pr checks`, existing comments, the
linked issue (via GraphQL `closingIssuesReferences`, not a `Closes #` regex), and merge-base
staleness.

**Do not run builds or test suites.** `mvn`, `pnpm`, and a container runtime may exist on the
host, but CI already runs them and a cold Maven/container build costs minutes. Build, lint,
format, typecheck, and test signal all come from `gh pr checks`. The single exception is
`node scripts/check-dependency-age.ts` (read-only, seconds) when `pom.xml` changed — see Step 6.
Whatever you did not execute goes in the report's *Coverage & limits* section.

## Step 2 — Load the rules that apply

**Never review from memory of this repo's rules.** They change often. Read root
[`AGENTS.md`](../../../AGENTS.md), then follow the routing table in
[`references/rule-sources.md`](references/rule-sources.md) to open only the module `AGENTS.md`
and README sections the changed paths call for. README sections are first-class rule sources,
not background reading.

If the diff itself touches an `AGENTS.md` or `README.md`, the **PR's version** of that file is
the rules-of-record, and the doc change gets reviewed for accuracy against the code beside it.

## Step 3 — Requirements met

Read the linked issue. Build an explicit checklist: each stated criterion → **Met** (with
`file:line` evidence) / **Partially** (name what is missing) / **Not met** / **Not statically
verifiable** (say why). No linked issue at all → Major; the PR template requires `Closes #` and
the in-review automation silently no-ops without it. Compare the PR body's "What changed and
why" against the actual diff — undisclosed behavior changes are a finding. Scope-creep probe and
issue-template parsing: `references/evidence-commands.md` §2.

## Step 4 — Conformance

Work the routing table in `references/rule-sources.md` for each changed path. Every finding here
must quote the rule it rests on as `file §section` — **no quote, no finding** (downgrade to
`Question` or drop it). That is what keeps this skill honest as the docs move.

## Step 5 — Correctness

Diff-scoped. A correctness finding needs a concrete failure scenario: inputs → wrong
output/state. If you cannot construct one, drop it. If the diff exceeds ~300 changed lines of
logic, or touches `apps/backend/src/{service,routes}` or a migration, **suggest** `/code-review`
as a deeper pass in the report — do not invoke it yourself.

## Step 6 — Dependencies & licensing

Run only if a `package.json`, `pnpm-workspace.yaml`, `pom.xml`, `Dockerfile`, or workflow
changed. Nothing in CI checks license compatibility, so this lane is the one that catches what
would otherwise merge green. Extract **added** dependency lines only, resolve each artifact's
real license (npm registry / raw `repo1.maven.org` POM — never guess from the package name), and
apply the ladder in `references/evidence-commands.md` §4, quoting the numbered rule from root
`AGENTS.md` §Licensing. Also license-check what people forget: Docker base images, GitHub
Actions, vendored code. Run `node scripts/check-dependency-age.ts` if `pom.xml` changed.

## Step 7 — Tests meaningful

The seven probes in
[`references/judgment-heuristics.md`](references/judgment-heuristics.md) §Tests. The core
question is never "are there tests" but **"which assertion fails if I delete the new code?"**
Calibrate to repo reality: the backend has real Testcontainers coverage; the frontend and bots
have only placeholders and no test infrastructure, so untested UI is Info, not Major.

## Step 8 — Readability

`references/judgment-heuristics.md` §Readability. Max **5** readability findings per PR, ranked;
never a Blocker. Say nothing about formatting or import order — Prettier and ESLint own that and
CI enforces it.

## Step 9 — Security & secrets

Cheap and high-value, because this repo's security invariants are already written down. Grep the
added lines for credentials and committed `.env` files, then check the documented auth
invariants listed in `references/rule-sources.md` §Security. For an auth-touching diff, suggest
`/security-review` as the deeper pass.

## Step 10 — Verify every candidate finding

Apply all seven gates in `references/judgment-heuristics.md` §Verification gates before anything
reaches the report. The two that catch the most false positives:

- **Read the whole changed file, not just the hunk** — hunks hide the import, the guard clause,
  the existing null check.
- **Check CI first.** If your claim is "this will not compile / lint fails / a test fails" and
  `gh pr checks` is green, you are wrong. Re-examine or drop it.

Scope to the diff. Pre-existing problems get **one aggregated Info line**, never per-instance
findings. When the merge-base is old and conventions moved since, emit **one** Major naming the
drift and what to rebase onto — not ten separate convention violations the author cannot act on.

## Step 11 — Report, then offer to post

Format, severity definitions, verdict mapping, and the posting flow are in
[`references/report-and-post.md`](references/report-and-post.md).

Print the report in the terminal. Then, if the target is a PR, print the **exact rendered comment
body** and ask one yes/no question. Only on an explicit yes, post it as a single comment with
`gh pr comment --body-file`. Never `gh pr review` in any form: `main` requires one human
approval, and an agent that could approve would hollow out that control.
