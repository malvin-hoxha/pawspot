import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { getCurrentUser } from '../api/auth.api'

export function useCurrentUser() {
    return useQuery({
        queryKey: ['auth', 'me'],
        queryFn: getCurrentUser,

        retry: (failureCount, error) => {
            if (axios.isAxiosError(error) && error.response?.status === 401) {
                return false
            }
            return failureCount < 1
        },

        staleTime: 60_000, // 1 minute
    });
}