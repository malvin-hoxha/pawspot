import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { getMyPosts, deletePost } from '../api/posts'
import { PostCard } from '../components/posts/PostCard'
import { useEffect } from 'react'

export function MyPostsPage() {

    const queryClient = useQueryClient();
    const [searchParams, setSearchParams] = useSearchParams();
    const parsedPage = Number(searchParams.get('page') ?? '0');
    const page = Number.isSafeInteger(parsedPage) && parsedPage >= 0
        ? parsedPage
        : 0;

    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ['posts', 'my', page],
        queryFn: () => getMyPosts(page),
        staleTime: 60_000
    });

    const deleteMutation = useMutation({
        mutationFn: deletePost,
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['posts']});
        }
    })

    useEffect(() => {
        if (!data || page === 0 || page < data.totalPages) return;

        const lastPage = Math.max(0, data.totalPages - 1);
        setSearchParams(
            lastPage === 0 ? {} : { page: String(lastPage) },
            { replace: true }
        );
    }, [data, page, setSearchParams]);


    function changePage(nextPage: number) {
        setSearchParams(nextPage === 0 ? {} : {page: String(nextPage)});
    }

    return (
        <section className="mx-auto max-w-7xl px-5 py-12 lg:px-10">
            <h1 className="mb-8 font-serif text-4xl text-[#253B2C]">
                Οι αγγελίες μου
            </h1>

            {deleteMutation.isError && (
                <p role="alert" className="mb-6 text-sm text-red-700">
                    Δεν ήταν δυνατή η διαγραφή της αγγελίας. Προσπάθησε ξανά.
                </p>
            )}

            {isPending ? (
                <p>Φόρτωση αγγελιών...</p>
            ) : isError ? (
                <div>
                <p>Δεν ήταν δυνατή η φόρτωση των αγγελιών σου.</p>
                <button type="button" onClick={() => void refetch()}>
                    Προσπάθησε ξανά
                </button>
                </div>
            ) : data.totalElements === 0 ? (
                <div>
                    <p>Δεν έχεις δημιουργήσει ακόμη αγγελίες.</p>
                    <Link to="/posts/create">Δημιούργησε αγγελία</Link>
                </div>
            ) : data.content.length === 0 ? (
                <p>Μετάβαση σε διαθέσιμη σελίδα...</p>
            ) : (
                <>
                <p className="mb-6 text-sm text-muted">
                    {data.totalElements} αγγελίες συνολικά
                </p>

                <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                    {data.content.map(post => (
                        <div key={post.id}>
                            <PostCard post={post} />

                            <div className="mt-3 flex items-center justify-end gap-1.5">
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (!window.confirm('Να διαγράψεις οριστικά αυτή την αγγελία;')) return;
                                        deleteMutation.reset();
                                        deleteMutation.mutate(post.id);
                                    }}
                                    disabled={deleteMutation.isPending}
                                    className="rounded-md border border-[#E7D5D2] bg-white px-4 py-2 text-sm font-semibold text-[#9A423A]
                                    transition-colors hover:bg-[#FFF4F2] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9A423A]
                                    disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
                                >
                                    Διαγραφή
                                </button>

                                <div className='rounded-md border border-[#E7D5D2] bg-white px-4 py-2 text-sm font-semibold text-gray-700'>
                                    <Link to={`/posts/${post.id}/edit`}>Επεξεργασία</Link>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {data.totalPages > 1 && (
                    <div className="mt-10 flex items-center justify-center gap-5">
                    <button
                        type="button"
                        disabled={data.first}
                        onClick={() => changePage(page - 1)}
                    >
                        Προηγούμενη
                    </button>

                    <span>Σελίδα {data.page + 1} από {data.totalPages}</span>

                    <button
                        type="button"
                        disabled={data.last}
                        onClick={() => changePage(page + 1)}
                    >
                        Επόμενη
                    </button>
                    </div>
                )}
                </>
            )}
        </section>
  )
}
