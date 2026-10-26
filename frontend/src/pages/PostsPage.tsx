import { useQuery } from '@tanstack/react-query'
import { getPosts } from '../api/posts'
import { PostCard } from '../components/posts/PostCard'
import { useSearchParams } from 'react-router'
import { PostsFilters } from '../components/posts/PostsFilters'
import type { PostFilters } from '../api/posts'

export function PostsPage() {
    
    const [searchParams, setSearchParams] = useSearchParams();
    const type = searchParams.get('type');
    const species = searchParams.get('species');
    const city = searchParams.get('city')?.trim() || undefined;
    const pageParam = searchParams.get('page');
    const parsedPage = Number(pageParam ?? '0');

    const search = searchParams.get('search')?.trim() || undefined;

    const page = Number.isSafeInteger(parsedPage) && parsedPage >= 0
        ? parsedPage
        : 0;

    const validType = type === 'LOST' || type === 'FOUND' ? type : undefined;
    const validSpecies = species === 'DOG' || species === 'CAT' || species === 'OTHER'
        ? species
        : undefined;

    const filters: PostFilters = {
        type: validType,
        species: validSpecies,
        city,
        search: search,
        page: page,
        size: 12,
        sortBy: 'createdAt',
        direction: 'desc',
    }

    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ['posts', 'all', filters],
        queryFn: () => getPosts(filters),
        staleTime: 60_000,
    })

    function changePage(nextPage: number) {
        setSearchParams(previous => {
            const next = new URLSearchParams(previous);
            if(nextPage === 0) {
                next.delete('page');
            } else {
                next.set('page', String(nextPage));
            }

            return next;
        });
    }

    return (
        <section className="mx-auto max-w-7xl px-5 py-12 lg:px-10">
        <div className="mb-10 max-w-2xl">
            <p className="mb-3 text-xs font-semibold tracking-[0.12em] text-[#52715C] uppercase">
            PAWSPOT
            </p>

            <h1 className="font-serif text-4xl tracking-tight text-[#253B2C] sm:text-5xl">
            Όλες οι αγγελίες
            </h1>

            <p className="mt-4 text-base leading-7 text-muted">
            Ανακάλυψε αγγελίες για κατοικίδια που χάθηκαν ή βρέθηκαν
            και βοήθησε να επιστρέψουν στις οικογένειές τους.
            </p>
        </div>

        <PostsFilters />

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
            <p className="mb-4 text-muted">
                Δεν ήταν δυνατή η φόρτωση των αγγελιών.
            </p>
            <button
                type="button"
                onClick={() => void refetch()}
                className="rounded-md bg-[#25533A] px-5 py-2.5 text-sm font-semibold text-white hover:bg-[#1E432E]"
            >
                Προσπάθησε ξανά
            </button>
            </div>
        ) : data.content.length === 0 ? (
            <div className="rounded-lg border border-[#E5E7E2] p-8 text-center">
            <p className="text-muted">
                Δεν υπάρχουν ακόμη δημοσιευμένες αγγελίες.
            </p>
            </div>
        ) : (
            <>
            <p className="mb-6 text-sm text-muted">
                {data.totalElements} αγγελίες συνολικά
            </p>

            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                {data.content.map(post => (
                    <PostCard key={post.id} post={post} />
                ))}
            </div>
            <div className="mt-10 flex items-center justify-center gap-5">
                <button
                    type="button"
                    disabled={data.first}
                    onClick={() => changePage(page - 1)}
                    className="rounded-md border border-line px-5 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-40"
                >
                    Προηγούμενη
                </button>

                <span className="text-sm text-muted">
                    Σελίδα {page + 1} από {data.totalPages}
                </span>

                <button
                    type="button"
                    disabled={data.last}
                    onClick={() => changePage(page + 1)}
                    className="rounded-md bg-[#25533A] px-5 py-2.5 text-sm text-white disabled:cursor-not-allowed disabled:opacity-40"
                >
                    Επόμενη
                </button>
            </div>
            </>
        )}
        </section>
  )
}