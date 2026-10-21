import { test, expect } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('refresh waiting behind logout must not restore the session', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([tabA.goto('/'), tabB.goto('/')])

    await tabA.goto('/login')
    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    // Αναγκάζουμε το επόμενο authenticated request
    // να χρειαστεί refresh.
    await context.clearCookies({ name: 'accessToken' })

    let releaseLogout!: () => void
    let signalLogoutReady!: () => void

    const logoutGate = new Promise<void>(resolve => {
      releaseLogout = resolve
    })

    const logoutReady = new Promise<void>(resolve => {
      signalLogoutReady = resolve
    })

    let refreshCount = 0

    // 2. Το logout φτάνει στο backend,
    // αλλά κρατάμε το response.
    await context.route('**/api/auth/logout', async route => {
      const response = await route.fetch()

      signalLogoutReady()

      await logoutGate
      await route.fulfill({ response })
    })

    await context.route('**/api/auth/refresh', async route => {
      refreshCount++
      await route.continue()
    })

    // 3. Tab A: ξεκινά logout.
    await tabA.getByRole('button', { name: 'Logout' }).click()

    await logoutReady

    // Επιβεβαιώνουμε ότι το logout κατέχει το Web Lock.
    await expect.poll(async () => {
      return tabB.evaluate(async () => {
        const locks = await navigator.locks.query()

        return locks.held?.some(
          lock => lock.name === 'pawspot-auth'
        ) ?? false
      })
    }).toBe(true)

    // 4. Tab B: ξεκινά request που απαιτεί refresh.
    const requestFromTabB = tabB.evaluate(async () => {
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

    // 5. ΚΡΙΣΙΜΟ: Περιμένουμε να μπει το refresh
    // πραγματικά στην ουρά του Web Lock.
    await expect.poll(async () => {
      return tabB.evaluate(async () => {
        const locks = await navigator.locks.query()

        return locks.pending?.some(
          lock => lock.name === 'pawspot-auth'
        ) ?? false
      })
    }).toBe(true)

    // 6. Ολοκληρώνουμε το logout.
    releaseLogout()

    await expect(
      tabA.getByText('You are not logged in.')
    ).toBeVisible()

    const result = await requestFromTabB

    // 7. Η session πρέπει να παραμείνει αποσυνδεδεμένη.
    expect(result).toBe(401)
    expect(refreshCount).toBe(1)

    const cookies = await context.cookies()

    expect(
      cookies.some(cookie => cookie.name === 'accessToken')
    ).toBe(false)

    expect(
      cookies.some(cookie => cookie.name === 'refreshToken')
    ).toBe(false)
  } finally {
    await context.close()
  }
})
