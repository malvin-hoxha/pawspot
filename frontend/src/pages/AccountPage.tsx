import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import axios from 'axios'
import { deleteAccount } from '../features/auth/api/auth.api'

export function AccountPage() {
    const [password, setPassword] = useState('');
    const queryClient = useQueryClient();
    const navigate = useNavigate();

    const deletion = useMutation({
        mutationFn: deleteAccount,
        onSuccess: async () => {
            await queryClient.cancelQueries();
            queryClient.removeQueries({ queryKey: ['posts'] });
            queryClient.setQueryData(['auth','me'], null);
        }
    });

    const wrongPassword = axios.isAxiosError(deletion.error) && 
        deletion.error.config?.url === '/users/me' &&
        deletion.error.response?.status === 401;

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (!window.confirm(
        'Η διαγραφή του λογαριασμού και των αγγελιών σου είναι οριστική. Θέλεις να συνεχίσεις;'
        )) return;

        try {
            await deletion.mutateAsync(password)
            navigate('/', { replace: true })
        } catch {
        // Το σφάλμα εμφανίζεται παρακάτω.
        }
    }

    return (
        <section className="mx-auto max-w-xl px-5 py-12">
            <h1 className="mb-6 font-serif text-4xl text-[#253B2C]">
                Ο λογαριασμός μου
            </h1>

            <div className="rounded-lg border border-red-200 bg-white p-6">
                <h2 className="mb-3 text-xl font-semibold text-red-800">
                    Διαγραφή λογαριασμού
                </h2>

                <p className="mb-6 text-sm text-gray-700">
                    Θα διαγραφούν οριστικά ο λογαριασμός σου και οι αγγελίες σου.
                    Πληκτρολόγησε τον κωδικό σου για επιβεβαίωση.
                </p>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <label htmlFor="delete-password" className="text-sm font-medium">
                        Κωδικός πρόσβασης
                    </label>

                    <input
                        id="delete-password"
                        type="password"
                        autoComplete="current-password"
                        required
                        value={password}
                        onChange={event => setPassword(event.target.value)}
                        className="h-11 rounded-md border border-gray-300 px-3"
                    />

                    {deletion.isError && (
                        <p role="alert" className="text-sm text-red-700">
                        {wrongPassword
                            ? 'Ο κωδικός δεν είναι σωστός.'
                            : 'Δεν ήταν δυνατή η διαγραφή. Προσπάθησε ξανά.'}
                        </p>
                    )}

                    <div className="flex items-center gap-4">
                        <button
                        type="submit"
                        disabled={deletion.isPending}
                        className="rounded-md bg-red-700 px-4 py-2.5 font-medium text-white disabled:opacity-50"
                        >
                        {deletion.isPending ? 'Διαγραφή...' : 'Διαγραφή λογαριασμού'}
                        </button>

                        <Link to="/" className="text-sm text-forest hover:underline">
                        Ακύρωση
                        </Link>
                    </div>
                </form>
            </div>
        </section>
    )
}