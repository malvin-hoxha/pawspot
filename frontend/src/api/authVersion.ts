import { queryClient } from '../app/queryClient'

const AUTH_GENERATION_KEY = 'pawspot-auth-generation'

const channel =
  typeof BroadcastChannel !== 'undefined'
    ? new BroadcastChannel('pawspot-auth-state')
    : null

export function getAuthVersion(): string | null {
  return localStorage.getItem(AUTH_GENERATION_KEY)
}

export function markAuthChanged(): void {
  const generation = crypto.randomUUID()

  localStorage.setItem(AUTH_GENERATION_KEY, generation)

  channel?.postMessage({
    type: 'AUTH_CHANGED',
  })
}

channel?.addEventListener('message', event => {
  if (event.data?.type !== 'AUTH_CHANGED') {
    return
  }

  // Ακυρώνουμε ενεργά queries του προηγούμενου χρήστη.
  void queryClient.cancelQueries().then(() => {
    // Αφαιρούμε προσωπικά δεδομένα από την cache.
    queryClient.removeQueries({
      queryKey: ['posts', 'my'],
    })

    // Η παλιά ταυτότητα δεν θεωρείται πλέον έγκυρη.
    queryClient.setQueryData(['auth', 'me'], null)

    // Ζητάμε την πραγματική τρέχουσα ταυτότητα.
    void queryClient.invalidateQueries({
      queryKey: ['auth', 'me'],
    })
  })
})