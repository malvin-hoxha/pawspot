import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { getPosts } from '../../api/posts'
import { PostCard } from '../posts/PostCard'

export function LatestPostsSection() {
    const {data, isPending, isError} = useQuery({
        queryKey: ['posts', 'latest'],
        queryFn: () =>
            getPosts({
                page: 0,
                size: 6,
                sortBy: 'createdAt',
                direction: 'desc'
            }),
        staleTime: 60_000
    });

    return (
        <section
            id="latest"
            aria-labelledby="latest-posts-title"
            className="mx-auto max-w-7xl px-5 pb-24 lg:px-10"
        >
            <div className="mb-9 flex flex-wrap items-end justify-between gap-5">
                <div className="max-w-2xl">
                    <p className="mb-3 text-xs font-semibold tracking-[0.12em] text-[#52715C] uppercase">
                        ΜΕΙΝΕ ΕΝΗΜΕΡΩΜΕΝΟΣ
                    </p>

                    <h2
                        id="latest-posts-title"
                        className="font-serif text-4xl leading-tight tracking-tight text-[#253B2C] sm:text-5xl"
                    >
                        Πρόσφατες αγγελίες
                    </h2>

                    <p className="mt-4 text-base leading-7 text-muted">
                        Δες τα πιο πρόσφατα περιστατικά και βοήθησε
                        ένα κατοικίδιο να επιστρέψει στο σπίτι του.
                    </p>
                </div>

                <Link
                    to="/posts"
                    className="text-sm font-semibold text-forest underline underline-offset-4 hover:text-[#1E432E]"
                    >
                    Όλες οι αγγελίες ↗
                </Link>
            </div>

            {isPending ? (
                <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                    {Array.from({ length: 6 }).map((_, index) => (
                        <div
                        key={index}
                        role="status"
                        aria-label="Φόρτωση αγγελίας"
                        className="overflow-hidden rounded-lg border border-[#E5E7E2] bg-white"
                        >
                        <div className="aspect-4/3 animate-pulse bg-[#E8ECE6]" />
                        <div className="space-y-4 p-5">
                            <div className="h-5 w-20 animate-pulse rounded bg-[#F0F1ED]" />
                            <div className="h-6 w-4/5 animate-pulse rounded bg-[#F0F1ED]" />
                            <div className="h-4 w-1/2 animate-pulse rounded bg-[#F0F1ED]" />
                        </div>
                        </div>
                    ))}
                </div>
            ) : isError ? (
                    <div className="rounded-lg border border-[#E5E7E2] p-8 text-center">
                        <p className="text-muted">
                            Δεν ήταν δυνατή η φόρτωση των αγγελιών.
                        </p>
                    </div>
            ) : data.content.length === 0 ? (
                    <div className="rounded-lg border border-[#E5E7E2] p-8 text-center">
                        <p className="text-muted">
                            Δεν υπάρχουν ακόμη δημοσιευμένες αγγελίες.
                        </p>
                    </div>
            ) : (
                    <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                        {data.content.map(post => (
                            <PostCard key={post.id} post={post} />
                        ))}
                    </div>
                )
            }
        </section>
  )
}