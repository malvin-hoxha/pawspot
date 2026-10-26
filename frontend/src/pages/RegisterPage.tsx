import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import axios from 'axios'
import { register } from '../features/auth/api/auth.api'

export function RegisterPage() {
    const [formError, setFormError] = useState('');

    const registration = useMutation({
        mutationFn: register
    });

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setFormError('');

        const form = new FormData(event.currentTarget);

        const name = String(form.get('name') ?? '').trim();
        const email = String(form.get('email') ?? '').trim();
        const password = String(form.get('password') ?? '');
        const confirmation = String(form.get('confirmation') ?? '');

        if (!name) {
            setFormError('Συμπλήρωσε το όνομά σου.');
            return;
        }

        if (password !== confirmation) {
            setFormError('Οι κωδικοί δεν ταιριάζουν.');
            return;
        }

        if (new TextEncoder().encode(password).length > 72) {
            setFormError('Ο κωδικός είναι πολύ μεγάλος.');
            return;
        }

        registration.mutate({ name, email, password });
    }

    if (registration.isSuccess) {
        return (
        <section className="mx-auto max-w-md py-12">
            <h1 className="font-serif text-3xl text-forest">Ο λογαριασμός δημιουργήθηκε</h1>
            <p className="mt-4 text-muted">Μπορείς τώρα να συνδεθείς στο PawSpot.</p>
            <Link to="/login" className="mt-6 inline-block font-semibold text-forest underline">
                Μετάβαση στη σύνδεση
            </Link>
        </section>
        )
    }

    const emailExists = axios.isAxiosError(registration.error) && registration.error.response?.status === 409;

    return (
        <section className="mx-auto max-w-md py-12">
            <h1 className="font-serif text-4xl text-forest">Δημιουργία λογαριασμού</h1>
            <p className="mt-3 text-muted">Δημοσίευσε αγγελίες και βοήθησε ζώα να επιστρέψουν σπίτι.</p>

            <form onSubmit={handleSubmit} className="mt-8 space-y-5 rounded-lg border border-line bg-white p-6">
                <label className="block text-sm font-medium text-forest">
                Όνομα
                    <input
                        name="name"
                        type="text"
                        autoComplete="name"
                        required
                        className="mt-2 h-11 w-full rounded-md border border-line px-3"
                    />
                </label>

                <label className="block text-sm font-medium text-forest">
                    Email
                    <input
                        name="email"
                        type="email"
                        autoComplete="email"
                        required
                        className="mt-2 h-11 w-full rounded-md border border-line px-3"
                    />
                </label>

                <label className="block text-sm font-medium text-forest">
                    Κωδικός
                    <input
                        name="password"
                        type="password"
                        autoComplete="new-password"
                        minLength={6}
                        required
                        className="mt-2 h-11 w-full rounded-md border border-line px-3"
                    />
                    <span className="mt-1 block text-xs text-muted">Τουλάχιστον 6 χαρακτήρες.</span>
                </label>

                <label className="block text-sm font-medium text-forest">
                    Επιβεβαίωση κωδικού
                    <input
                        name="confirmation"
                        type="password"
                        autoComplete="new-password"
                        required
                        className="mt-2 h-11 w-full rounded-md border border-line px-3"
                    />
                </label>

                {(formError || registration.isError) && (
                    <p role="alert" className="text-sm text-red-700">
                        {formError || (emailExists
                        ? 'Υπάρχει ήδη λογαριασμός με αυτό το email.'
                        : 'Δεν ήταν δυνατή η εγγραφή. Προσπάθησε ξανά.')}
                    </p>
                )}

                <button
                    type="submit"
                    disabled={registration.isPending}
                    className="h-11 w-full rounded-md bg-forest font-semibold text-white disabled:opacity-50 cursor-pointer"
                >
                    {registration.isPending ? 'Δημιουργία...' : 'Εγγραφή'}
                </button>
            </form>

            <p className="mt-6 text-center text-sm text-muted">
                Έχεις ήδη λογαριασμό;{' '}
                <Link to="/login" className="font-semibold text-forest underline">Σύνδεση</Link>
            </p>
        </section>
    )
}