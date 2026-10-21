import axios, { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { isLogoutInProgress, refreshSession } from './refresh'
import { queryClient } from '../app/queryClient'
import { getAuthVersion, markAuthChanged } from './authVersion'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
  _authVersion?: string | null
}

export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 10_000,
});

const excludedPaths = new Set([
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/logout',
]);

apiClient.interceptors.request.use(config => {
  const request = config as RetryableRequestConfig

  if (request._authVersion === undefined) {
    request._authVersion = getAuthVersion()
  }

  return request
})

apiClient.interceptors.response.use(response => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as 
    | RetryableRequestConfig
    | undefined;

    if (error.response?.status !== 401 || !originalRequest || originalRequest._retry 
      || excludedPaths.has(originalRequest.url ?? '') || isLogoutInProgress()) {
      return Promise.reject(error);
    }

    // Η ταυτότητα άλλαξε πριν ξεκινήσει το refresh;
    if (originalRequest._authVersion !== getAuthVersion()) {
      return Promise.reject(error)
    }

    originalRequest._retry = true;
    
    try {
      await refreshSession()
    } catch (refreshError) {
      if (
        axios.isAxiosError(refreshError) &&
        refreshError.response?.status === 401
      ) {
        if (originalRequest._authVersion === getAuthVersion()) {
          queryClient.setQueryData(['auth', 'me'], null)
          queryClient.removeQueries({ queryKey: ['posts', 'my'] })

          markAuthChanged()
        }

        return Promise.reject(error)
      }

      // Για 500, network errors κ.λπ. επιστρέφουμε
      // το πραγματικό σφάλμα του refresh.
      return Promise.reject(refreshError)
    }

    // Μήπως ξεκίνησε logout όσο περιμέναμε το refresh;
    if (isLogoutInProgress() || originalRequest._authVersion !== getAuthVersion()) {
      return Promise.reject(error)
    }

    return apiClient(originalRequest)
  },
);

/*
GET /api/auth/me
       │
       ▼
    401 Unauthorized
       │
       ▼
Response interceptor
       │
       ▼
refreshSession()
       │
       ├── 200 → Retry GET /api/auth/me
       │
       └── 401 → Invalid authentication
*/