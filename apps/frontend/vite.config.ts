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

// React Compiler stays on the default `compilationMode: 'infer'` — don't switch
// to 'all'. Under 'all', babel-plugin-react-compiler@1.0.0 hits a BuildHIR bug on
// ordinary destructuring: 2+ defaulted params (`function F({ a = 1, b = 2 })`) or
// a rename-with-default (`const { data: foo = [] } = useQuery()`). It's non-fatal
// — the build succeeds, that function just isn't memoized — but 'infer' declines
// the shape outright instead of failing on it.
//
// Revisit when a release newer than 1.0.0 ships, or when the native Rust port
// lands. @swc/react-compiler is not that port: it only pre-checks eligibility and
// still delegates to this same Babel plugin, so it inherits the bug.
export default defineConfig(
  mergeConfig(base, {
    root: 'src',
    plugins: [react(), babel({ presets: [reactCompilerPreset()] })],
    server: {
      port: 3000,
      proxy: {
        // localhost, not the `backend` service name: that only resolves inside
        // the Docker network, which is what nginx.conf uses. `pnpm dev` runs on
        // the host and reaches the backend via its published port.
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
