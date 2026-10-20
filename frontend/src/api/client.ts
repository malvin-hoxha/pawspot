import axios, { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { isLogoutInProgress, refreshSession } from './refresh'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
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

apiClient.interceptors.response.use(response => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as 
    | RetryableRequestConfig
    | undefined;

    if (error.response?.status !== 401 || !originalRequest || originalRequest._retry 
      || excludedPaths.has(originalRequest.url ?? '') || isLogoutInProgress()) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;
    
    try {
      await refreshSession()
    } catch (refreshError) {
      if (
        axios.isAxiosError(refreshError) &&
        refreshError.response?.status === 401
      ) {
        return Promise.reject(error)
      }

      return Promise.reject(refreshError)
    }

    // Μήπως ξεκίνησε logout όσο περιμέναμε το refresh;
    if (isLogoutInProgress()) {
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