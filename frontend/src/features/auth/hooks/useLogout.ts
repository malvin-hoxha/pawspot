import { useMutation, useQueryClient } from '@tanstack/react-query'
import { logout } from '../api/auth.api'
import { markAuthChanged } from '../../../api/authVersion'

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,

    onMutate: async () => {
      markAuthChanged()
      await queryClient.cancelQueries()
    },

    onSuccess: async () => {
      // Το backend ολοκλήρωσε το logout.
      // Καθαρίζουμε όλα τα cached queries,
      // συμπεριλαμβανομένων των προσωπικών δεδομένων.
      await queryClient.cancelQueries()

      queryClient.removeQueries({
        queryKey: ['posts', 'my'],
      })
      queryClient.setQueryData(['auth', 'me'], null)
    },
  })
}