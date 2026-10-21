import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { apiClient } from '../../src/api/client'
import { initializeCsrf } from '../../src/api/csrf'
import { refreshSession, runLogout } from '../../src/api/refresh'
import { queryClient } from '../../src/app/queryClient'
import { markAuthChanged } from '../../src/api/authVersion'



let refreshCount = 0
let sessionRefreshed = false

const server = setupServer(
  http.get('/api/auth/me', () => {
    if (!sessionRefreshed) {
      return new HttpResponse(null, { status: 401 })
    }

    return HttpResponse.json({ id: 1, name: 'Test User' })
  }),

  http.get('/api/posts/my', () => {
    if (!sessionRefreshed) {
      return new HttpResponse(null, { status: 401 })
    }

    return HttpResponse.json({ content: [] })
  }),

  http.post('/api/auth/refresh', async () => {
    refreshCount++

    // Κρατάμε το refresh ενεργό ώστε τα δύο
    // requests να περιμένουν την ίδια Promise.
    await new Promise(resolve => setTimeout(resolve, 50))

    sessionRefreshed = true
    return new HttpResponse(null, { status: 200 })
  }),

  http.get('/api/auth/csrf', () => {
    return HttpResponse.json({ token: 'test-csrf-token' })
  }),

  http.post('/api/auth/logout', () =>
    new HttpResponse(null, { status: 204 })
  ),
)

beforeEach(() => {
  refreshCount = 0
  sessionRefreshed = false
  server.listen({ onUnhandledFrame: 'error' })
  vi.stubGlobal('navigator', {
    ...navigator,
    locks: {
      request: async (
        _name: string,
        callback: () => Promise<void>,
      ) => callback(),
    },
  })
})

afterEach(() => {
  queryClient.clear()
  server.resetHandlers()
  server.close()
})

describe('Axios single-flight refresh', () => {
  it('uses one refresh request for concurrent 401 responses', async () => {
    const [userResponse, postsResponse] = await Promise.all([
      apiClient.get('/auth/me'),
      apiClient.get('/posts/my'),
    ])

    expect(userResponse.data.name).toBe('Test User')
    expect(postsResponse.data.content).toEqual([])
    expect(refreshCount).toBe(1)
  })

  it('does not retry when refresh fails with 401', async () => {
    server.use(
      http.post('/api/auth/refresh', () => {
        refreshCount++
        return new HttpResponse(null, { status: 401 })
      }),
    )

    await expect(
      apiClient.get('/auth/me'),
    ).rejects.toMatchObject({
      response: {
        status: 401,
      },
    })

    expect(refreshCount).toBe(1)
  })

  it('preserves server errors when refresh fails with 500', async () => {
    server.use(
      http.post('/api/auth/refresh', () => {
        refreshCount++

        return new HttpResponse(null, { status: 500 })
      }),
    )

    await expect(
      apiClient.get('/auth/me'),
    ).rejects.toMatchObject({
      response: {
        status: 500,
      },
    })

    expect(refreshCount).toBe(1)
  })

  it('sends the CSRF cookie value in the request header', async () => {
    let receivedCsrfHeader: string | null = null

    server.use(
      http.get('/api/auth/csrf', () => {
        return new HttpResponse(null, {
          status: 200,
          headers: {
            'Set-Cookie': 'XSRF-TOKEN=test-csrf-123; Path=/',
          },
        })
      }),

      http.post('/api/auth/login', ({ request }) => {
        receivedCsrfHeader = request.headers.get('X-XSRF-TOKEN')

        return HttpResponse.json({
          user: { id: 1, name: 'Test User' },
        })
      }),
    )

    await initializeCsrf()

    await apiClient.post('/auth/login', {
      email: 'test@example.com',
      password: 'Password123!',
    })

    expect(receivedCsrfHeader).toBe('test-csrf-123')
  })

  it('waits for an active refresh before sending logout', async () => {
    let completeRefresh!: () => void
    const events: string[] = []

    const refreshGate = new Promise<void>(resolve => {
      completeRefresh = resolve
    })

    server.use(
      http.post('/api/auth/refresh', async () => {
        events.push('refresh-started')

        await refreshGate

        events.push('refresh-completed')
        return new HttpResponse(null, { status: 200 })
      }),

      http.post('/api/auth/logout', () => {
        events.push('logout-sent')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const refresh = refreshSession()

    // Περιμένουμε να φτάσει το refresh στον MSW handler.
    await vi.waitFor(() => {
      expect(events).toContain('refresh-started')
    })

    const logout = runLogout(async () => {
      await apiClient.post('/auth/logout')
    })

    expect(events).not.toContain('logout-sent')

    completeRefresh()

    await Promise.all([refresh, logout])

    expect(events).toEqual([
      'refresh-started',
      'refresh-completed',
      'logout-sent',
    ])
  })

  it('prevents new refresh requests while logout is in progress', async () => {
    let completeLogout!: () => void
    let logoutStarted = false

    const logoutGate = new Promise<void>(resolve => {
      completeLogout = resolve
    })

    const logout = runLogout(async () => {
      logoutStarted = true
      await logoutGate
    })

    expect(logoutStarted).toBe(true)

    await expect(refreshSession()).rejects.toThrow('Logout in progress')

    expect(refreshCount).toBe(0)

    completeLogout()
    await logout
  })

  it('does not retry an unauthorized request after logout starts', async () => {
    let completeRefresh!: () => void
    let originalRequestCount = 0

    const refreshGate = new Promise<void>(resolve => {
      completeRefresh = resolve
    })

    server.use(
      http.get('/api/auth/me', () => {
        originalRequestCount++
        return new HttpResponse(null, { status: 401 })
      }),

      http.post('/api/auth/refresh', async () => {
        refreshCount++
        await refreshGate
        return new HttpResponse(null, { status: 200 })
      }),
    )

    const request = apiClient.get('/auth/me')

    // Περιμένουμε να ξεκινήσει πραγματικά το refresh.
    await vi.waitFor(() => {
      expect(refreshCount).toBe(1)
    })

    const logout = runLogout(async () => {
      await apiClient.post('/auth/logout')
    })

    // Ολοκληρώνουμε το refresh αφού έχει ξεκινήσει το logout.
    completeRefresh()

    await expect(request).rejects.toMatchObject({
      response: { status: 401 },
    })

    await logout

    // 1 αρχικό GET + 1 session recheck μέσα στο refresh lock.
    // Δεν έγινε τρίτο GET (retry) μετά την έναρξη logout.
    expect(originalRequestCount).toBe(2)
    expect(refreshCount).toBe(1)
  })

  it('does not retry a POST after the authenticated user changes', async () => {
    let releaseRefresh!: () => void
    let refreshStarted!: () => void

    const refreshGate = new Promise<void>(resolve => {
      releaseRefresh = resolve
    })

    const refreshArrived = new Promise<void>(resolve => {
      refreshStarted = resolve
    })

    let postCount = 0

    server.use(
      http.post('/api/posts', () => {
        postCount++

        if (postCount === 1) {
          return new HttpResponse(null, { status: 401 })
        }

        // Αν φτάσουμε εδώ, έγινε ανεπιθύμητο retry.
        return HttpResponse.json({ id: 10 }, { status: 201 })
      }),

      http.post('/api/auth/refresh', async () => {
        refreshCount++
        refreshStarted()

        await refreshGate

        return new HttpResponse(null, { status: 200 })
      }),
    )

    // Ο User A ξεκινά τη δημιουργία post.
    const request = apiClient.post('/posts', {
      title: 'Post from User A',
    })

    // Περιμένουμε μέχρι να ξεκινήσει το refresh.
    await refreshArrived

    // Προσομοιώνουμε επιτυχημένο login ως User B.
    // Η auth cache αλλάζει, όπως στο useLogin().
    const userB = {
      id: 2,
      name: 'User B',
      email: 'b@example.com',
      createdAt: '2026-10-21T00:00:00Z',
    }

    queryClient.setQueryData(['auth', 'me'], userB)
    markAuthChanged()

    // Ολοκληρώνουμε το παλιό refresh.
    releaseRefresh()

    // Το παλιό POST δεν πρέπει να εκτελεστεί ξανά.
    await expect(request).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(postCount).toBe(1)
    expect(refreshCount).toBe(1)
  })

  it('clears the auth cache when refresh definitively fails with 401', async () => {
    const userA = {
      id: 1,
      name: 'User A',
      email: 'a@example.com',
    }

    queryClient.setQueryData(['auth', 'me'], userA)

    server.use(
      http.post('/api/auth/refresh', () => {
        refreshCount++
        return new HttpResponse(null, { status: 401 })
      }),
    )

    await expect(
      apiClient.get('/auth/me'),
    ).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refreshCount).toBe(1)

    // Δεν πρέπει να παραμένει authenticated ο User A.
    expect(
      queryClient.getQueryData(['auth', 'me']),
    ).toBeNull()
  })

  it('does not clear a newer login after an old refresh fails with 401', async () => {
    const userA = {
      id: 1,
      name: 'User A',
      email: 'a@example.com',
    }

    const userB = {
      id: 2,
      name: 'User B',
      email: 'b@example.com',
    }

    let releaseRefresh!: () => void
    let signalRefreshStarted!: () => void

    const refreshGate = new Promise<void>(resolve => {
      releaseRefresh = resolve
    })

    const refreshStarted = new Promise<void>(resolve => {
      signalRefreshStarted = resolve
    })

    queryClient.setQueryData(['auth', 'me'], userA)

    server.use(
      http.post('/api/auth/refresh', async () => {
        refreshCount++
        signalRefreshStarted()

        await refreshGate

        return new HttpResponse(null, { status: 401 })
      }),
    )

    // Το request ξεκινά με την παλιά ταυτότητα.
    const oldRequest = apiClient.get('/auth/me')

    // Περιμένουμε να έχει ξεκινήσει πραγματικά το refresh.
    await refreshStarted

    // Προσομοιώνουμε νέο επιτυχημένο login ως User B.
    markAuthChanged()
    queryClient.setQueryData(['auth', 'me'], userB)

    // Το refresh του User A αποτυγχάνει καθυστερημένα.
    releaseRefresh()

    await expect(oldRequest).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refreshCount).toBe(1)

    // Το παλιό 401 δεν πρέπει να διαγράψει τον User B.
    expect(
      queryClient.getQueryData(['auth', 'me']),
    ).toEqual(userB)
  })
})
