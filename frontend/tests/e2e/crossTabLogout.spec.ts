import { test, expect } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('logout must not be undone by a delayed cross-tab refresh response', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()

  let releaseRefresh: (() => void) | undefined

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([
      tabA.goto('/'),
      tabB.goto('/'),
    ])

    await tabA.goto('/login')

    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    // 2. Αφαιρούμε μόνο το access cookie για να απαιτείται refresh.
    await context.clearCookies({ name: 'accessToken' })

    let signalRefreshReady!: () => void

    const refreshGate = new Promise<void>(resolve => {
      releaseRefresh = resolve
    })

    const refreshReady = new Promise<void>(resolve => {
      signalRefreshReady = resolve
    })

    // 3. Το backend ολοκληρώνει το refresh,
    // αλλά κρατάμε το response πριν φτάσει στον browser.
    await context.route('**/api/auth/refresh', async route => {
      const response = await route.fetch()

      if (response.status() !== 200) {
        await route.fulfill({ response })
        return
      }

      signalRefreshReady()

      await refreshGate

      await route.fulfill({ response })
    })

    let logoutCount = 0

    await context.route('**/api/auth/logout', async route => {
      logoutCount++
      await route.continue()
    })

    // 4. Ξεκινά το κανονικό Axios flow στο Tab B.
    const refreshRequest = tabB.evaluate(async () => {
      const modulePath = '/src/api/client.ts'

      const { apiClient } = await import(
        /* @vite-ignore */ modulePath
      )

      try {
        const response = await apiClient.get('/auth/me')
        return response.status
      } catch (error: unknown) {
        if (
          typeof error === 'object' &&
          error !== null &&
          'response' in error
        ) {
          const response = error.response as {
            status?: number
          }

          return response.status ?? 0
        }

        return 0
      }
    })

    // Περιμένουμε να ολοκληρωθεί το backend refresh.
    await refreshReady

    await tabA.getByRole('button', { name: 'Logout' }).click()

    // Το refresh κρατά το lock και το logout πρέπει να βρίσκεται στην ουρά.
    await expect.poll(async () => {
      return tabA.evaluate(async () => {
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

    // Δεν πρέπει να έχει φτάσει logout request στο backend πριν ελευθερωθεί
    // το lock του refresh.
    expect(logoutCount).toBe(0)

    releaseRefresh?.()

    await refreshRequest

    // Τώρα περιμένουμε να ολοκληρωθεί το logout.
    await expect(
      tabA.getByText('You are not logged in.')
    ).toBeVisible()

    expect(logoutCount).toBe(1)

    const cookiesAfterLateRefresh = await context.cookies()

    const accessRestored = cookiesAfterLateRefresh.some(
      cookie => cookie.name === 'accessToken'
    )

    const refreshRestored = cookiesAfterLateRefresh.some(
      cookie => cookie.name === 'refreshToken'
    )

    // 7. Επιθυμητή συμπεριφορά:
    // Το παλιό response δεν πρέπει να αναιρέσει το logout.
    expect(accessRestored).toBe(false)
    expect(refreshRestored).toBe(false)

  } finally {
    releaseRefresh?.()
    await context.close()
  }
})
