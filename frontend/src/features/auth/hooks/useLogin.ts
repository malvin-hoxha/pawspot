import { useMutation, useQueryClient } from '@tanstack/react-query'
import { login } from '../api/auth.api'
import type { AuthUser } from '../api/auth.api'
import { markAuthChanged } from '../../../api/authVersion'

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,

    onMutate: async () => {
      markAuthChanged()
      await queryClient.cancelQueries()
    },

    onSuccess: (user: AuthUser) => {
      // Αφαιρούμε cached δεδομένα προηγούμενου χρήστη.
      queryClient.removeQueries({
        queryKey: ['posts', 'my'],
      })

      queryClient.setQueryData(['auth', 'me'], user)
    },
  })
}