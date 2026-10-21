import { test, expect } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('expired session updates all tabs automatically', async ({ browser }) => {
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

    // 1. Login με τον υπάρχοντα test account.
    await tabA.goto('/login')
    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    // 2. Και τα δύο tabs εμφανίζουν authenticated UI.
    await tabB.reload()
    await expect(tabB.getByText(/welcome/i)).toBeVisible()

    // 3. Αποθηκεύουμε προσωπικά δεδομένα στην cache του Tab B.
    const stateBeforeExpiry = await tabB.evaluate(async () => {
      const path = '/src/app/queryClient.ts'
      const { queryClient } = await import(/* @vite-ignore */ path)

      const documentMarker = crypto.randomUUID()

      Object.assign(window, { __pawspotDocumentMarker: documentMarker })

      queryClient.setQueryData(['posts', 'my'], [
        { id: 1, title: 'Private post' },
      ])

      return {
        authGeneration: localStorage.getItem('pawspot-auth-generation'),
        documentMarker,
        user: queryClient.getQueryData(['auth', 'me']),
        privatePosts: queryClient.getQueryData(['posts', 'my']),
      }
    })

    expect(stateBeforeExpiry.authGeneration).toBeTruthy()
    expect(stateBeforeExpiry.user).toBeTruthy()
    expect(stateBeforeExpiry.privatePosts).toEqual([
      { id: 1, title: 'Private post' },
    ])

    // Αφαιρούμε το ενεργό auth query από το initiating tab. Το request
    // παρακάτω εξακολουθεί να χρησιμοποιεί τον πραγματικό apiClient.
    await tabA.goto('/login')

    const sourceCacheBeforeExpiry = await tabA.evaluate(
      async user => {
        const path = '/src/app/queryClient.ts'
        const { queryClient } = await import(/* @vite-ignore */ path)

        queryClient.setQueryData(['auth', 'me'], user)
        queryClient.setQueryData(['posts', 'my'], [
          { id: 2, title: 'Private post in source tab' },
        ])

        return {
          user: queryClient.getQueryData(['auth', 'me']),
          privatePosts: queryClient.getQueryData(['posts', 'my']),
        }
      },
      stateBeforeExpiry.user,
    )

    expect(sourceCacheBeforeExpiry.user).toEqual(stateBeforeExpiry.user)
    expect(sourceCacheBeforeExpiry.privatePosts).toEqual([
      { id: 2, title: 'Private post in source tab' },
    ])

    // 4. Αφαιρούμε μόνο το access token,
    // ώστε το επόμενο request να επιχειρήσει refresh.
    await context.clearCookies({ name: 'accessToken' })

    let refreshCount = 0

    // 5. Προσομοιώνουμε οριστικό refresh 401.
    await tabA.route('**/api/auth/refresh', async route => {
      refreshCount++

      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        headers: {
          'Set-Cookie':
            'refreshToken=; Max-Age=0; Path=/api/auth; HttpOnly',
        },
        body: JSON.stringify({
          message: 'Refresh token expired',
        }),
      })
    })

    // 6. Το Tab A επιχειρεί authenticated request.
    const status = await tabA.evaluate(async () => {
      const path = '/src/api/client.ts'
      const { apiClient } = await import(/* @vite-ignore */ path)

      try {
        const response = await apiClient.get('/auth/me')
        return response.status
      } catch (error: unknown) {
        if (
          typeof error === 'object' &&
          error !== null &&
          'response' in error
        ) {
          return (error.response as { status?: number }).status ?? 0
        }

        return 0
      }
    })

    expect(status).toBe(401)
    expect(refreshCount).toBe(1)

    const sourceState = await tabA.evaluate(async () => {
      const path = '/src/app/queryClient.ts'
      const { queryClient } = await import(/* @vite-ignore */ path)

      return {
        user: queryClient.getQueryData(['auth', 'me']),
        privatePosts: queryClient.getQueryData(['posts', 'my']),
      }
    })

    expect(sourceState.user).toBeNull()
    expect(sourceState.privatePosts).toBeUndefined()

    // 7. Το Tab B ενημερώνεται μέσω BroadcastChannel,
    // χωρίς reload ή χειροκίνητο refetch.
    await expect(
      tabB.getByText('You are not logged in.'),
    ).toBeVisible()

    // 8. Οι caches πρέπει να έχουν καθαριστεί.
    const state = await tabB.evaluate(async () => {
      const path = '/src/app/queryClient.ts'
      const { queryClient } = await import(/* @vite-ignore */ path)

      return {
        authGeneration: localStorage.getItem('pawspot-auth-generation'),
        documentMarker: (
          window as Window & { __pawspotDocumentMarker?: string }
        ).__pawspotDocumentMarker,
        user: queryClient.getQueryData(['auth', 'me']),
        privatePosts: queryClient.getQueryData(['posts', 'my']),
      }
    })

    expect(state.authGeneration).not.toBe(stateBeforeExpiry.authGeneration)
    expect(state.documentMarker).toBe(stateBeforeExpiry.documentMarker)
    expect(state.user).toBeNull()
    expect(state.privatePosts).toBeUndefined()
  } finally {
    await context.close()
  }
})
