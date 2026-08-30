/**
 * Mirrors `BackendConstants.Infisical` in
 * `apps/backend/src/constants/Constants.kt`, and the literals in
 * `scripts/infisical-env.ts`. All three call the same API — change one, check
 * the others.
 */
export const INFISICAL_CONFIG = {
  /** Master switch. Anything but `'true'` leaves the env-only path in place. */
  ENABLED_ENV: 'INFISICAL_ENABLED',
  ENABLED_VALUE: 'true',

  DOMAIN_ENV: 'INFISICAL_DOMAIN',
  PROJECT_ID_ENV: 'INFISICAL_PROJECT_ID',
  ENVIRONMENT_ENV: 'INFISICAL_ENV',
  SECRET_PATH_ENV: 'INFISICAL_SECRET_PATH',
  CLIENT_ID_ENV: 'INFISICAL_CLIENT_ID',
  CLIENT_SECRET_ENV: 'INFISICAL_CLIENT_SECRET',
  /** Pre-issued access token. When set, the login call is skipped. */
  TOKEN_ENV: 'INFISICAL_TOKEN',

  /**
   * Infisical Cloud (EU). US Cloud is `https://app.infisical.com`; self-hosted
   * installs override this.
   */
  DEFAULT_DOMAIN: 'https://eu.infisical.com',
  DEFAULT_ENVIRONMENT: 'dev',
  DEFAULT_SECRET_PATH: '/',

  REQUEST_TIMEOUT_MS: 10_000,
} as const

export const INFISICAL_ROUTES = {
  UNIVERSAL_AUTH_LOGIN: '/api/v1/auth/universal-auth/login',
  LIST_SECRETS: '/api/v4/secrets',
} as const
