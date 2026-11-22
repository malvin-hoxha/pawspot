import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../hooks/useAuth'

export function AdminRoute() {
    const { user, isLoading, isError } = useAuth()

    if (isLoading) {
        return <p role="status">Έλεγχος πρόσβασης…</p>
    }

    if (isError) {
        return (
            <p role="alert">
                Δεν ήταν δυνατός ο έλεγχος πρόσβασης.
            </p>
        )
    }

    if (user?.role !== 'ADMIN') {
        return <Navigate to="/" replace />
    }

    return <Outlet />
}