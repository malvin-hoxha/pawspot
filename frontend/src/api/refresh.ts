import axios from 'axios'

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
  await authHttp.get('/auth/csrf')
  await authHttp.post('/auth/refresh')
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

export async function runLogout(logoutRequest: () => Promise<void>): Promise<void> {
    if (logoutInProgress) {
        return
    }

    logoutInProgress = true;

    try {
        // Περιμένουμε το ενεργό refresh να ολοκληρωθεί,
        // είτε επιτυχημένα είτε αποτυχημένα.
        if (refreshPromise !== null) {
            await refreshPromise.catch(() => undefined)
        }

        await logoutRequest()
    } finally {
        logoutInProgress = false
  }
}

export function isLogoutInProgress(): boolean {
  return logoutInProgress
}