import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { getConversations } from '../api/messages'
import { useAuth } from '../features/auth/hooks/useAuth'

export function MessagesPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)

  const conversations = useQuery({
    queryKey: ['conversations', user?.id, 'inbox', page],
    queryFn: () => getConversations(page),
    enabled: !!user,
  })

  if (conversations.isPending) return <p>Φόρτωση συζητήσεων…</p>
  if (conversations.isError) return <p role="alert">Δεν φορτώθηκαν οι συζητήσεις.</p>

  const { content, first, last } = conversations.data

  return (
    <section className="mx-auto max-w-3xl px-5 py-10">
      <h1 className="mb-6 font-serif text-3xl text-forest">Μηνύματα</h1>

      {content.length === 0 ? (
        <p>Δεν υπάρχουν συζητήσεις σε αυτή τη σελίδα.</p>
      ) : (
        <ul className="space-y-3">
          {content.map((conversation) => (
            <li key={conversation.id}>
              <Link
                to={`/messages/${conversation.id}`}
                className="block rounded-lg border border-line bg-white p-4 hover:border-forest"
              >
                <p className="font-semibold text-forest">
                  {conversation.otherUserName}
                </p>
                <p className="mt-1 text-sm text-muted">{conversation.postTitle}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {(!first || !last) && (
        <div className="mt-6 flex justify-between">
          <button type="button" disabled={first} onClick={() => setPage(page - 1)}>
            Προηγούμενη
          </button>
          <button type="button" disabled={last} onClick={() => setPage(page + 1)}>
            Επόμενη
          </button>
        </div>
      )}
    </section>
  )
}