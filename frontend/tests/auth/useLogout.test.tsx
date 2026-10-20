import { useEffect } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'

import { useLogout } from '../../src/features/auth/hooks/useLogout'
import { logout, type AuthUser } from '../../src/features/auth/api/auth.api'

vi.mock('../../src/features/auth/api/auth.api', () => ({
  logout: vi.fn().mockResolvedValue(undefined),
}))

describe('useLogout', () => {
  it('does not restore stale user data after logout', async () => {
    let completeRequest!: (user: AuthUser) => void

    const pendingRequest = new Promise<AuthUser>(resolve => {
      completeRequest = resolve
    })

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

    function useTestAuthQuery() {
      const query = useQuery({
        queryKey: ['auth', 'me'],
        queryFn: () => pendingRequest,
        retry: false,
      })

      useEffect(() => {
        // Το query ενεργοποιείται μέσω του useQuery.
      }, [])

      return query
    }

    const user = {
      id: 1,
      name: 'Noe',
      email: 'noe@example.com',
      createdAt: '2026-10-20T00:00:00Z',
    }

    const authQuery = renderHook(() => useTestAuthQuery(), { wrapper })
    const logoutHook = renderHook(() => useLogout(), { wrapper })

    await waitFor(() => {
      expect(authQuery.result.current.isPending).toBe(true)
    })

    logoutHook.result.current.mutate()

    await waitFor(() => {
      expect(logoutHook.result.current.isSuccess).toBe(true)
    })

    completeRequest(user)

    // Επιτρέπουμε να ολοκληρωθεί η παλιά Promise.
    await Promise.resolve()

    expect(queryClient.getQueryData(['auth', 'me'])).toBeNull()

    authQuery.unmount()
    logoutHook.unmount()
    queryClient.clear()
  })

  it('preserves user data when logout fails', async () => {
    const user = {
        id: 1,
        name: 'Noe',
        email: 'noe@example.com',
        createdAt: '2026-10-20T00:00:00Z',
    }

    const queryClient = new QueryClient({
        defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
        },
    })

    queryClient.setQueryData(['auth', 'me'], user)
    queryClient.setQueryData(['posts', 'my'], [{ id: 10 }])

    vi.mocked(logout).mockRejectedValueOnce(
        new Error('Network error')
    )

    function wrapper({ children }: { children: ReactNode }) {
        return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
        )
    }

    const { result, unmount } = renderHook(() => useLogout(), {
        wrapper,
    })

    await expect(
        result.current.mutateAsync()
    ).rejects.toThrow('Network error')

    expect(queryClient.getQueryData(['auth', 'me'])).toEqual(user)
    expect(queryClient.getQueryData(['posts', 'my'])).toEqual([
        { id: 10 },
    ])

    unmount()
    queryClient.clear()
    })
})