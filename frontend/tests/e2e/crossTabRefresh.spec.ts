import { test, expect } from '@playwright/test'
import type { Page, Route } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

test('concurrent refresh from two tabs should preserve the session', async ({ browser }) => {
  const email = process.env.E2E_USER_A_EMAIL
  const password = process.env.E2E_USER_A_PASSWORD

  if (!email || !password) {
    throw new Error('Missing E2E_USER_A_EMAIL or E2E_USER_A_PASSWORD')
  }

  const context = await browser.newContext()
  let releaseInitialMeRequests: (() => void) | undefined
  let releaseRefresh: (() => void) | undefined

  try {
    const tabA = await context.newPage()
    const tabB = await context.newPage()

    await Promise.all([tabA.goto('/'), tabB.goto('/')])

    await tabA.goto('/login')
    await tabA.getByLabel('Email').fill(email)
    await tabA.getByLabel('Password').fill(password)
    await tabA.getByRole('button', { name: /login/i }).click()

    await expect(tabA.getByText(/welcome/i)).toBeVisible()

    // Και τα δύο αρχικά /auth/me requests πρέπει να έχουν φύγει χωρίς
    // access cookie πριν επιτρέψουμε σε κάποιο response να ενεργοποιήσει
    // τον interceptor.
    await context.clearCookies({ name: 'accessToken' })

    let initialMeRequests = 0
    const initialMeGate = new Promise<void>(resolve => {
      releaseInitialMeRequests = resolve
    })

    await context.route('**/api/auth/me', async (route: Route) => {
      if (initialMeRequests < 2) {
        initialMeRequests++

        if (initialMeRequests === 2) {
          releaseInitialMeRequests?.()
        }

        await initialMeGate
      }

      await route.continue()
    })

    let refreshCount = 0
    let signalRefreshReady!: () => void

    const refreshGate = new Promise<void>(resolve => {
      releaseRefresh = resolve
    })

    const refreshReady = new Promise<void>(resolve => {
      signalRefreshReady = resolve
    })

    await context.route('**/api/auth/refresh', async route => {
      refreshCount++
      const response = await route.fetch()

      if (refreshCount === 1) {
        signalRefreshReady()
        await refreshGate
      }

      await route.fulfill({ response })
    })

    const requestFromTab = (page: Page) =>
      page.evaluate(async () => {
        // @ts-expect-error Module served dynamically by Vite in the browser
        const { apiClient } = await import(/* @vite-ignore */ '/src/api/client.ts')

        try {
          const response = await apiClient.get('/auth/me')
          return response.status
        } catch (error: unknown) {
          if (
            typeof error === 'object' &&
            error !== null &&
            'response' in error
          ) {
            const response = error.response as { status?: number }
            return response?.status ?? 0
          }

          return 0
        }
      })

    const requests = Promise.all([
      requestFromTab(tabA),
      requestFromTab(tabB),
    ])

    await refreshReady

    expect(initialMeRequests).toBe(2)

    // Το πρώτο tab κρατά το lock όσο το δεύτερο περιμένει στην ουρά.
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

    expect(refreshCount).toBe(1)

    releaseRefresh?.()

    const results = await requests

    expect(results).toEqual([200, 200])

    // Το πρώτο lock holder κάνει refresh. Το δεύτερο κάνει /auth/me
    // recheck αφού πάρει το lock και δεν περιστρέφει ξανά το token.
    expect(refreshCount).toBe(1)

    await context.clearCookies({ name: 'accessToken' })

    const finalCheck = await requestFromTab(tabA)

    expect(finalCheck).toBe(200)
    expect(refreshCount).toBe(2)
  } finally {
    releaseInitialMeRequests?.()
    releaseRefresh?.()
    await context.close()
  }
})
