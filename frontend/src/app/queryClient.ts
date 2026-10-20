import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000, // 30 seconds
            retry: 1, // Retry failed requests once
        },
        mutations: {
            retry: false, // Do not retry failed mutations
        },
    }
});