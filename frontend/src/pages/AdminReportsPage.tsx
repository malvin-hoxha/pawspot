import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
    getPendingReports,
    reportReasons,
    dismissReport,
    removeReportedPost,
} from '../api/reports'
import { useAuth } from '../features/auth/hooks/useAuth'

export function AdminReportsPage() {
    const { user } = useAuth();
    
    const queryClient = useQueryClient();
    const dismiss = useMutation({
        mutationFn: dismissReport,
        onSuccess: () => queryClient.invalidateQueries({
            queryKey: ['admin', 'post-reports', user?.id]
        })
    });

    const removal = useMutation({
        mutationFn: removeReportedPost,
        onSuccess: async () => {
            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: ['admin', 'post-reports', user?.id]
                }),
                queryClient.invalidateQueries({ queryKey: ['posts'] }),
                queryClient.invalidateQueries({ queryKey: ['conversations'] }),
                queryClient.invalidateQueries({ queryKey: ['conversation'] })
            ]);
        }
    });

    const isUpdating = dismiss.isPending || removal.isPending

    const [searchParams, setSearchParams] = useSearchParams();

    const parsedPage = Number(searchParams.get('page') ?? 0);
    const page = Number.isSafeInteger(parsedPage) && parsedPage >= 0
        ? parsedPage : 0;

    const { data, isPending, isError, refetch } = useQuery({
        queryKey: ['admin', 'post-reports', user?.id, page],
        queryFn: () => getPendingReports(page),
        enabled: user?.role === 'ADMIN',
        retry: false,
    });

    useEffect(() => {
        if (!data || page === 0 || page < data.totalPages) return;

        // Recover when the requested page no longer exists.
        const lastPage = Math.max(0, data.totalPages - 1);

        setSearchParams(previous => {
            const next = new URLSearchParams(previous);

            if (lastPage === 0) {
                next.delete('page')
            } else {
                next.set('page', String(lastPage))
            }

            return next;
        },{replace:true});
    },[data , page ,setSearchParams]);

    function changePage(nextPage: number) {
        setSearchParams(previous => {
            const next = new URLSearchParams(previous)

            if (nextPage === 0) {
                next.delete('page')
            } else {
                next.set('page', String(nextPage))
            }

            return next
        });
    }

    if (isPending) {
        return <p role="status">Φόρτωση αναφορών…</p>
    }

    if (isError) {
        return (
            <div>
                <p role="alert">Δεν ήταν δυνατή η φόρτωση των αναφορών.</p>
                <button
                    type="button"
                    onClick={() => void refetch()}
                    className="mt-3 text-forest underline"
                >
                    Προσπάθησε ξανά
                </button>
            </div>
        )
    }

    return (
        <section className="mx-auto max-w-4xl px-5 py-10">
            <h1 className="font-serif text-3xl text-forest">
                Αναφορές αγγελιών
            </h1>

            <p className="mt-2 text-sm text-muted">
                {data.totalElements} εκκρεμείς αναφορές
            </p>

            {(dismiss.isError || removal.isError) && (
                <p role="alert" className="mt-4 text-sm text-red-700">
                    Η ενέργεια δεν ολοκληρώθηκε. Προσπάθησε ξανά.
                </p>
            )}

            {data.totalElements === 0 ? (
                <p className="mt-8">Δεν υπάρχουν εκκρεμείς αναφορές.</p>
            ) : (
                <div className="mt-8 space-y-4">
                    {data.content.map(report => (
                        <article
                            key={report.id}
                            className="rounded-lg border border-line bg-white p-5"
                        >
                            <h2 className="text-lg font-semibold text-forest">
                                {report.postTitle ? (
                                    <Link
                                        to={`/posts/${report.reportedPostId}`}
                                        className="hover:underline"
                                    >
                                        {report.postTitle}
                                    </Link>
                                ) : (
                                    `Διαγραμμένη αγγελία #${report.reportedPostId}`
                                )}
                            </h2>

                            <p className="mt-2 text-sm font-medium">
                                {reportReasons[report.reason]}
                            </p>

                            {report.description && (
                                <p className="mt-3 whitespace-pre-wrap wrap-break-word">
                                    {report.description}
                                </p>
                            )}

                            <time
                                dateTime={report.createdAt}
                                className="mt-4 block text-sm text-muted"
                            >
                                {new Date(report.createdAt).toLocaleString('el-GR')}
                            </time>
                            <div className="mt-5 flex flex-wrap gap-3">
                                <button
                                    type="button"
                                    disabled={isUpdating}
                                    onClick={() => {
                                        removal.reset()
                                        dismiss.mutate(report.id)
                                    }}
                                    className="cursor-pointer rounded-md border border-line px-4 py-2
                                        text-sm font-medium text-forest hover:bg-canvas
                                        disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {dismiss.isPending && dismiss.variables === report.id
                                        ? 'Απόρριψη…'
                                        : 'Απόρριψη αναφοράς'}
                                </button>

                                <button
                                    type="button"
                                    disabled={isUpdating || report.postTitle === null}
                                    onClick={() => {
                                        const confirmed = window.confirm(
                                            'Να διαγραφεί οριστικά η αγγελία μαζί με τις συζητήσεις και τα μηνύματά της;',
                                        )

                                        if (!confirmed) return

                                        dismiss.reset()
                                        removal.mutate(report.id)
                                    }}
                                    className="cursor-pointer rounded-md border border-red-200 px-4 py-2
                                        text-sm font-medium text-red-700 hover:bg-red-50
                                        disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    {removal.isPending && removal.variables === report.id
                                        ? 'Διαγραφή…'
                                        : 'Διαγραφή αγγελίας'}
                                </button>
                            </div>
                        </article>
                    ))}
                </div>
            )}

            {data.totalPages > 1 && (
                <nav
                    aria-label="Σελίδες αναφορών"
                    className="mt-8 flex items-center justify-center gap-5"
                >
                    <button
                        type="button"
                        disabled={data.first}
                        onClick={() => changePage(page - 1)}
                        className="text-forest disabled:opacity-50"
                    >
                        Προηγούμενη
                    </button>

                    <span>
                        Σελίδα {data.page + 1} από {data.totalPages}
                    </span>

                    <button
                        type="button"
                        disabled={data.last}
                        onClick={() => changePage(page + 1)}
                        className="text-forest disabled:opacity-50"
                    >
                        Επόμενη
                    </button>
                </nav>
            )}
        </section>
    )
}