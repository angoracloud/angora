import { INFISICAL_CONFIG, INFISICAL_ROUTES } from './constants.js'

/** Injectable for tests; defaults to Node's global `fetch`. */
export type FetchLike = typeof globalThis.fetch

/** The subset of `process.env` this package reads. */
export type EnvSource = Record<string, string | undefined>

interface InfisicalConfig {
  domain: string
  projectId: string
  environment: string
  secretPath: string
  clientId?: string
  clientSecret?: string
  token?: string
}

export function isInfisicalEnabled(env: EnvSource): boolean {
  return env[INFISICAL_CONFIG.ENABLED_ENV] === INFISICAL_CONFIG.ENABLED_VALUE
}

function trimmed(env: EnvSource, name: string): string | undefined {
  return env[name]?.trim() || undefined
}

/**
 * Builds the connection config, throwing on anything missing. Only reached when
 * Infisical is enabled, so an incomplete configuration is an operator error.
 */
function resolveConfig(env: EnvSource): InfisicalConfig {
  const projectId = trimmed(env, INFISICAL_CONFIG.PROJECT_ID_ENV)
  if (!projectId) {
    throw new Error(
      `${INFISICAL_CONFIG.ENABLED_ENV} is set but ${INFISICAL_CONFIG.PROJECT_ID_ENV} is missing`,
    )
  }

  const token = trimmed(env, INFISICAL_CONFIG.TOKEN_ENV)
  const clientId = trimmed(env, INFISICAL_CONFIG.CLIENT_ID_ENV)
  const clientSecret = trimmed(env, INFISICAL_CONFIG.CLIENT_SECRET_ENV)
  if (!token && !(clientId && clientSecret)) {
    throw new Error(
      `${INFISICAL_CONFIG.ENABLED_ENV} is set but no credentials were provided — set ${INFISICAL_CONFIG.TOKEN_ENV}, or both ${INFISICAL_CONFIG.CLIENT_ID_ENV} and ${INFISICAL_CONFIG.CLIENT_SECRET_ENV}`,
    )
  }

  const domain =
    trimmed(env, INFISICAL_CONFIG.DOMAIN_ENV) ?? INFISICAL_CONFIG.DEFAULT_DOMAIN

  return {
    domain: domain.replace(/\/+$/, ''),
    projectId,
    environment:
      trimmed(env, INFISICAL_CONFIG.ENVIRONMENT_ENV) ??
      INFISICAL_CONFIG.DEFAULT_ENVIRONMENT,
    secretPath:
      trimmed(env, INFISICAL_CONFIG.SECRET_PATH_ENV) ??
      INFISICAL_CONFIG.DEFAULT_SECRET_PATH,
    clientId,
    clientSecret,
    token,
  }
}

async function failure(what: string, res: Response): Promise<Error> {
  const body = await res.text().catch(() => '')
  return new Error(`${what} (${res.status}${body ? `: ${body}` : ''})`)
}

function request(): RequestInit {
  return { signal: AbortSignal.timeout(INFISICAL_CONFIG.REQUEST_TIMEOUT_MS) }
}

async function login(
  config: InfisicalConfig,
  fetchImpl: FetchLike,
): Promise<string> {
  const res = await fetchImpl(
    `${config.domain}${INFISICAL_ROUTES.UNIVERSAL_AUTH_LOGIN}`,
    {
      ...request(),
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        clientId: config.clientId,
        clientSecret: config.clientSecret,
      }),
    },
  )
  if (!res.ok) throw await failure('Infisical login failed', res)

  const { accessToken } = (await res.json()) as { accessToken?: string }
  if (!accessToken) {
    throw new Error('Infisical login response did not contain an accessToken')
  }
  return accessToken
}

export async function fetchInfisicalSecrets(
  env: EnvSource,
  fetchImpl: FetchLike = globalThis.fetch,
): Promise<Record<string, string>> {
  const config = resolveConfig(env)
  const token = config.token ?? (await login(config, fetchImpl))

  const url = new URL(`${config.domain}${INFISICAL_ROUTES.LIST_SECRETS}`)
  url.searchParams.set('projectId', config.projectId)
  url.searchParams.set('environment', config.environment)
  url.searchParams.set('secretPath', config.secretPath)

  const res = await fetchImpl(url.toString(), {
    ...request(),
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok)
    throw await failure('Fetching secrets from Infisical failed', res)

  const { secrets } = (await res.json()) as {
    secrets?: { secretKey?: string; secretValue?: string }[]
  }
  if (!Array.isArray(secrets)) {
    throw new Error('Infisical response did not contain a secrets array')
  }

  return Object.fromEntries(
    secrets
      .filter(
        (s) =>
          typeof s.secretKey === 'string' && typeof s.secretValue === 'string',
      )
      .map((s) => [s.secretKey, s.secretValue] as [string, string]),
  )
}
