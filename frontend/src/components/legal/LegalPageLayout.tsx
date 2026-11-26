import type { ReactNode } from 'react'

type LegalPageLayoutProps = {
  title: string
  description: string
  children: ReactNode
}

type LegalSectionProps = {
  title: string
  children: ReactNode
}

export function LegalPageLayout({
  title,
  description,
  children,
}: LegalPageLayoutProps) {
  return (
    <article className="mx-auto max-w-4xl py-8 sm:py-12">
      <header className="border-b border-line pb-8">
        <p className="text-sm font-bold uppercase tracking-[0.12em] text-muted">
          Νομικές πληροφορίες
        </p>
        <h1 className="mt-3 font-editorial text-4xl leading-tight text-forest sm:text-5xl">
          {title}
        </h1>
        <p className="mt-4 max-w-3xl text-base leading-7 text-muted sm:text-lg">
          {description}
        </p>

        <div
          role="note"
          className="mt-6 rounded-card border border-[#C5A969] bg-[#FFF9E9] px-5 py-4 font-semibold text-[#604C1F]"
        >
          Προσχέδιο — το περιεχόμενο δεν έχει ακόμη οριστικοποιηθεί.
        </div>
      </header>

      <div className="mt-10 space-y-12 text-[1rem] leading-7 text-ink">
        {children}
      </div>
    </article>
  )
}

export function LegalSection({ title, children }: LegalSectionProps) {
  return (
    <section>
      <h2 className="font-editorial text-2xl leading-tight text-forest sm:text-3xl">
        {title}
      </h2>
      <div className="mt-4 space-y-4">{children}</div>
    </section>
  )
}

export function LegalTable({ children }: { children: ReactNode }) {
  return (
    <div className="max-w-full overflow-x-auto rounded-card border border-line bg-white">
      <table className="min-w-[44rem] border-collapse text-left text-sm leading-6">
        {children}
      </table>
    </div>
  )
}

export const legalLinkClass =
  'rounded-sm font-semibold text-forest underline decoration-1 underline-offset-4 hover:decoration-2'

export const legalListClass = 'list-disc space-y-2 pl-6 marker:text-forest'
