import axios from 'axios'
import { withAuthLock } from './authLock'
import { markAuthChanged } from './authVersion';

const authHttp = axios.create({
  baseURL: '/api',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 10_000,
})

let refreshPromise: Promise<void> | null = null;
let logoutInProgress = false;

async function performRefresh(): Promise<void> {
  await withAuthLock(async () => {
    if (logoutInProgress) {
      throw new Error('Logout in progress')
    }

    try {
      await authHttp.get('/auth/me')
      return
    } catch (error) {
      if (
        !axios.isAxiosError(error) ||
        error.response?.status !== 401
      ) {
        throw error
      }
    }

    if (logoutInProgress) {
      throw new Error('Logout in progress')
    }

    await authHttp.get('/auth/csrf')
    await authHttp.post('/auth/refresh')
  })
}

export function refreshSession(): Promise<void> {

    if (logoutInProgress) {
        return Promise.reject(new Error('Logout in progress'))
    }

    if (refreshPromise !== null) {
        return refreshPromise;
    }

    refreshPromise = performRefresh().finally(() => {
        refreshPromise = null;
    });

    return refreshPromise;
}

export async function runLogout(
  logoutRequest: () => Promise<void>
): Promise<void> {
  if (logoutInProgress) {
    return
  }

  logoutInProgress = true

  try {
    if (refreshPromise !== null) {
      await refreshPromise.catch(() => undefined)
    }

    await withAuthLock(async () => {
      await logoutRequest()
      markAuthChanged()
    })

  } finally {
    logoutInProgress = false
  }
}

export function isLogoutInProgress(): boolean {
  return logoutInProgress
}