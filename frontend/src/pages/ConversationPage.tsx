import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type UIEvent,
} from 'react'
import { Link, useParams } from 'react-router'
import { getConversation, getMessages, sendMessage } from '../api/messages'
import { useAuth } from '../features/auth/hooks/useAuth'

export function ConversationPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const conversationId = Number(id)
  const userId = user?.id
  const isValidId = Number.isSafeInteger(conversationId) && conversationId > 0

  const [draft, setDraft] = useState('')
  const queryClient = useQueryClient()

  const viewportRef = useRef<HTMLDivElement>(null)
  const followLatest = useRef(true)
  const previousLayout = useRef<{
    conversationId: number
    userId: number | undefined
    pages: number
    height: number
  } | null>(null)

  const conversation = useQuery({
    queryKey: ['conversation', userId, 'detail', conversationId],
    queryFn: () => getConversation(conversationId),
    enabled: isValidId && !!user,
    retry: false,
  })

  const messagesKey = [
    'conversations',
    userId,
    'messages',
    conversationId,
    'infinite',
  ] as const

  const messages = useInfiniteQuery({
    queryKey: messagesKey,
    queryFn: ({ pageParam }) => getMessages(conversationId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.last ? undefined : lastPage.page + 1,
    enabled: isValidId && !!user && conversation.isSuccess,
    retry: false,
  })

  const history = Array.from(
    new Map(
      (messages.data?.pages.flatMap((page) => page.content) ?? []).map(
        (message) => [message.id, message] as const,
      ),
    ).values(),
  ).reverse()

  useLayoutEffect(() => {
    const viewport = viewportRef.current

    if (!viewport || !messages.data) {
      previousLayout.current = null
      return
    }

    const pages = messages.data.pages.length
    const previous = previousLayout.current

    if (
      !previous ||
      previous.conversationId !== conversationId ||
      previous.userId !== userId
    ) {
      viewport.scrollTop = viewport.scrollHeight
      followLatest.current = true
    } else if (pages > previous.pages) {
      // Keep the visible messages in place when older messages are prepended.
      viewport.scrollTop += viewport.scrollHeight - previous.height
    } else if (followLatest.current) {
      viewport.scrollTop = viewport.scrollHeight
    }

    previousLayout.current = {
      conversationId,
      userId,
      pages,
      height: viewport.scrollHeight,
    }
  }, [messages.data, conversationId, userId, conversation.isSuccess])

  const send = useMutation({
    mutationFn: (body: string) => sendMessage(conversationId, body),
    onSuccess: async () => {
      setDraft('')
      followLatest.current = true

      await queryClient.invalidateQueries({
        queryKey: messagesKey,
        exact: true,
      })
    },
  })

  function handleHistoryScroll(event: UIEvent<HTMLDivElement>) {
    const { scrollTop, scrollHeight, clientHeight } = event.currentTarget

    followLatest.current = scrollHeight - scrollTop - clientHeight <= 48

    if (scrollTop <= 80 && messages.hasNextPage && !messages.isFetching) {
      void messages.fetchNextPage({ cancelRefetch: false })
    }
  }

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

      <Link to="/messages" className="mt-2 block text-sm text-forest underline">
        ← Όλες οι συζητήσεις
      </Link>

      <Link
        to={`/posts/${conversation.data.postId}`}
        className="mt-2 inline-block text-sm text-forest underline"
      >
        {conversation.data.postTitle}
      </Link>

      <div
        ref={viewportRef}
        onScroll={handleHistoryScroll}
        tabIndex={0}
        role="region"
        aria-label="Ιστορικό μηνυμάτων"
        className="mt-8 h-[55vh] max-h-[34rem] space-y-3 overflow-y-auto rounded-lg border border-line p-4 [overflow-anchor:none]"
      >
        {messages.isPending ? (
          <p>Φόρτωση μηνυμάτων…</p>
        ) : messages.isError && !messages.data ? (
          <p role="alert">Δεν ήταν δυνατή η φόρτωση των μηνυμάτων.</p>
        ) : history.length === 0 ? (
          <p>Δεν υπάρχουν ακόμη μηνύματα.</p>
        ) : (
          history.map((message) => (
            <article
              key={message.id}
              className={`w-fit max-w-[85%] rounded-lg p-3 ${
                message.senderId === userId
                  ? 'ml-auto bg-amber-300'
                  : 'mr-auto bg-green-500'
              }`}
            >
              <p className="text-sm font-semibold">{message.senderName}</p>
              <p className="whitespace-pre-wrap wrap-break-word">
                {message.body}
              </p>
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