import {
  fetchInfisicalSecrets,
  isInfisicalEnabled,
  type EnvSource,
  type FetchLike,
} from './infisical.js'

/** Read-only view over the resolved secrets. Callers never branch on the source. */
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
 * Resolves this service's secrets once, at startup: Infisical when enabled,
 * otherwise `process.env`. Rotating a secret needs a restart.
 *
 * Rejects if Infisical is enabled but unreachable, and callers exit; serving
 * environment values would boot the service on the dev credentials in
 * `docker-compose.yml`.
 */
export async function loadSecrets(
  env: EnvSource = process.env,
  fetchImpl?: FetchLike,
): Promise<SecretsProvider> {
  if (!isInfisicalEnabled(env)) return providerFrom((name) => env[name])

  const secrets = await fetchInfisicalSecrets(env, fetchImpl)
  return providerFrom((name) => secrets[name] ?? env[name])
}
