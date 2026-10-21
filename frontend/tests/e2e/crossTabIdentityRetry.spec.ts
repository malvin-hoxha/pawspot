import { test, expect } from '@playwright/test'
import type { Route } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('does not retry an old POST after a new login to the same account', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()

  let releaseOldResponse!: () => void

  const responseGate = new Promise<void>(resolve => {
    releaseOldResponse = resolve
  })

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([tabA.goto('/'), tabB.goto('/')])

    // 1. Συνδεόμαστε αρχικά.
    await tabA.goto('/login')
    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    const initialIdentity = await tabA.evaluate(async () => {
      const path = '/src/features/auth/api/auth.api.ts'
      const { getCurrentUser } = await import(/* @vite-ignore */ path)

      return getCurrentUser()
    })

    const generationBefore = await tabA.evaluate(() =>
      localStorage.getItem('pawspot-auth-generation')
    )

    expect(initialIdentity.email).toBe(email)
    expect(generationBefore).toBeTruthy()

    let postCount = 0
    let signalPostStarted!: () => void

    const postStarted = new Promise<void>(resolve => {
      signalPostStarted = resolve
    })

    // 2. Το αρχικό POST επιστρέφει 401,
    // αλλά καθυστερούμε την παράδοση στον Axios.
    await context.route('**/api/posts', async (route: Route) => {
      if (route.request().method() !== 'POST') {
        await route.continue()
        return
      }

      postCount++

      if (postCount === 1) {
        signalPostStarted()

        await responseGate

        await route.fulfill({
          status: 401,
          body: '',
        })

        return
      }

      // Αν γίνει retry, το καταγράφουμε.
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ id: 123 }),
      })
    })

    // 3. Tab A: ξεκινά το παλιό POST.
    const oldRequest = tabA.evaluate(async () => {
      const path = '/src/api/client.ts'
      const { apiClient } = await import(/* @vite-ignore */ path)

      try {
        const response = await apiClient.post('/posts', {
          title: 'Original post',
        })

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

    await postStarted

    // 4. Tab B: νέο login στον ίδιο λογαριασμό.
    const loginResult = await tabB.evaluate(
      async ({ email, password }) => {
        const path = '/src/features/auth/api/auth.api.ts'
        const { login } = await import(/* @vite-ignore */ path)

        return login({ email, password })
      },
      { email, password },
    )

    expect(loginResult.id).toBe(initialIdentity.id)
    expect(loginResult.email).toBe(initialIdentity.email)

    // 5. Το generation έχει αλλάξει.
    // Επιβεβαιώνουμε ότι είναι κοινό στα δύο tabs.
    const versions = await Promise.all([
      tabA.evaluate(() => localStorage.getItem('pawspot-auth-generation')),
      tabB.evaluate(() => localStorage.getItem('pawspot-auth-generation')),
    ])

    expect(versions[0]).toBeTruthy()
    expect(versions[0]).not.toBe(generationBefore)
    expect(versions[0]).toBe(versions[1])

    // 6. Τώρα αφήνουμε το παλιό POST να λάβει 401.
    releaseOldResponse()

    const result = await oldRequest

    // Το παλιό request απορρίπτεται.
    expect(result).toBe(401)

    // Και δεν πρέπει να έχει γίνει δεύτερο POST.
    expect(postCount).toBe(1)
  } finally {
    releaseOldResponse()
    await context.close()
  }
})
