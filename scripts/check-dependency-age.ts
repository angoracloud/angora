#!/usr/bin/env node
// Supply-chain guardrail for the one ecosystem pnpm's `minimumReleaseAge`
// can't reach: Maven. Also cross-checks the pnpm/npm side as a second line
// of defense. Fails (exit 1) if any pinned dependency or plugin version was
// published more recently than MIN_AGE_DAYS (default 7).
//
// Usage: node scripts/check-dependency-age.ts [--min-age-days=7]

import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)))

const minAgeDaysArg = process.argv.find((a) => a.startsWith('--min-age-days='))
const MIN_AGE_DAYS = Number(
  minAgeDaysArg?.split('=')[1] ?? process.env.MIN_AGE_DAYS ?? 7,
)
const MIN_AGE_MS = MIN_AGE_DAYS * 24 * 60 * 60 * 1000

interface Dep {
  ecosystem: 'maven' | 'npm'
  source: string
  name: string
  version: string
}

interface Violation extends Dep {
  publishedAt: Date
  ageDays: string
}

interface Unverifiable extends Dep {
  error?: string
}

// ---------- Maven (pom.xml) ----------

async function collectMavenDeps(): Promise<Dep[]> {
  const pomPath = path.join(ROOT, 'apps/backend/pom.xml')
  const xml = await readFile(pomPath, 'utf8')

  const properties: Record<string, string> = {}
  const propsBlock =
    xml.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] ?? ''
  for (const m of propsBlock.matchAll(/<([\w.-]+)>([^<]*)<\/\1>/g)) {
    properties[m[1]] = m[2]
  }
  const resolve = (v: string) =>
    v.replace(/\$\{([\w.-]+)\}/g, (_, key) => properties[key] ?? '')

  const deps: Dep[] = []
  // Matches both <dependency>...</dependency> and <plugin>...</plugin> blocks.
  for (const block of xml.matchAll(/<(dependency|plugin)>([\s\S]*?)<\/\1>/g)) {
    const body = block[2]
    const groupId = body.match(/<groupId>([^<]+)<\/groupId>/)?.[1]
    const artifactId = body.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]
    const rawVersion = body.match(/<version>([^<]+)<\/version>/)?.[1]
    if (!groupId || !artifactId || !rawVersion) continue
    if (groupId === 'cloud.angora') continue // our own artifact, not an external dep
    deps.push({
      ecosystem: 'maven',
      source: 'apps/backend/pom.xml',
      name: `${groupId}:${artifactId}`,
      version: resolve(rawVersion),
    })
  }
  return deps
}

async function mavenPublishDate(
  name: string,
  version: string,
): Promise<Date | null> {
  // search.maven.org's Solr index lags/misses recent releases; the raw
  // repository's Last-Modified header on the POM is the ground truth for
  // when an artifact was actually published.
  const [g, a] = name.split(':')
  const gpath = g.replace(/\./g, '/')
  const url = `https://repo1.maven.org/maven2/${gpath}/${a}/${version}/${a}-${version}.pom`
  const res = await fetch(url, { method: 'HEAD' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const lastModified = res.headers.get('last-modified')
  return lastModified ? new Date(lastModified) : null
}

// ---------- npm / pnpm (package.json + catalog) ----------

async function readCatalog(): Promise<Record<string, string>> {
  const yaml = await readFile(path.join(ROOT, 'pnpm-workspace.yaml'), 'utf8')
  const block = yaml.match(/^catalog:\n((?:[ \t]+.+\n?)*)/m)?.[1] ?? ''
  const catalog: Record<string, string> = {}
  for (const line of block.split('\n')) {
    const m = line.match(/^\s+([\w@/.-]+):\s*(.+)$/)
    if (m) catalog[m[1]] = m[2].trim()
  }
  return catalog
}

async function collectNpmDeps(): Promise<Dep[]> {
  const catalog = await readCatalog()
  // Every package.json in the pnpm workspace, plus the root manifest (which
  // isn't a workspace "package" but still gets installed and can carry its
  // own dependencies, e.g. prettier).
  const manifests = [
    'package.json',
    'packages/config/package.json',
    'packages/secrets/package.json',
    'apps/frontend/package.json',
    'apps/bots/slack/package.json',
    'apps/bots/discord/package.json',
    'apps/bots/email/package.json',
  ]

  const deps = new Map<string, Dep>()
  for (const rel of manifests) {
    const pkg = JSON.parse(await readFile(path.join(ROOT, rel), 'utf8')) as {
      dependencies?: Record<string, string>
      devDependencies?: Record<string, string>
    }
    for (const group of ['dependencies', 'devDependencies'] as const) {
      for (const [name, spec] of Object.entries(pkg[group] ?? {})) {
        if (spec.startsWith('workspace:')) continue
        const version = spec.startsWith('catalog:') ? catalog[name] : spec
        if (!version) continue
        deps.set(`${name}@${version}`, {
          ecosystem: 'npm',
          source: rel,
          name,
          version,
        })
      }
    }
  }
  return [...deps.values()]
}

async function npmPublishDate(
  name: string,
): Promise<{ time?: Record<string, string> }> {
  const encoded = name.startsWith('@') ? name.replace('/', '%2f') : name
  const res = await fetch(`https://registry.npmjs.org/${encoded}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<{ time?: Record<string, string> }>
}

// ---------- main ----------

async function main() {
  const [mavenDeps, npmDeps] = await Promise.all([
    collectMavenDeps(),
    collectNpmDeps(),
  ])
  const all = [...mavenDeps, ...npmDeps]
  const now = Date.now()

  const violations: Violation[] = []
  const unverifiable: Unverifiable[] = []

  const npmTimeCache = new Map<string, ReturnType<typeof npmPublishDate>>()

  for (const dep of all) {
    try {
      let publishedAt: Date | null
      if (dep.ecosystem === 'maven') {
        publishedAt = await mavenPublishDate(dep.name, dep.version)
      } else {
        if (!npmTimeCache.has(dep.name)) {
          npmTimeCache.set(dep.name, npmPublishDate(dep.name))
        }
        const doc = await npmTimeCache.get(dep.name)!
        const iso = doc.time?.[dep.version]
        publishedAt = iso ? new Date(iso) : null
      }

      if (!publishedAt) {
        unverifiable.push(dep)
        continue
      }

      const ageMs = now - publishedAt.getTime()
      if (ageMs < MIN_AGE_MS) {
        violations.push({
          ...dep,
          publishedAt,
          ageDays: (ageMs / (24 * 60 * 60 * 1000)).toFixed(1),
        })
      }
    } catch (err) {
      unverifiable.push({ ...dep, error: (err as Error).message })
    }
  }

  console.log(
    `Checked ${all.length} pinned dependencies/plugins (Maven + npm) for a minimum age of ${MIN_AGE_DAYS} days.\n`,
  )

  if (unverifiable.length) {
    console.log(
      `Could not verify publish date for ${unverifiable.length} package(s) (skipped, not treated as failures):`,
    )
    for (const d of unverifiable) {
      console.log(
        `  - [${d.ecosystem}] ${d.name}@${d.version} (${d.source})${d.error ? ` — ${d.error}` : ''}`,
      )
    }
    console.log('')
  }

  if (violations.length) {
    console.error(
      `FAIL: ${violations.length} package(s) are newer than ${MIN_AGE_DAYS} days old:`,
    )
    for (const v of violations) {
      console.error(
        `  - [${v.ecosystem}] ${v.name}@${v.version} (${v.source}) — published ${v.publishedAt.toISOString()} (${v.ageDays}d ago)`,
      )
    }
    process.exitCode = 1
    return
  }

  console.log(
    `OK: all verifiable pinned versions are at least ${MIN_AGE_DAYS} days old.`,
  )
}

main().catch((err) => {
  console.error('check-dependency-age failed to run:', err)
  process.exitCode = 1
})
