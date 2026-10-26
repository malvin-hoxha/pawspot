import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../hooks/useAuth'

export function GuestOnlyRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <p>Φόρτωση...</p>
  if (isAuthenticated) return <Navigate to="/" replace />

  return <Outlet />
}