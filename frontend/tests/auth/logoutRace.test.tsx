import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'

import { useCurrentUser } from '../../src/features/auth/hooks/useCurrentUser'
import { useLogout } from '../../src/features/auth/hooks/useLogout'
import { getCurrentUser, logout } from '../../src/features/auth/api/auth.api'

import { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { useAuth } from '../../src/features/auth/hooks/useAuth'

vi.mock('../../src/features/auth/api/auth.api', () => ({
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}))

describe('Logout authentication race', () => {
  it('does not restore a user from a request started during logout', async () => {
    let completeLogout!: () => void
    let completeMe!: (user: {
      id: number
      name: string
      email: string
      createdAt: string
    }) => void

    const logoutGate = new Promise<void>(resolve => {
      completeLogout = resolve
    })

    const meGate = new Promise<{
      id: number
      name: string
      email: string
      createdAt: string
    }>(resolve => {
      completeMe = resolve
    })

    vi.mocked(logout).mockReturnValue(logoutGate)
    vi.mocked(getCurrentUser).mockReturnValue(meGate)

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

    const logoutHook = renderHook(() => useLogout(), { wrapper })

    // Ξεκινά logout, αλλά το backend response καθυστερεί.
    act(() => {
      logoutHook.result.current.mutate()
    })

    await waitFor(() => {
      expect(logout).toHaveBeenCalledOnce()
    })

    // Νέο component ζητά τον current user ενώ γίνεται logout.
    const authHook = renderHook(() => useCurrentUser(), { wrapper })

    await waitFor(() => {
      expect(getCurrentUser).toHaveBeenCalledOnce()
    })

    // Το logout ολοκληρώνεται πρώτο.
    await act(async () => {
      completeLogout()
      await logoutGate
    })

    await waitFor(() => {
      expect(logoutHook.result.current.isSuccess).toBe(true)
    })

    // Το παλιό /me response φτάνει μετά το logout.
    await act(async () => {
      completeMe({
        id: 1,
        name: 'Noe',
        email: 'noe@example.com',
        createdAt: '2026-10-20T00:00:00Z',
      })
      await meGate
    })

    expect(queryClient.getQueryData(['auth', 'me'])).toBeNull()

    authHook.unmount()
    logoutHook.unmount()
    queryClient.clear()
  })

  it('keeps the user unauthenticated after a new session check', async () => {
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

    vi.mocked(logout).mockResolvedValue(undefined)

    const unauthorized = new AxiosError(
        'Authentication required',
        'ERR_BAD_REQUEST',
        undefined,
        undefined,
        {
        status: 401,
        statusText: 'Unauthorized',
        data: { message: 'Authentication required' },
        headers: {},
        config: {} as InternalAxiosRequestConfig,
        },
    )

    vi.mocked(getCurrentUser).mockRejectedValue(unauthorized)

    const logoutHook = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
        await logoutHook.result.current.mutateAsync()
    })

    expect(queryClient.getQueryData(['auth', 'me'])).toBeNull()

    const authHook = renderHook(() => useAuth(), { wrapper })

    await waitFor(() => {
        expect(authHook.result.current.isUnauthenticated).toBe(true)
    })

    expect(authHook.result.current.isAuthenticated).toBe(false)
    expect(authHook.result.current.user).toBeNull()
    expect(authHook.result.current.isUnauthenticated).toBe(true)

    authHook.unmount()
    logoutHook.unmount()
    queryClient.clear()
    })
})