import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { getConversation, getMessages, sendMessage } from '../api/messages'
import { useAuth } from '../features/auth/hooks/useAuth'
import { useState, type FormEvent } from 'react';

export function ConversationPage() {
    const {id} = useParams();
    const {user} = useAuth();
    const conversationId = Number(id);
    const isValidId = Number.isSafeInteger(conversationId) && conversationId > 0;

    const conversation = useQuery({
        queryKey: ['conversation', user?.id, 'detail', conversationId],
        queryFn: () => getConversation(conversationId),
        enabled: isValidId && !!user,
        retry: false
    });

    const messages = useQuery({
        queryKey: ['conversations', user?.id, 'messages', conversationId],
        queryFn: () => getMessages(conversationId),
        enabled: isValidId && !!user && conversation.isSuccess,
        retry: false,
    });

    const [draft, setDraft] = useState('')
    const queryClient = useQueryClient()

    const send = useMutation({
        mutationFn: (body: string) => sendMessage(conversationId, body),
        onSuccess: async () => {
            setDraft('')
            await queryClient.invalidateQueries({
                queryKey: ['conversations', user?.id, 'messages', conversationId],
                exact: true,
            })
        },
    })

    function handleSend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const body = draft.trim()
    if (!body || send.isPending) return

    send.mutate(body)
    }

    if (!isValidId) return <p>Μη έγκυρη συζήτηση.</p>
    if (conversation.isPending) return <p>Φόρτωση συζήτησης…</p>
    if (conversation.isError) return <p>Η συζήτηση δεν είναι διαθέσιμη.</p>

    return (
        <section className="mx-auto max-w-3xl px-5 py-10">
            <h1 className="font-serif text-3xl text-forest">
                Συζήτηση με {conversation.data.otherUserName}
            </h1>
            <Link 
                to="/messages"
                className="mt-2 block text-sm text-forest underline">
                    ← Όλες οι συζητήσεις</Link>
            <Link
                to={`/posts/${conversation.data.postId}`}
                className="mt-2 inline-block text-sm text-forest underline"
            >
                {conversation.data.postTitle}
            </Link>

            <div className="mt-8 space-y-3">
                {messages.isPending ? (
                    <p>Φόρτωση μηνυμάτων…</p>
                ) : messages.isError ? (
                    <p role="alert">Δεν ήταν δυνατή η φόρτωση των μηνυμάτων.</p>
                ) : messages.data.length === 0 ? (
                    <p>Δεν υπάρχουν ακόμη μηνύματα.</p>
                ) : (
                    [...messages.data].reverse().map((message) => (
                        <article
                            key={message.id}
                            className={`w-fit max-w-[85%] rounded-lg bg-[#E0EDE2] p-3 ${
                                message.senderId === user?.id ? 'ml-auto bg-amber-300' : 'mr-auto bg-green-500'
                            }`}
                        >
                            <p className="text-sm font-semibold">{message.senderName}</p>
                            <p className="whitespace-pre-wrap wrap-break-word">{message.body}</p>
                        </article>
                    ))
                )}
            </div>

            <form onSubmit={handleSend} className="mt-6 space-y-3">
                <label htmlFor="message-body" className="block text-sm font-medium">
                    Μήνυμα
                </label>
                <textarea
                    id="message-body"
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    maxLength={2000}
                    disabled={send.isPending}
                    rows={3}
                    className="w-full rounded-md border border-line p-3"
                />

                {send.isError && (
                    <p role="alert" className="text-sm text-red-700">
                    Το μήνυμα δεν στάλθηκε. Προσπάθησε ξανά.
                    </p>
                )}

                <button
                    type="submit"
                    disabled={!draft.trim() || send.isPending}
                    className="rounded-md bg-forest px-5 py-2 text-white disabled:opacity-50"
                >
                    {send.isPending ? 'Αποστολή…' : 'Αποστολή'}
                </button>
            </form>
        </section>
    )

}