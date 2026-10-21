import { test, expect } from '@playwright/test'
import type { Route } from '@playwright/test'

declare const process: {
  env: Record<string, string | undefined>
}

const BASE_URL = 'http://localhost:5173'
const AUTH_MODULE = '/src/features/auth/api/auth.api.ts'
const CLIENT_MODULE = '/src/api/client.ts'

test('old POST must not retry as another user', async ({ browser }) => {
  const emailA = process.env.E2E_USER_A_EMAIL
  const passwordA = process.env.E2E_USER_A_PASSWORD
  const emailB = process.env.E2E_USER_B_EMAIL
  const passwordB = process.env.E2E_USER_B_PASSWORD

  if (!emailA || !passwordA || !emailB || !passwordB) {
    throw new Error('Missing E2E user credentials')
  }

  expect(emailA).not.toBe(emailB)

  // Κοινά cookies και localStorage.
  const context = await browser.newContext()
  const tabA = await context.newPage()
  const tabB = await context.newPage()

  let release401: (() => void) | undefined

  try {
    await Promise.all([
      tabA.goto(BASE_URL),
      tabB.goto(BASE_URL),
    ])

    // 1. Login ως User A.
    const userA = await tabA.evaluate(
      async ({ email, password, modulePath }) => {
        const { login } = await import(
          /* @vite-ignore */ modulePath
        )

        return login({ email, password })
      },
      {
        email: emailA,
        password: passwordA,
        modulePath: AUTH_MODULE,
      },
    )

    const generationA = await tabA.evaluate(() =>
      localStorage.getItem('pawspot-auth-generation')
    )

    expect(generationA).toBeTruthy()

    // 2. Ετοιμάζουμε ένα 401 που θα ελέγχουμε εμείς.
    let notifyFirstPost!: () => void

    const firstPostArrived = new Promise<void>(resolve => {
      notifyFirstPost = resolve
    })

    const waitBefore401 = new Promise<void>(resolve => {
      release401 = resolve
    })

    let postCount = 0

    await tabA.route('**/api/posts', async (route: Route) => {
      if (route.request().method() !== 'POST') {
        await route.continue()
        return
      }

      postCount++

      if (postCount === 1) {
        notifyFirstPost()

        // Περιμένουμε να συνδεθεί ο User B.
        await waitBefore401

        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Unauthorized',
          }),
        })

        return
      }

      // Αν γίνει retry, το μετράμε αλλά δεν
      // επιτρέπουμε δημιουργία πραγματικής αγγελίας.
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Unexpected POST retry',
        }),
      })
    })

    // 3. Ο User A ξεκινά POST.
    const pendingPost = tabA.evaluate(
      async (modulePath) => {
        const { apiClient } = await import(
          /* @vite-ignore */ modulePath
        )

        const data = new FormData()
        data.append('test', 'cross-user-race')

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
      },
      CLIENT_MODULE,
    )

    await firstPostArrived

    // 4. Πριν επιστρέψει το 401,
    // γίνεται πραγματικό login ως User B.
    const userB = await tabB.evaluate(
      async ({ email, password, modulePath }) => {
        const { login } = await import(
          /* @vite-ignore */ modulePath
        )

        return login({ email, password })
      },
      {
        email: emailB,
        password: passwordB,
        modulePath: AUTH_MODULE,
      },
    )

    const generationB = await tabB.evaluate(() =>
      localStorage.getItem('pawspot-auth-generation')
    )

    const generationSeenByA = await tabA.evaluate(() =>
      localStorage.getItem('pawspot-auth-generation')
    )

    expect(generationB).toBeTruthy()
    expect(generationB).not.toBe(generationA)
    expect(generationSeenByA).toBe(generationB)

    expect(userB.id).not.toBe(userA.id)

    // 5. Επιτρέπουμε στο παλιό POST να λάβει 401.
    release401?.()

    const result = await pendingPost

    // 6. Ελέγχουμε ότι δεν έγινε retry.
    expect(result.succeeded).toBe(false)
    expect(result.status).toBe(401)
    expect(postCount).toBe(1)

    // 7. Επιβεβαιώνουμε ότι ο backend
    // αναγνωρίζει τον User B.
    const currentUser = await tabB.evaluate(
      async (modulePath) => {
        const { getCurrentUser } = await import(
          /* @vite-ignore */ modulePath
        )

        return getCurrentUser()
      },
      AUTH_MODULE,
    )

    expect(currentUser.id).toBe(userB.id)
  } finally {
    // Αποφεύγουμε να αφήσουμε route σε αναμονή
    // αν αποτύχει κάποια assertion ή το login.
    release401?.()
    await context.close()
  }
})
