import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

import { useLogin } from '../../src/features/auth/hooks/useLogin'
import { useLogout } from '../../src/features/auth/hooks/useLogout'
import { login, logout } from '../../src/features/auth/api/auth.api'

vi.mock('../../src/features/auth/api/auth.api', () => ({
  login: vi.fn(),
  logout: vi.fn(),
}))

describe('Authentication cache lifecycle', () => {
  it('replaces the previous user after logout and another login', async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })

    const userA = {
      id: 1,
      name: 'User A',
      email: 'a@example.com',
      createdAt: '2026-10-20T00:00:00Z',
    }

    const userB = {
      id: 2,
      name: 'User B',
      email: 'b@example.com',
      createdAt: '2026-10-20T00:00:00Z',
    }

    vi.mocked(login)
      .mockResolvedValueOnce(userA)
      .mockResolvedValueOnce(userB)

    vi.mocked(logout).mockResolvedValue(undefined)

    function wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      )
    }

    const loginHook = renderHook(() => useLogin(), { wrapper })
    const logoutHook = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await loginHook.result.current.mutateAsync({
        email: 'a@example.com',
        password: 'Password123!',
      })
    })

    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(userA)

    await act(async () => {
      await logoutHook.result.current.mutateAsync()
    })

    expect(queryClient.getQueryData(['auth', 'me'])).toBeNull()

    await act(async () => {
      await loginHook.result.current.mutateAsync({
        email: 'b@example.com',
        password: 'Password123!',
      })
    })

    await waitFor(() => {
      expect(queryClient.getQueryData(['auth', 'me'])).toEqual(userB)
    })

    loginHook.unmount()
    logoutHook.unmount()
    queryClient.clear()
  })
})