import { apiClient } from '../../../api/client'
import { initializeCsrf } from '../../../api/csrf'
import { runLogout } from '../../../api/refresh'

export interface AuthUser {
    id: number
    name: string
    email: string
    createdAt: string
}

export interface LoginInput {
    email: string
    password: string
}

export interface RegisterInput extends LoginInput {
    name: string
}

interface LoginResponse {
  user: AuthUser
}

export async function login(input: LoginInput): Promise<AuthUser> {
    await initializeCsrf()
    const response = await apiClient.post<LoginResponse>('/auth/login', input);
    return response.data.user;
}

export async function register(input: RegisterInput): Promise<void> {
    await initializeCsrf();
    await apiClient.post('/auth/register', input);
}

export async function getCurrentUser(): Promise<AuthUser> {
    const response = await apiClient.get<AuthUser>('/auth/me');
    return response.data;
}

export async function logout(): Promise<void> {
  await runLogout(async () => {
    await initializeCsrf()
    await apiClient.post('/auth/logout')
  })
}