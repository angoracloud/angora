import {
  fetchInfisicalSecrets,
  isInfisicalEnabled,
  type EnvSource,
  type FetchLike,
} from './infisical.js'

/**
 * Read-only view over the resolved secrets for one service. Callers never branch
 * on where a value came from.
 */
export interface SecretsProvider {
  get(name: string): string | undefined
  get(name: string, fallback: string): string
}

function providerFrom(
  lookup: (name: string) => string | undefined,
): SecretsProvider {
  function get(name: string): string | undefined
  function get(name: string, fallback: string): string
  function get(name: string, fallback?: string): string | undefined {
    return lookup(name) ?? fallback
  }
  return { get }
}

/**
 * Resolves this service's secrets once, at startup.
 *
 * With `INFISICAL_ENABLED` unset this is just `process.env`. With it set,
 * Infisical values win and the environment stays the fallback for names the
 * project doesn't define.
 *
 * Rejects if Infisical is enabled but unreachable, and callers are expected to
 * exit. Serving environment values instead would let a service boot on the
 * angora/angora credentials in docker-compose.yml and look healthy.
 *
 * Read once — rotating a secret needs a restart.
 */
export async function loadSecrets(
  env: EnvSource = process.env,
  fetchImpl?: FetchLike,
): Promise<SecretsProvider> {
  if (!isInfisicalEnabled(env)) return providerFrom((name) => env[name])

  const secrets = await fetchInfisicalSecrets(env, fetchImpl)
  return providerFrom((name) => secrets[name] ?? env[name])
}
