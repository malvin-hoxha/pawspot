import { useMutation, useQueryClient } from '@tanstack/react-query'
import { logout } from '../api/auth.api'

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,

    onMutate: async () => {
      // Αποτρέπουμε ενεργά queries από το να
      // γράψουν παλιά δεδομένα στην cache.
      await queryClient.cancelQueries()
    },

    onSuccess: () => {
      // Το backend ολοκλήρωσε το logout.
      // Καθαρίζουμε όλα τα cached queries,
      // συμπεριλαμβανομένων των προσωπικών δεδομένων.
      queryClient.removeQueries({
        queryKey: ['posts', 'my'],
      })
      queryClient.setQueryData(['auth', 'me'], null)
    },
  })
}