import { apiClient } from '../../../api/client'
import { initializeCsrf } from '../../../api/csrf'
import { withAuthLock } from '../../../api/authLock'
import { markAuthChanged } from '../../../api/authVersion'
import { refreshSession, runLogout } from '../../../api/refresh'

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
  return withAuthLock(async () => {
    await initializeCsrf()

    const response = await apiClient.post<LoginResponse>(
      '/auth/login',
      input,
    )

    // Τα cookies του νέου login έχουν παραληφθεί.
    // Ενημερώνουμε το generation πριν αφήσουμε το Web Lock.
    markAuthChanged()

    return response.data.user
  })
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

export async function deleteAccount(password:string):Promise<void> {
  // Βεβαιωνόμαστε ότι υπάρχει έγκυρη συνεδρία πριν τη διαγραφή.
  await refreshSession()

  // Όσο γίνεται η διαγραφή, ένα 401 για λάθος κωδικό
  // δεν πρέπει να ξεκινήσει νέο refresh.
  await runLogout(async () => {
    await initializeCsrf();
    await apiClient.delete('/users/me', {
      data: { password },
    });
  });
}