import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import axios from 'axios'
import { useLogin } from '../features/auth/hooks/useLogin'

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const from = location.state?.from;
  const redirectTo = from
    ? `${from.pathname}${from.search}${from.hash}`
    : '/';

  const invalidCredentials = axios.isAxiosError(login.error) &&
    login.error.response?.status === 401;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      await login.mutateAsync({ email: email.trim(), password });
      navigate(redirectTo, { replace: true });
    } catch {
      // Το μήνυμα εμφανίζεται μέσω του login.isError.
    }
  }

  return (
    <section className="mx-auto max-w-md py-12">
      <h1 className="font-serif text-4xl text-forest">Σύνδεση</h1>
      <p className="mt-3 text-muted">
        Καλώς ήρθες ξανά στο PawSpot.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-8 space-y-5 rounded-lg border border-line bg-white p-6"
      >
        <label className="block text-sm font-medium text-forest">
          Email
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={event => setEmail(event.target.value)}
            className="mt-2 h-11 w-full rounded-md border border-line px-3 focus:outline-none focus:ring-2 focus:ring-[#52715C]"
          />
        </label>

        <label className="block text-sm font-medium text-forest">
          Κωδικός
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={event => setPassword(event.target.value)}
            className="mt-2 h-11 w-full rounded-md border border-line px-3 focus:outline-none focus:ring-2 focus:ring-[#52715C]"
          />
        </label>

        {login.isError && (
          <p role="alert" className="text-sm text-red-700">
            {invalidCredentials
              ? 'Λάθος email ή κωδικός. Δοκίμασε ξανά.'
              : 'Δεν ήταν δυνατή η σύνδεση. Προσπάθησε ξανά.'}
          </p>
        )}

        <button
          type="submit"
          disabled={login.isPending}
          className="h-11 w-full rounded-md bg-forest font-semibold text-white transition-colors hover:bg-[#26382E] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {login.isPending ? 'Σύνδεση...' : 'Σύνδεση'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-muted">
        Δεν έχεις λογαριασμό;{' '}
        <Link to="/register" className="font-semibold text-forest underline">
          Εγγραφή
        </Link>
      </p>
    </section>
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