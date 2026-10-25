import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '../../features/auth/hooks/useAuth'
import { useLogout } from '../../features/auth/hooks/useLogout'

const navLinks = [
  { label: 'Αρχική', to: '/' },
  { label: 'Χαμένα', to: '/posts?type=LOST' },
  { label: 'Βρέθηκαν', to: '/posts?type=FOUND' },
  { label: 'Πρόσφατες αγγελίες', to: '/posts' },
];


function PawIcon() {
  return (
    <svg
      width="26"
      height="26"
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      className="shrink-0"
    >
      <ellipse cx="8.1" cy="11.3" rx="3" ry="4.1"
        transform="rotate(-24 8.1 11.3)" fill="currentColor" />
      <ellipse cx="15" cy="7.9" rx="3" ry="4.1"
        fill="currentColor" />
      <ellipse cx="22" cy="10.5" rx="3" ry="4.1"
        transform="rotate(20 22 10.5)" fill="currentColor" />
      <ellipse cx="27" cy="16.2" rx="2.5" ry="3.5"
        transform="rotate(35 27 16.2)" fill="currentColor" />
      <path
        d="M8.4 21.9C9.9 19.9 11.6 16.7 13.8 15.7C15 15.1 16.9 15.2 18.1 15.8C20.4 17 22.2 19.9 23.7 
        22C25.9 25.1 23.6 28 20.3 27.4C18.6 27.1 17.2 26.2 16 26.2C14.5 26.2 13 27.2 11.1 27.5C7.9 28 
        6.2 25.2 8.4 21.9Z"
        fill="currentColor"
      />
    </svg>
  )
}


function AuthSkeleton() {
  return (
    <div
      className="flex items-center justify-end gap-3"
      aria-label="Loading account actions"
      role="status"
    >
      <div className="h-9 w-20 animate-pulse rounded bg-stone-100" />
      <div className="h-9 w-25 animate-pulse rounded bg-stone-100" />
    </div>
  )
}

export function Header() {
  const { user, isAuthenticated, isLoading, isError } = useAuth();
  const logout = useLogout();
  const [menuOpen, setMenuOpen] = useState(false);

  const location = useLocation();
  const activeType = new URLSearchParams(location.search).get('type');

  function isNavActive(to: string) {
    if (to === '/') return location.pathname === '/';
    if (location.pathname !== '/posts') return false;

    const linkType = new URLSearchParams(to.split('?')[1] ?? '').get('type');
    return linkType === activeType;
  }

  function closeMenu() {
    setMenuOpen(false);
  }

  return (
    <header className="border-b border-line bg-white">
      <div
        className="
          mx-auto grid min-h-19 max-w-7xl
          grid-cols-[1fr_auto] items-center
          gap-4 px-5
          md:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]
          lg:px-10
        "
      >
        {/* Brand */}
        
        <Link
            to="/"
            onClick={closeMenu}
            aria-label="PawSpot home"
            className="flex w-fit shrink-0 items-center text-forest"
        >
            <span
                aria-hidden="true"
                className="
                inline-flex items-center
                font-['Nunito',sans-serif]
                text-[29px] font-extrabold
                tracking-[-0.055em]
                "
            >
                <span>P</span>

                <span className="-mx-px inline-flex translate-y-0.5 items-center">
                <PawIcon />
            </span>

            <span>wSpot</span>
        </span>
        </Link>


        {/* Desktop navigation */}
        <nav
          aria-label="Main navigation"
          className="hidden items-center justify-center gap-7 md:flex lg:gap-9"
        >
          {navLinks.map(link => {
            const active = isNavActive(link.to)

            return (
              <Link
                key={link.label}
                to={link.to}
                aria-current={active ? 'page' : undefined}
                className={`
                  whitespace-nowrap text-[13px] transition-colors hover:text-forest
                  ${active ? 'font-semibold text-forest' : 'font-medium text-[#696C66]'}
                `}
              >
                {link.label}
              </Link>
            )
          })}
        </nav>

        {/* Desktop account actions */}
        <div className="hidden min-w-0 justify-end md:flex">
          <div className="flex min-h-10 items-center justify-end gap-3">
            {isLoading ? (
              <AuthSkeleton />
            ) : isError ? (
              <Link
                to="/login"
                className="text-sm font-medium text-forest hover:underline"
              >
                Σύνδεση
              </Link>
            ) : isAuthenticated ? (
              <>
                <div
                  className="flex shrink-0 items-center gap-2"
                  aria-label={`Συνδεδεμένος ως ${user?.name ?? 'χρήστης'}`}
                >
                  <span className="relative flex size-9 items-center justify-center rounded-full bg-[#DCE9DC] text-sm font-bold text-forest">
                    {user?.name?.charAt(0).toLocaleUpperCase('el') ?? 'Χ'}
                    <span className="absolute bottom-0 right-0 size-2.5 rounded-full bg-green-600 ring-2 ring-white" />
                  </span>
                </div>

                <Link to="/posts/my" className="text-sm font-medium text-forest hover:underline">
                  Οι αγγελίες μου
                </Link>

                <Link
                  to="/posts/create"
                  className="
                    inline-flex h-10 items-center justify-center
                    whitespace-nowrap rounded px-4
                    bg-forest text-sm font-medium text-white
                    transition-colors hover:bg-[#26382E]
                  "
                >
                  Νέα αγγελία
                </Link>

                <button
                  type="button"
                  disabled={logout.isPending}
                  onClick={() => logout.mutate()}
                  className="
                    h-10 whitespace-nowrap rounded
                    border border-line px-4
                    text-sm font-medium text-forest
                    transition-colors hover:bg-canvas
                    disabled:opacity-50 cursor-pointer
                  "
                >
                  {logout.isPending ? 'Αποσύνδεση...' : 'Αποσύνδεση'}
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="
                    inline-flex h-10 items-center justify-center
                    whitespace-nowrap rounded border border-forest
                    px-4 text-sm font-medium text-forest
                    transition-colors hover:bg-canvas
                  "
                >
                  Σύνδεση
                </Link>

                <Link
                  to="/register"
                  className="
                    inline-flex h-10 items-center justify-center
                    whitespace-nowrap rounded px-5
                    bg-[#25533a] text-sm font-semibold
                    text-white transition-colors
                    hover:bg-[#26382E]
                  "
                >
                  Εγγραφή
                </Link>
              </>
            )}
          </div>
        </div>

        {/* Mobile menu button */}
        <button
          type="button"
          aria-expanded={menuOpen}
          aria-controls="mobile-navigation"
          aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
          onClick={() => setMenuOpen(open => !open)}
          className="
            justify-self-end rounded border border-line
            px-3 py-2 text-sm text-forest md:hidden
          "
        >
          {menuOpen ? 'Κλείσιμο' : 'Μενού'}
        </button>
      </div>

      {/* Mobile navigation */}
      {menuOpen && (
        <nav
          id="mobile-navigation"
          aria-label="Mobile navigation"
          className="border-t border-line px-5 py-5 md:hidden"
        >
          <div className="flex flex-col gap-5">
            {navLinks.map(link => (
              <Link
                key={link.label}
                to={link.to}
                onClick={closeMenu}
                className="text-sm font-medium text-forest"
              >
                {link.label}
              </Link>
            ))}

            <div className="border-t border-line pt-5">
              {isLoading ? (
                <AuthSkeleton />
              ) : isError ? (
                <Link to="/login" onClick={closeMenu}>
                  Σύνδεση
                </Link>
              ) : isAuthenticated ? (
                <div className="flex flex-col items-start gap-4">
                  <span className="text-sm font-medium text-forest">
                    Συνδεδεμένος ως {user?.name}
                  </span>

                  <Link to="/posts/my" onClick={closeMenu}>
                    Οι αγγελίες μου
                  </Link>

                  <Link to="/posts/create" onClick={closeMenu}>
                    Νέα αγγελία
                  </Link>

                  <button
                    type="button"
                    disabled={logout.isPending}
                    onClick={() => {
                      closeMenu()
                      logout.mutate()
                    }}
                    className="text-sm text-forest cursor-pointer"
                  >
                    Αποσύνδεση
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-start gap-4">
                  <Link to="/login" onClick={closeMenu}>
                    Σύνδεση
                  </Link>

                  <Link
                    to="/register"
                    onClick={closeMenu}
                    className="
                      rounded bg-[#DCE9DC] px-5 py-2.5
                      text-sm font-semibold text-forest
                    "
                  >
                    Εγγραφή
                  </Link>
                </div>
              )}
            </div>
          </div>
        </nav>
      )}
    </header>
  )
}
