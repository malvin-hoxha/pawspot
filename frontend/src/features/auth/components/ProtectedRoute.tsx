import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../hooks/useAuth'

export function ProtectedRoute() {
  const { isAuthenticated, isUnauthenticated, isLoading, isError } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <p>Checking authentication...</p>
  }

  if (isError) {
    return <p>Unable to verify your session. Please try again.</p>
  }

  if (isUnauthenticated || !isAuthenticated) {
    return (
        <Navigate to="/login" replace state={{from: location}} />
    )
  }

  return <Outlet />
}


/*
User → /posts/create
          │
          ▼
    ProtectedRoute
          │
          ├── Loading → Περιμένει το /auth/me
          │
          ├── Error → Εμφανίζει σφάλμα
          │
          ├── Unauthenticated → Redirect /login
          │
          └── Authenticated → Εμφανίζει τη σελίδα
*/