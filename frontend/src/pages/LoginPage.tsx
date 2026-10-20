import { useState } from "react";
import { useNavigate } from "react-router";
import { useLogin } from "../features/auth/hooks/useLogin";

export function LoginPage() {
  const navigate = useNavigate();
  const login = useLogin();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      await login.mutateAsync({ email, password });
      navigate("/");
    } catch {
      //
    }
  }

  return (
    <main className="mx-auto max-w-md space-y-6">
      <h1 className="text-3xl font-bold">Login</h1>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="email" className="block mb-1">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={event => setEmail(event.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        <div>
          <label htmlFor="password" className="block mb-1">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={event => setPassword(event.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        {login.isError && (
          <p role="alert" className="text-red-600">
            Login failed. Check your credentials and try again.
          </p>
        )}

        <button
          type="submit"
          disabled={login.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {login.isPending ? 'Signing in...' : 'Login'}
        </button>
      </form>
    </main>
  )
}

/*
  LoginPage
    ↓
  useLogin().mutateAsync()
      ↓
  login() → initializeCsrf()
      ↓
  POST /api/auth/login
      ↓
  Spring Boot ελέγχει credentials
      ↓
  JWT HttpOnly cookies
      ↓
  TanStack Query ενημερώνει ['auth', 'me']
      ↓
  navigate('/')
*/