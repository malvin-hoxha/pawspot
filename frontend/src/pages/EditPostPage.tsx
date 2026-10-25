import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { getPost, updatePost } from '../api/posts'
import type { PetPost, UpdatePostInput } from '../api/posts'
import { useAuth } from '../features/auth/hooks/useAuth'

export function EditPostPage() {
    const { id } = useParams();
    const { user } = useAuth();
    const postId = Number(id);
    const isValidId = Number.isSafeInteger(postId) && postId > 0;

    const {data:post, isPending, isError} = useQuery({
        queryKey: ['posts', 'detail', postId],
        queryFn: () => getPost(postId),
        enabled: isValidId,
        staleTime: 60_000,
        retry: false,
    });

    if (!isValidId) {
        return <p className="p-10 text-center">Μη έγκυρη αγγελία.</p>
    }

    if (isPending) {
        return (
            <div className="mx-auto max-w-7xl animate-pulse px-5 py-12 lg:px-10">
                <div className="grid gap-10 lg:grid-cols-2">
                <div className="aspect-4/3 rounded-lg bg-[#E8ECE6]" />
                    <div className="space-y-5">
                        <div className="h-7 w-24 rounded bg-[#E8ECE6]" />
                        <div className="h-12 w-4/5 rounded bg-[#E8ECE6]" />
                        <div className="h-5 w-2/3 rounded bg-[#E8ECE6]" />
                    </div>
                </div>
            </div>
        )
    }

    if (isError || !post) {
        return <p className="p-10 text-center">Δεν βρέθηκε η αγγελία.</p>
    }

    if (post.userId !== user?.id) {
        return <p className="p-10 text-center">Δεν μπορείς να επεξεργαστείς αυτή την αγγελία.</p>
    }

    return <EditPostForm key={post.id} post={post} />
}

const fieldClass =
  'mt-2 w-full rounded-md border border-[#D5DDD4] bg-white px-4 py-2.5 text-[#253B2C] outline-none focus:border-[#25533A] focus:ring-2 focus:ring-[#DCE9DC]'

function EditPostForm({ post }: { post: PetPost }) {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [validationError, setValidationError] = useState('');

    const [values, setValues] = useState({
        type: post.type,
        title: post.title,
        description: post.description,
        species: post.species,
        city: post.city,
        area: post.area ?? '',
        status: post.status,
    });

    const updateMutation = useMutation({
        mutationFn: (changes: UpdatePostInput) => updatePost(post.id, changes),
        onSuccess: async updated => { //updated: the PetPost returned from the patch
            await queryClient.invalidateQueries({queryKey: ['posts']});
            void navigate(`/posts/${updated.id}`);
        }
    });

    // Στέλνουμε μόνο τα πεδία που άλλαξαν.
    const changes: UpdatePostInput = {};

    if (values.type !== post.type) changes.type = values.type;
    if (values.title.trim() !== post.title) changes.title = values.title.trim();
    if (values.description.trim() !== post.description) {
        changes.description = values.description.trim();
    }
    if (values.species !== post.species) changes.species = values.species;
    if (values.city.trim() !== post.city) changes.city = values.city.trim();
    if (values.area.trim() !== (post.area ?? '')) {
        changes.area = values.area.trim();
    }
    if (values.status !== post.status) changes.status = values.status;

    const hasChanges = Object.keys(changes).length > 0;

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
         if (
            !values.title.trim() ||
            !values.description.trim() ||
            !values.city.trim()
        ) {
            setValidationError('Συμπλήρωσε τίτλο, περιγραφή και πόλη.')
            return
        }

        if (!hasChanges || updateMutation.isPending) return;

        setValidationError('');
        updateMutation.mutate(changes);

    }

    return (
        <section className="mx-auto max-w-3xl px-5 py-12">
            <h1 className="font-serif text-4xl text-[#253B2C]">
                Επεξεργασία αγγελίας
            </h1>
            <p className="mt-3 text-sm text-muted">
                Άλλαξε τα στοιχεία της αγγελίας σου και αποθήκευσέ τα.
            </p>

            <form
                onSubmit={handleSubmit}
                className="mt-8 space-y-6 rounded-lg border border-[#E5E7E2] bg-white p-6 sm:p-8"
            >
                <div className="grid gap-6 sm:grid-cols-2">
                <label className="block text-sm font-medium text-[#253B2C]">
                    Τύπος αγγελίας
                    <select
                        value={values.type}
                        onChange={event =>
                            setValues(previous => ({
                            ...previous,
                            type: event.target.value as PetPost['type'],
                            }))
                        }
                        className={fieldClass}
                    >
                        <option value="LOST">Χάθηκε</option>
                        <option value="FOUND">Βρέθηκε</option>
                    </select>
                </label>

                <label className="block text-sm font-medium text-[#253B2C]">
                    Ζώο
                    <select
                        value={values.species}
                        onChange={event =>
                            setValues(previous => ({
                            ...previous,
                            species: event.target.value as PetPost['species'],
                            }))
                        }
                        className={fieldClass}
                    >
                        <option value="DOG">Σκύλος</option>
                        <option value="CAT">Γάτα</option>
                        <option value="OTHER">Άλλο</option>
                    </select>
                </label>
                </div>

                <label className="block text-sm font-medium text-[#253B2C]">
                    Τίτλος
                    <input
                        required
                        maxLength={255}
                        value={values.title}
                        onChange={event =>
                            setValues(previous => ({
                                ...previous,
                                title: event.target.value,
                            }))
                        }
                        className={fieldClass}
                    />
                </label>

                <label className="block text-sm font-medium text-[#253B2C]">
                    Περιγραφή
                    <textarea
                        required
                        rows={6}
                        maxLength={2000}
                        value={values.description}
                        onChange={event =>
                            setValues(previous => ({
                                ...previous,
                                description: event.target.value,
                            }))
                        }
                        className={fieldClass}
                    />
                </label>

                <div className="grid gap-6 sm:grid-cols-2">
                    <label className="block text-sm font-medium text-[#253B2C]">
                        Πόλη
                        <input
                            required
                            maxLength={255}
                            value={values.city}
                            onChange={event =>
                                setValues(previous => ({
                                ...previous,
                                city: event.target.value,
                                }))
                            }
                            className={fieldClass}
                        />
                    </label>

                    <label className="block text-sm font-medium text-[#253B2C]">
                        Περιοχή
                        <input
                            maxLength={255}
                            value={values.area}
                            onChange={event =>
                                setValues(previous => ({
                                ...previous,
                                area: event.target.value,
                                }))
                            }
                            className={fieldClass}
                        />
                    </label>
                </div>

                    <label className="block text-sm font-medium text-[#253B2C]">
                        Κατάσταση
                        <select
                            value={values.status}
                            onChange={event =>
                                setValues(previous => ({
                                    ...previous,
                                    status: event.target.value as PetPost['status'],
                                }))
                            }
                            className={fieldClass}
                        >
                            <option value="ACTIVE">Ενεργή</option>
                            <option value="RESOLVED">Επιλύθηκε</option>
                        </select>
                    </label>

                {validationError && (
                    <p role="alert" className="text-sm text-[#9A423A]">
                        {validationError}
                    </p>
                )}

                {updateMutation.isError && (
                    <p role="alert" className="text-sm text-[#9A423A]">
                        Δεν αποθηκεύτηκαν οι αλλαγές. Προσπάθησε ξανά.
                    </p>
                )}

                <div className="flex flex-wrap items-center justify-end gap-4 border-t border-[#E5E7E2] pt-6">
                    <Link
                        to={`/posts/${post.id}`}
                        className="text-sm font-medium text-forest hover:underline"
                    >
                        Ακύρωση
                    </Link>

                    <button
                        type="submit"
                        disabled={!hasChanges || updateMutation.isPending}
                        className="rounded-md bg-[#25533A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#1E432E]
                        disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {updateMutation.isPending ? 'Αποθήκευση...' : 'Αποθήκευση αλλαγών'}
                    </button>
                </div>
            </form>
        </section>
    )
}
