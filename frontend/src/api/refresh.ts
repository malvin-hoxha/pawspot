import axios from 'axios'

let refreshPromise: Promise<void> | null = null

function performRefresh(): Promise<void> {
    return axios.post('/api/auth/refresh', undefined, {
        withCredentials: true,
        withXSRFToken: true,
        xsrfCookieName: 'XSRF-TOKEN',
        xsrfHeaderName: 'X-XSRF-TOKEN',
        timeout: 10_000,
        },
    ).then(() => undefined);
}

export function refreshSession(): Promise<void> {
    if (refreshPromise !== null) {
        return refreshPromise;
    }

    refreshPromise = performRefresh().finally(() => {
        refreshPromise = null;
    });

    return refreshPromise;
}