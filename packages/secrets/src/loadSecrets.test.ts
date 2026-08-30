import { describe, expect, it, vi } from 'vitest'
import { loadSecrets } from './loadSecrets.js'
import { INFISICAL_CONFIG, INFISICAL_ROUTES } from './constants.js'
import type { EnvSource } from './infisical.js'

const PROJECT_ID = 'proj-123'

function enabledEnv(extra: EnvSource = {}): EnvSource {
  return {
    [INFISICAL_CONFIG.ENABLED_ENV]: INFISICAL_CONFIG.ENABLED_VALUE,
    [INFISICAL_CONFIG.PROJECT_ID_ENV]: PROJECT_ID,
    [INFISICAL_CONFIG.TOKEN_ENV]: 'pre-issued-token',
    ...extra,
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function secretsFetch(secrets: Record<string, string>) {
  return vi.fn(async () =>
    jsonResponse({
      secrets: Object.entries(secrets).map(([secretKey, secretValue]) => ({
        secretKey,
        secretValue,
      })),
    }),
  )
}

describe('loadSecrets with Infisical disabled', () => {
  it('reads straight from the environment and never calls out', async () => {
    const fetchImpl = vi.fn()
    const secrets = await loadSecrets({ TOKEN: 'from-env' }, fetchImpl)

    expect(secrets.get('TOKEN')).toBe('from-env')
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('returns the fallback for a name the environment does not define', async () => {
    const secrets = await loadSecrets({})

    expect(secrets.get('MISSING')).toBeUndefined()
    expect(secrets.get('MISSING', 'default')).toBe('default')
  })

  it('treats any value other than "true" as disabled', async () => {
    const fetchImpl = vi.fn()
    const secrets = await loadSecrets(
      { [INFISICAL_CONFIG.ENABLED_ENV]: '1', TOKEN: 'from-env' },
      fetchImpl,
    )

    expect(secrets.get('TOKEN')).toBe('from-env')
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})

describe('loadSecrets with Infisical enabled', () => {
  it('prefers an Infisical value over the same name in the environment', async () => {
    const fetchImpl = secretsFetch({ TOKEN: 'from-infisical' })
    const secrets = await loadSecrets(
      enabledEnv({ TOKEN: 'from-env' }),
      fetchImpl,
    )

    expect(secrets.get('TOKEN')).toBe('from-infisical')
  })

  it('falls back to the environment for a name Infisical does not define', async () => {
    const fetchImpl = secretsFetch({ TOKEN: 'from-infisical' })
    const secrets = await loadSecrets(
      enabledEnv({ OTHER: 'only-in-env' }),
      fetchImpl,
    )

    expect(secrets.get('OTHER')).toBe('only-in-env')
  })

  it('skips the login call when a pre-issued token is configured', async () => {
    const fetchImpl = secretsFetch({ TOKEN: 'from-infisical' })
    await loadSecrets(enabledEnv(), fetchImpl)

    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(String(fetchImpl.mock.calls[0]?.[0])).toContain(
      INFISICAL_ROUTES.LIST_SECRETS,
    )
  })

  it('logs in with client credentials and uses the returned access token', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'issued-token' }))
      .mockResolvedValueOnce(
        jsonResponse({
          secrets: [{ secretKey: 'TOKEN', secretValue: 'from-infisical' }],
        }),
      )

    const env = enabledEnv({
      [INFISICAL_CONFIG.TOKEN_ENV]: undefined,
      [INFISICAL_CONFIG.CLIENT_ID_ENV]: 'client-id',
      [INFISICAL_CONFIG.CLIENT_SECRET_ENV]: 'client-secret',
    })
    const secrets = await loadSecrets(env, fetchImpl)

    expect(secrets.get('TOKEN')).toBe('from-infisical')
    expect(String(fetchImpl.mock.calls[0]?.[0])).toContain(
      INFISICAL_ROUTES.UNIVERSAL_AUTH_LOGIN,
    )
    const listHeaders = (fetchImpl.mock.calls[1]?.[1] as RequestInit).headers
    expect(listHeaders).toMatchObject({ Authorization: 'Bearer issued-token' })
  })

  it('targets the configured project, environment and path', async () => {
    const fetchImpl = secretsFetch({})
    await loadSecrets(
      enabledEnv({
        [INFISICAL_CONFIG.ENVIRONMENT_ENV]: 'prod',
        [INFISICAL_CONFIG.SECRET_PATH_ENV]: '/backend',
      }),
      fetchImpl,
    )

    const url = new URL(String(fetchImpl.mock.calls[0]?.[0]))
    expect(url.origin).toBe(INFISICAL_CONFIG.DEFAULT_DOMAIN)
    expect(url.searchParams.get('projectId')).toBe(PROJECT_ID)
    expect(url.searchParams.get('environment')).toBe('prod')
    expect(url.searchParams.get('secretPath')).toBe('/backend')
  })

  it('honors a self-hosted domain, trailing slash and all', async () => {
    const fetchImpl = secretsFetch({})
    await loadSecrets(
      enabledEnv({
        [INFISICAL_CONFIG.DOMAIN_ENV]: 'https://infisical.internal/',
      }),
      fetchImpl,
    )

    expect(String(fetchImpl.mock.calls[0]?.[0])).toContain(
      `https://infisical.internal${INFISICAL_ROUTES.LIST_SECRETS}`,
    )
  })
})

describe('loadSecrets failure handling', () => {
  it('rejects instead of falling back when the project id is missing', async () => {
    await expect(
      loadSecrets(
        { [INFISICAL_CONFIG.ENABLED_ENV]: INFISICAL_CONFIG.ENABLED_VALUE },
        vi.fn(),
      ),
    ).rejects.toThrow(INFISICAL_CONFIG.PROJECT_ID_ENV)
  })

  it('rejects when neither a token nor client credentials are configured', async () => {
    await expect(
      loadSecrets(
        enabledEnv({ [INFISICAL_CONFIG.TOKEN_ENV]: undefined }),
        vi.fn(),
      ),
    ).rejects.toThrow(INFISICAL_CONFIG.CLIENT_ID_ENV)
  })

  it('rejects when the secrets request fails, without serving env values', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ message: 'nope' }, 403))

    await expect(
      loadSecrets(enabledEnv({ TOKEN: 'from-env' }), fetchImpl),
    ).rejects.toThrow('403')
  })

  it('rejects when the login request fails', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({}, 401))

    await expect(
      loadSecrets(
        enabledEnv({
          [INFISICAL_CONFIG.TOKEN_ENV]: undefined,
          [INFISICAL_CONFIG.CLIENT_ID_ENV]: 'client-id',
          [INFISICAL_CONFIG.CLIENT_SECRET_ENV]: 'client-secret',
        }),
        fetchImpl,
      ),
    ).rejects.toThrow('401')
  })

  it('rejects a response with no secrets array', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ unexpected: true }))

    await expect(loadSecrets(enabledEnv(), fetchImpl)).rejects.toThrow(
      /secrets array/,
    )
  })
})
