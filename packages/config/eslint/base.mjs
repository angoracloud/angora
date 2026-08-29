import js from '@eslint/js'
import tseslint from 'typescript-eslint'

// Shared by every package. react.mjs and node.mjs add their own globals and
// plugins on top, then append eslint-config-prettier last.
//
// Stays plain JS, not .ts: loading a TypeScript ESLint config needs jiti, and
// adding it surfaced an unconditional crash in typescript-eslint against this
// repo's TypeScript 7. See ../README.md.
/** @type {import('eslint').Linter.Config[]} */
export const base = [
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
]

export default base
