#!/usr/bin/env node
// Writes an env file docker-compose can read, so `${VAR}` interpolation in
// docker-compose.yml resolves from Infisical instead of `.env`.
//
// This exists for one reason: `postgres` runs a third-party image, so no code of
// ours can feed it POSTGRES_PASSWORD — and that variable is only read at initdb,
// on the first start against an empty volume. Compose interpolation happens on
// the host before any container starts, so covering Postgres means getting the
// values in here. Every other service is covered twice over: once through this
// file, and once in-process via @angora/secrets / the backend's SecretsProvider.
//
// Usage:
//   node scripts/infisical-env.ts [--out=.env.infisical]
//   docker-compose --env-file .env.infisical up -d --build
//
// Self-contained rather than importing @angora/secrets: Node runs .ts directly
// (same as check-dependency-age.ts), but it won't resolve a `.js` specifier to a
// `.ts` file, which is what that package's nodenext imports use. Keep them in
// sync — the API shape is the same.

import { rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)))

const DEFAULT_OUT = '.env.infisical'

/**
 * The output file holds secrets in plaintext, and `.gitignore` keeps it out of
 * history via the `.env` / `.env.*` block. That rule is filename-based, so an
 * `--out=` pointing anywhere else (`secrets.env`, `compose.env`) would be
 * committable — require the name to stay inside what the rule covers.
 */
const OUT_PREFIX = '.env'

/**
 * A POSIX environment variable name. Compose's env-file parser takes the rest of
 * the line as the value, so a key containing a newline would inject additional
 * variables — see `render`.
 */
const VALID_KEY = /^[A-Za-z_][A-Za-z0-9_]*$/

// Infisical Cloud (EU). US Cloud is https://app.infisical.com; self-hosted
// installs override this. Mirrors BackendConstants.Infisical.DEFAULT_DOMAIN and
// INFISICAL_CONFIG.DEFAULT_DOMAIN in packages/secrets/src/constants.ts.
const DEFAULT_DOMAIN = 'https://eu.infisical.com'
const DEFAULT_ENVIRONMENT = 'dev'
const DEFAULT_SECRET_PATH = '/'
const LOGIN_PATH = '/api/v1/auth/universal-auth/login'
const SECRETS_PATH = '/api/v4/secrets'
const REQUEST_TIMEOUT_MS = 10_000

function env(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function fail(message: string): never {
  console.error(`[infisical-env] ${message}`)
  process.exit(1)
}

async function detail(res: Response): Promise<string> {
  const body = await res.text().catch(() => '')
  return body ? `${res.status}: ${body}` : `${res.status}`
}

function reason(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

async function accessToken(domain: string): Promise<string> {
  const preIssued = env('INFISICAL_TOKEN')
  if (preIssued) return preIssued

  const clientId = env('INFISICAL_CLIENT_ID')
  const clientSecret = env('INFISICAL_CLIENT_SECRET')
  if (!clientId || !clientSecret) {
    fail(
      'No credentials provided — set INFISICAL_TOKEN, or both INFISICAL_CLIENT_ID and INFISICAL_CLIENT_SECRET',
    )
  }

  const res = await fetch(`${domain}${LOGIN_PATH}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ clientId, clientSecret }),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
  if (!res.ok) fail(`Universal Auth login failed (${await detail(res)})`)

  const body = (await res.json()) as { accessToken?: string }
  if (!body.accessToken) fail('Login response did not contain an accessToken')
  return body.accessToken
}

async function fetchSecrets(): Promise<Record<string, string>> {
  const domain = (env('INFISICAL_DOMAIN') ?? DEFAULT_DOMAIN).replace(/\/+$/, '')
  const projectId = env('INFISICAL_PROJECT_ID')
  if (!projectId) fail('INFISICAL_PROJECT_ID is required')

  const token = await accessToken(domain)

  const url = new URL(`${domain}${SECRETS_PATH}`)
  url.searchParams.set('projectId', projectId)
  url.searchParams.set(
    'environment',
    env('INFISICAL_ENV') ?? DEFAULT_ENVIRONMENT,
  )
  url.searchParams.set(
    'secretPath',
    env('INFISICAL_SECRET_PATH') ?? DEFAULT_SECRET_PATH,
  )

  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
  if (!res.ok) fail(`Fetching secrets failed (${await detail(res)})`)

  const body = (await res.json()) as {
    secrets?: { secretKey?: string; secretValue?: string }[]
  }
  if (!Array.isArray(body.secrets)) {
    fail('Response did not contain a secrets array')
  }

  const resolved: Record<string, string> = {}
  for (const { secretKey, secretValue } of body.secrets) {
    if (typeof secretKey === 'string' && typeof secretValue === 'string') {
      resolved[secretKey] = secretValue
    }
  }
  return resolved
}

/**
 * docker-compose reads this file itself, and its parser takes the whole rest of
 * the line as the value — no shell quoting, no escapes.
 *
 * That makes both halves of a `KEY=VALUE` line untrusted input. A value
 * containing a newline can't be represented at all, and a *key* containing one
 * would inject further lines: a secret named `A\nPOSTGRES_PASSWORD=x` writes a
 * second variable that overrides the compose default. Whoever can name a secret
 * in the project would otherwise be able to set any variable in the file — so
 * reject both rather than emitting something that parses as more than it says.
 */
function render(secrets: Record<string, string>): string {
  const lines = [
    '# Generated by scripts/infisical-env.ts — do not edit or commit.',
  ]
  for (const [key, value] of Object.entries(secrets).sort()) {
    if (!VALID_KEY.test(key)) {
      fail(
        `Secret name ${JSON.stringify(key)} is not a valid environment variable ` +
          `name (letters, digits and underscore, not starting with a digit)`,
      )
    }
    if (value.includes('\n')) {
      fail(
        `Secret ${key} contains a newline, which a compose env file cannot carry`,
      )
    }
    lines.push(`${key}=${value}`)
  }
  return lines.join('\n') + '\n'
}

// `slice`, not `split('=')[1]`: a path may legitimately contain '=', and
// splitting would silently write the secrets to a truncated path instead.
const outArg = process.argv.find((a) => a.startsWith('--out='))
const outPath = path.resolve(
  ROOT,
  outArg?.slice('--out='.length) || DEFAULT_OUT,
)

if (!path.basename(outPath).startsWith(OUT_PREFIX)) {
  fail(
    `--out must name a file starting with "${OUT_PREFIX}" (got ` +
      `"${path.basename(outPath)}"). Anything else falls outside the .gitignore ` +
      `rule that keeps this plaintext secrets file out of the repository.`,
  )
}

// A network failure here would otherwise surface as a bare `TypeError: fetch
// failed` stack trace, which says nothing about what an operator should check.
// Scoped to the fetch alone: a failure to write the file below is a different
// problem and gets its own message, rather than being blamed on the network.
const secrets = await fetchSecrets().catch((err: unknown) =>
  fail(
    `Could not reach Infisical at ${env('INFISICAL_DOMAIN') ?? DEFAULT_DOMAIN} — ${reason(err)}`,
  ),
)

const contents = render(secrets)
const relative = path.relative(ROOT, outPath)

// Remove first, then create exclusively. Two reasons, both about the fact that
// this file holds secrets in plaintext:
//
//   - `mode` is only honored when the file is created, so writing over an
//     existing file would leave whatever permissions it already had.
//   - a plain write follows symlinks. A symlink pre-planted at this path would
//     redirect the secrets to its target; `wx` (O_CREAT|O_EXCL) refuses a
//     symlink outright, so the unlink-then-create pair can't be steered.
try {
  await rm(outPath, { force: true })
  await writeFile(outPath, contents, { flag: 'wx', mode: 0o600 })
} catch (err) {
  fail(`Could not write ${relative} — ${reason(err)}`)
}

console.log(
  `[infisical-env] Wrote ${Object.keys(secrets).length} secret(s) to ${relative}`,
)
console.log(
  `[infisical-env] Run: docker-compose --env-file ${relative} up -d --build`,
)
