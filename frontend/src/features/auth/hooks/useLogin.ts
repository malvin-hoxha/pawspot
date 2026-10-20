import { useMutation, useQueryClient } from '@tanstack/react-query'
import { login } from '../api/auth.api'
import type { AuthUser } from '../api/auth.api'

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,

    onSuccess: (user: AuthUser) => {
      queryClient.setQueryData(['auth', 'me'], user)
    },
  })
}