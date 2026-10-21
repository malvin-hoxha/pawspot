import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import {
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'

import { useCurrentUser } from '../../src/features/auth/hooks/useCurrentUser'
import { useLogin } from '../../src/features/auth/hooks/useLogin'
import {
  getCurrentUser,
  login,
} from '../../src/features/auth/api/auth.api'

import type { AuthUser } from '../../src/features/auth/api/auth.api'

vi.mock('../../src/features/auth/api/auth.api', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
}))

describe('Login identity race', () => {
  it('does not allow an old /auth/me response to overwrite a newer login', async () => {
    const userA: AuthUser = {
      id: 1,
      name: 'User A',
      email: 'a@example.com',
      createdAt: '2026-10-21T00:00:00Z',
    }

    const userB: AuthUser = {
      id: 2,
      name: 'User B',
      email: 'b@example.com',
      createdAt: '2026-10-21T00:00:00Z',
    }

    let completeOldRequest!: (user: AuthUser) => void

    const oldRequest = new Promise<AuthUser>(resolve => {
      completeOldRequest = resolve
    })

    vi.mocked(getCurrentUser).mockReturnValue(oldRequest)
    vi.mocked(login).mockResolvedValue(userB)

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })

    function wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      )
    }

    // 1. Ξεκινά παλιό /auth/me.
    const authHook = renderHook(() => useCurrentUser(), {
      wrapper,
    })

    await waitFor(() => {
      expect(getCurrentUser).toHaveBeenCalledOnce()
    })

    // 2. Πριν επιστρέψει, γίνεται login ως User B.
    const loginHook = renderHook(() => useLogin(), {
      wrapper,
    })

    await act(async () => {
      await loginHook.result.current.mutateAsync({
        email: userB.email,
        password: 'Password123!',
      })
    })

    expect(
      queryClient.getQueryData(['auth', 'me'])
    ).toEqual(userB)

    // 3. Το παλιό /auth/me επιστρέφει User A.
    await act(async () => {
      completeOldRequest(userA)
      await oldRequest
    })

    // 4. Η νέα ταυτότητα πρέπει να διατηρηθεί.
    expect(
      queryClient.getQueryData(['auth', 'me'])
    ).toEqual(userB)

    authHook.unmount()
    loginHook.unmount()
    queryClient.clear()
  })
})