export const AUTH_LOCK = 'pawspot-auth'

export function withAuthLock<T>(
  operation: () => Promise<T>,
): Promise<T> {
  return navigator.locks.request(AUTH_LOCK, operation)
}
