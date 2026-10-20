import axios, { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { refreshSession } from './refresh'

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
      || excludedPaths.has(originalRequest.url ?? '')) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;
    
    try {
      await refreshSession();
      return apiClient(originalRequest);
    } catch (refreshError) {
      if (
        axios.isAxiosError(refreshError) &&
        refreshError.response?.status === 401
      ) {
        // Οριστική αποτυχία ανανέωσης.
        // Διατηρούμε το αρχικό 401, ώστε το useAuth()
        // να αναγνωρίζει τον χρήστη ως unauthenticated.
        return Promise.reject(error)
      }

      // Network error, timeout, 5xx, 403 κ.λπ.
      // Δεν τα μετατρέπουμε σε unauthenticated.
      return Promise.reject(refreshError)
        }
      }
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