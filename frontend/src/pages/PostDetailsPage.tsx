import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { getPost } from '../api/posts'

export function PostDetailsPage() {
    const { id } = useParams();
    const postId = Number(id);

    const isValidId = Number.isSafeInteger(postId) && postId > 0;

    const { data: post, isPending, isError } = useQuery({
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
        return (
            <div className="mx-auto max-w-7xl px-5 py-16 text-center lg:px-10">
                <h1 className="font-serif text-3xl text-[#253B2C]">
                Η αγγελία δεν είναι διαθέσιμη
                </h1>
                <Link to="/posts" className="mt-5 inline-block text-[#25533A] underline">
                    Επιστροφή στις αγγελίες
                </Link>
            </div>
        )
    }

    const date = new Intl.DateTimeFormat('el-GR', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    }).format(new Date(post.createdAt));

     const speciesLabel = {
        DOG: 'Σκύλος',
        CAT: 'Γάτα',
        OTHER: 'Άλλο ζώο',
    }[post.species] ?? post.species;

    return (
        <section className="mx-auto max-w-7xl px-5 py-10 lg:px-10">
            <Link
                to="/posts"
                className="mb-8 inline-flex text-sm font-medium text-forest hover:underline"
            >
                ← Επιστροφή στις αγγελίες
            </Link>

            <div className="grid items-start gap-10 lg:grid-cols-2">

                {/* Photo */}
                <div className="aspect-4/3 overflow-hidden rounded-lg bg-[#F3F5F3]">
                    {post.imageUrl ? (
                        <img
                            src={post.imageUrl}
                            alt={post.title}
                            className="h-full w-full object-cover"
                        />
                    ) : (
                        <div className="flex h-full items-center justify-center text-muted">
                            Χωρίς φωτογραφία
                        </div>
                    )}
                </div>

                {/* Details */}
                <div>
                    <div className="mb-5 flex flex-wrap items-center gap-3">
                        <span
                            className={`rounded-full px-3 py-1 text-xs font-semibold ${
                                post.type === 'LOST'
                                ? 'bg-[#FCE9DB] text-[#89522E]'
                                : 'bg-[#E0EDE2] text-[#25533A]'
                            }`}
                        >
                        {   post.type === 'LOST' ? 'Χάθηκε' : 'Βρέθηκε'}
                        </span>

                        <span className="text-sm text-muted">
                            {post.status === 'ACTIVE' ? 'Ενεργή αγγελία' : 'Ολοκληρωμένη'}
                        </span>
                    </div>

                    <h1 className="font-serif text-4xl leading-tight tracking-tight text-[#253B2C] sm:text-5xl">
                        {post.title}
                    </h1>

                    <dl className="mt-8 grid grid-cols-2 gap-6 border-y border-[#E5E7E2] py-6">
                        <div>
                            <dt className="text-sm text-muted">Τοποθεσία</dt>
                            <dd className="mt-1 font-medium text-[#253B2C]">
                                {[post.area, post.city].filter(Boolean).join(', ')}
                            </dd>
                        </div>

                        <div>
                            <dt className="text-sm text-muted">Είδος ζώου</dt>
                            <dd className="mt-1 font-medium text-[#253B2C]">
                                {speciesLabel}
                            </dd>
                        </div>

                        <div>
                            <dt className="text-sm text-muted">Δημοσιεύτηκε</dt>
                            <dd className="mt-1 font-medium text-[#253B2C]">
                                {date}
                            </dd>
                        </div>

                        <div>
                            <dt className="text-sm text-muted">Δημοσιεύτηκε από</dt>
                            <dd className="mt-1 font-medium text-[#253B2C]">
                                {post.userName}
                            </dd>
                        </div>
                    </dl>

                    <div className="mt-8">
                        <h2 className="font-serif text-2xl text-[#253B2C]">
                            Περιγραφή
                        </h2>

                        <p className="mt-4 whitespace-pre-line text-base leading-8 text-muted">
                            {post.description}
                        </p>
                    </div>
                </div>
            </div>
        </section>
    )
}