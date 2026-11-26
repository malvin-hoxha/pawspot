import { Link } from 'react-router'

const legalLinks = [
  { label: 'Πολιτική Απορρήτου', to: '/privacy' },
  { label: 'Όροι Χρήσης', to: '/terms' },
  { label: 'Cookies & Αποθήκευση', to: '/cookies' },
]

export function Footer() {
  return (
    <footer className="border-t border-line bg-white">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-7 sm:flex-row sm:items-center sm:justify-between lg:px-10">
        <p className="text-sm font-semibold text-forest">PawSpot</p>

        <nav aria-label="Νομικές πληροφορίες">
          <ul className="flex flex-col gap-3 text-sm sm:flex-row sm:flex-wrap sm:gap-x-6">
            {legalLinks.map(link => (
              <li key={link.to}>
                <Link
                  to={link.to}
                  className="rounded-sm text-muted underline-offset-4 transition-colors hover:text-forest hover:underline"
                >
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </footer>
  )
}
