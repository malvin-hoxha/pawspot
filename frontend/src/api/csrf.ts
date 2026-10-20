import { apiClient } from './client'

export async function initializeCsrf(): Promise<void> {
  await apiClient.get('/auth/csrf')
}