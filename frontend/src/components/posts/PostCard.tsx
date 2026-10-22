
import { Link } from 'react-router'
import type { PetPost } from '../../api/posts'

interface PostCardProps {
  post: PetPost
}

export function PostCard({ post }: PostCardProps) {
  const isLost = post.type === 'LOST'

  const date = new Intl.DateTimeFormat('el-GR', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(post.createdAt))

  const location = [post.area, post.city]
    .filter(Boolean)
    .join(', ')

  return (
    <article className="group overflow-hidden rounded-lg border border-[#E5E7E2] bg-white">
      <Link to={`/posts/${post.id}`} className="block h-full">
        <div className="aspect-4/3 overflow-hidden bg-[#F3F5F3]">
          {post.imageUrl ? (
            <img
              src={post.imageUrl}
              alt={post.title}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-muted">
              Χωρίς φωτογραφία
            </div>
          )}
        </div>

        <div className="p-5">
          <div className="flex items-center justify-between gap-3">
            <span
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                isLost
                  ? 'bg-[#FCE9DB] text-[#89522E]'
                  : 'bg-[#E0EDE2] text-[#25533A]'
              }`}
            >
              {isLost ? 'Χάθηκε' : 'Βρέθηκε'}
            </span>

            <time
              dateTime={post.createdAt}
              className="text-xs text-[#798079]"
            >
              {date}
            </time>
          </div>

          <h3 className="mt-4 line-clamp-2 text-lg font-semibold text-[#253B2C]">
            {post.title}
          </h3>

          <p className="mt-2 truncate text-sm text-muted">
            {location}
          </p>

          <div className="mt-5 flex items-center justify-between border-t border-[#E5E7E2] pt-4">
            <span className="text-sm font-semibold text-forest">
              Δες την αγγελία
            </span>

            <span
              aria-hidden="true"
              className="text-lg text-forest transition-transform group-hover:translate-x-1"
            >
              ↗
            </span>
          </div>
        </div>
      </Link>
    </article>
  )
}
