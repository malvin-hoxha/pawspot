import { test, expect } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('logout in one tab updates the other tab automatically', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([
      tabA.goto('/'),
      tabB.goto('/'),
    ])

    // 1. Login στο Tab A.
    await tabA.goto('/login')

    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    // 2. Το Tab B φορτώνει την authenticated κατάσταση.
    await tabB.reload()

    await expect(tabB.getByText(/welcome/i)).toBeVisible()

    // 3. Αποθηκεύουμε προσωπικά δεδομένα στην cache του Tab B.
    // Χρησιμοποιούμε την ίδια QueryClient instance της εφαρμογής.
    const stateBeforeLogout = await tabB.evaluate(async () => {
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
        privatePosts: queryClient.getQueryData(['posts', 'my']),
      }
    })

    expect(stateBeforeLogout.authGeneration).toBeTruthy()
    expect(stateBeforeLogout.privatePosts).toEqual([
      { id: 1, title: 'Private post' },
    ])

    // 4. Logout στο Tab A.
    await tabA.getByRole('button', { name: 'Logout' }).click()

    await expect(
      tabA.getByText('You are not logged in.'),
    ).toBeVisible()

    // 5. Το Tab B πρέπει να ενημερωθεί χωρίς reload.
    await expect(
      tabB.getByText('You are not logged in.'),
    ).toBeVisible()

    // 6. Δεν πρέπει να υπάρχει παλιά ταυτότητα ή προσωπική cache.
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

    expect(state.authGeneration).not.toBe(stateBeforeLogout.authGeneration)
    expect(state.documentMarker).toBe(stateBeforeLogout.documentMarker)
    expect(state.user).toBeNull()
    expect(state.privatePosts).toBeUndefined()

    // 7. Επιβεβαιώνουμε ότι τα cookies διαγράφηκαν.
    const cookies = await context.cookies()

    expect(
      cookies.some(cookie => cookie.name === 'accessToken'),
    ).toBe(false)

    expect(
      cookies.some(cookie => cookie.name === 'refreshToken'),
    ).toBe(false)
  } finally {
    await context.close()
  }
})
