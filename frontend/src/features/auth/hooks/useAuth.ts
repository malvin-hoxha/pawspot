import axios from 'axios'
import { useCurrentUser } from './useCurrentUser'

export function useAuth() {
    const query = useCurrentUser();

    const isUnauthorized = axios.isAxiosError(query.error) && query.error.response?.status === 401;

    return {
        user: query.data ?? null,
        isAuthenticated: query.isSuccess,
        isUnauthenticated: query.isError && isUnauthorized,
        isLoading: query.isPending,
        isError: query.isError && !isUnauthorized
    }
}