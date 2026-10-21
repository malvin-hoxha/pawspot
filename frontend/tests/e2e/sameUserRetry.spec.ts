import { test, expect } from '@playwright/test'
import type { Route } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

const BASE_URL = 'http://localhost:5173'
const AUTH_MODULE = '/src/features/auth/api/auth.api.ts'
const CLIENT_MODULE = '/src/api/client.ts'

test('POST retries after 401 when user has not changed', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()
  const page = await context.newPage()

  try {
    await page.goto(BASE_URL)

    // 1. Πραγματικό login στον backend.
    const user = await page.evaluate(
      async ({ email, password, modulePath }) => {
        const { login } = await import(
          /* @vite-ignore */ modulePath
        )

        return login({ email, password })
      },
      { email, password, modulePath: AUTH_MODULE },
    )

    expect(user.id).toBeDefined()

    // 2. Αποθηκεύουμε το authentication generation.
    const generationBefore = await page.evaluate(() => {
      return localStorage.getItem('pawspot-auth-generation')
    })

    expect(generationBefore).toBeTruthy()

    let postCount = 0

    // 3. Πρώτο POST -> 401.
    //    Δεύτερο POST -> επιτυχία.
    await page.route('**/api/posts', async (route: Route) => {
      if (route.request().method() !== 'POST') {
        await route.continue()
        return
      }

      postCount++

      if (postCount === 1) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Unauthorized',
          }),
        })
        return
      }

      await route.fulfill({
        status: postCount === 2 ? 201 : 409,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 12345,
          message: 'Mocked response',
        }),
      })
    })

    // 4. Το POST εκτελείται μέσω του πραγματικού
    // Axios apiClient και των interceptors του.
    const result = await page.evaluate(async (modulePath) => {
      const { apiClient } = await import(
        /* @vite-ignore */ modulePath
      )

      const data = new FormData()
      data.append('test', 'same-user-retry')

      try {
        const response = await apiClient.post('/posts', data)

        return {
          succeeded: true,
          status: response.status,
        }
      } catch (error: unknown) {
        const status =
          typeof error === 'object' &&
          error !== null &&
          'response' in error
            ? (
                error as {
                  response?: { status?: number }
                }
              ).response?.status
            : undefined

        return {
          succeeded: false,
          status: status ?? null,
        }
      }
    }, CLIENT_MODULE)

    // 5. Το retry πρέπει να ολοκληρώθηκε.
    expect(result.succeeded).toBe(true)
    expect(result.status).toBe(201)

    // Αρχικό POST + ένα retry.
    expect(postCount).toBe(2)

    // 6. Δεν πρέπει να άλλαξε το authGeneration.
    const generationAfter = await page.evaluate(() => {
      return localStorage.getItem('pawspot-auth-generation')
    })

    expect(generationAfter).toBe(generationBefore)

    // 7. Ο backend εξακολουθεί να βλέπει τον User A.
    const currentUser = await page.evaluate(async (modulePath) => {
      const { getCurrentUser } = await import(
        /* @vite-ignore */ modulePath
      )

      return getCurrentUser()
    }, AUTH_MODULE)

    expect(currentUser.id).toBe(user.id)
  } finally {
    await context.close()
  }
})
