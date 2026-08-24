import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { defineConfig, mergeConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { base } from '@angora/config/vite/base'

const pkg = JSON.parse(
  readFileSync(new URL('./package.json', import.meta.url), 'utf-8'),
) as { version: string }

// In CI/Docker builds .git isn't present in the build context (see
// .dockerignore), so the Dockerfile passes the real commit as a GIT_SHA
// build arg/env var instead. Locally (pnpm dev/build), fall back to asking
// git directly.
function resolveCommitHash(): string {
  if (process.env.GIT_SHA) return process.env.GIT_SHA
  try {
    return execSync('git rev-parse --short HEAD').toString().trim()
  } catch {
    return 'unknown'
  }
}

// React Compiler stays on the default `compilationMode: 'infer'` — DO NOT
// switch to 'all'. Forcing 'all' hits a real bug in
// babel-plugin-react-compiler@1.0.0 (BuildHIR::lowerAssignment, category
// "Todo") on ordinary destructuring: 2+ defaulted params in one destructured
// function signature (e.g. `function F({ a = 1, b = 2 })`), or a
// rename-with-default (e.g. `const { data: foo = [] } = useQuery()`).
// Confirmed non-fatal even under 'all' — `pnpm build` still succeeds, the
// affected function just doesn't get memoized — but 'infer' avoids
// triggering it at all: its own eligibility check declines this exact shape
// rather than attempting and failing on it (verified empirically, not just
// per the docs).
//
// Revisit if either of these change:
//   - A babel-plugin-react-compiler release newer than 1.0.0 ships:
//     `npm view babel-plugin-react-compiler dist-tags`.
//   - The native Rust port of the compiler lands (in progress upstream,
//     with SWC/Oxc compatibility layers). @swc/react-compiler exists today
//     but is NOT that port — per its maintainer it only does a fast
//     eligibility pre-check and still delegates the actual transform to
//     this same babel-plugin-react-compiler code, so it inherits this bug
//     rather than avoiding it.
export default defineConfig(
  mergeConfig(base, {
    root: 'src',
    plugins: [react(), babel({ presets: [reactCompilerPreset()] })],
    server: {
      port: 3000,
      proxy: {
        // 'backend' is the Docker Compose service name and only resolves
        // inside the Docker network (that's what nginx.conf uses in the
        // production container). `pnpm dev` runs on the host, where the
        // backend is reachable via its published port instead.
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: '../dist',
    },
    define: {
      __APP_VERSION__: JSON.stringify(pkg.version),
      __COMMIT_HASH__: JSON.stringify(resolveCommitHash()),
    },
  }),
)
