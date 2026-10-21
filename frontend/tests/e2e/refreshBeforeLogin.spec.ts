import { test, expect } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('login waits for an in-flight cross-tab refresh', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()

  let releaseRefresh!: () => void

  const refreshGate = new Promise<void>(resolve => {
    releaseRefresh = resolve
  })

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([tabA.goto('/'), tabB.goto('/')])

    await tabA.goto('/login')
    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    await context.clearCookies({ name: 'accessToken' })

    let signalRefreshReady!: () => void

    const refreshReady = new Promise<void>(resolve => {
      signalRefreshReady = resolve
    })

    let loginRequests = 0

    await context.route('**/api/auth/refresh', async route => {
      const response = await route.fetch()

      expect(response.status()).toBe(200)

      signalRefreshReady()

      await refreshGate

      await route.fulfill({ response })
    })

    await context.route('**/api/auth/login', async route => {
      loginRequests++
      await route.continue()
    })

    // Tab A: refresh αποκτά το lock.
    const refreshRequest = tabA.evaluate(async () => {
      const path = '/src/api/refresh.ts'
      const { refreshSession } = await import(/* @vite-ignore */ path)

      await refreshSession()
    })

    await refreshReady

    // Tab B: ξεκινά login όσο το refresh κρατά το lock.
    const loginRequest = tabB.evaluate(
      async ({ email, password }) => {
        const path = '/src/features/auth/api/auth.api.ts'
        const { login } = await import(/* @vite-ignore */ path)

        return login({ email, password })
      },
      { email, password },
    )

    // Επιβεβαιώνουμε ότι το login περιμένει πραγματικά.
    await expect.poll(async () => {
      return tabB.evaluate(async () => {
        const locks = await navigator.locks.query()

        return {
          held: locks.held?.some(
            lock => lock.name === 'pawspot-auth'
          ) ?? false,
          pending: locks.pending?.some(
            lock => lock.name === 'pawspot-auth'
          ) ?? false,
        }
      })
    }).toEqual({ held: true, pending: true })

    // Δεν έχει σταλεί νέο login request.
    expect(loginRequests).toBe(0)

    // Ολοκληρώνεται το refresh και απελευθερώνει το lock.
    releaseRefresh()

    await refreshRequest

    // Τώρα επιτρέπεται να ολοκληρωθεί το login.
    const user = await loginRequest

    expect(user.email).toBe(email)
    expect(loginRequests).toBe(1)

    // Η τελική authentication κατάσταση παραμένει έγκυρη.
    const response = await tabB.evaluate(async () => {
      const path = '/src/api/client.ts'
      const { apiClient } = await import(/* @vite-ignore */ path)

      const result = await apiClient.get('/auth/me')
      return result.status
    })

    expect(response).toBe(200)
  } finally {
    releaseRefresh()
    await context.close()
  }
})
