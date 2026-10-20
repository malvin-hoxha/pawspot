import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { apiClient } from '../../src/api/client'
import { initializeCsrf } from '../../src/api/csrf'
import { refreshSession, runLogout } from '../../src/api/refresh'


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
})

afterEach(() => {
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

    // Το αρχικό request εκτελέστηκε μόνο μία φορά.
    expect(originalRequestCount).toBe(1)
    expect(refreshCount).toBe(1)
  })
})
