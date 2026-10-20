import { useAuth } from "../features/auth/hooks/useAuth"
import { useLogout } from "../features/auth/hooks/useLogout";

export function HomePage() {
  const { user, isLoading, isAuthenticated, isError } = useAuth();
  const logout = useLogout();

  if (isLoading) return <p>Checking session...</p>
  if (isError) return <p>Unable to verify session.</p>

  return (
    <main className="space-y-4 p-6">
      <h1 className="text-3xl font-bold">PawSpot</h1>

      {isAuthenticated ? (
        <>
          <p>Welcome, {user?.name}!</p>

          <button
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="rounded bg-red-600 px-4 py-2 text-white disabled:opacity-50"
          >
            {logout.isPending ? 'Logging out...' : 'Logout'}
          </button>
        </>
      ) : (
        <p>You are not logged in.</p>
      )}

      {logout.isError && (
        <p role="alert" className="text-red-600">
          Logout failed. Please try again.
        </p>
      )}
    </main>
  )
}