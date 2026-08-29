#!/usr/bin/env node
// Writes an env file for `docker-compose --env-file`, so `${VAR}` interpolation
// resolves from Infisical instead of `.env`.
//
// Exists for postgres: it's a third-party image that reads POSTGRES_PASSWORD at
// initdb, before our code runs, so compose's own interpolation has to be fed.
// Every other service is also covered in-process by @angora/secrets.
//
// Run this once, to initialize the volume. Afterwards POSTGRES_PASSWORD is
// ignored and plain `docker-compose up` is correct.
//
// Usage:
//   node scripts/infisical-env.ts [--out=.env.infisical]
//   docker-compose --env-file .env.infisical up -d --build
//
// Duplicates the two calls in packages/secrets/src/infisical.ts because Node
// won't resolve that package's `.js` specifiers to `.ts` files. Keep them in sync.

import { rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)))

const DEFAULT_OUT = '.env.infisical'

/**
 * `.gitignore` keeps this plaintext secrets file out of history by filename, via
 * its `.env` / `.env.*` block. An `--out=secrets.env` would be committable, so
 * require the name to stay inside that rule.
 */
const OUT_PREFIX = '.env'

/** A POSIX environment variable name. See `render` for why this is enforced. */
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
 * Compose's env-file parser takes the whole rest of the line as the value, with
 * no quoting or escapes, so both halves of `KEY=VALUE` are untrusted input.
 *
 * A newline in a value can't be represented. A newline in a *key* injects extra
 * lines: a secret named `A\nPOSTGRES_PASSWORD=x` sets a second variable. Reject
 * both so the file never parses as more than it says.
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

// Scoped to the fetch, so a write failure below isn't blamed on the network.
// Otherwise this surfaces as a bare `TypeError: fetch failed`.
const secrets = await fetchSecrets().catch((err: unknown) =>
  fail(
    `Could not reach Infisical at ${env('INFISICAL_DOMAIN') ?? DEFAULT_DOMAIN} — ${reason(err)}`,
  ),
)

const contents = render(secrets)
const relative = path.relative(ROOT, outPath)

// Remove, then create exclusively. `mode` is only honored on creation, so writing
// over an existing file would keep its old permissions. And a plain write follows
// symlinks: `wx` (O_CREAT|O_EXCL) refuses one, so a symlink pre-planted at this
// path can't redirect the secrets to its target.
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
