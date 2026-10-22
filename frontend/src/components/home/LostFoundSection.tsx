
import { Link } from 'react-router'

const categories = [
  {
    title: 'Χαμένα κατοικίδια',
    description:
      'Ανακάλυψε αγγελίες για κατοικίδια που χάθηκαν και βοήθησε να επιστρέψουν στις οικογένειές τους.',
    image: '/images/lost-pet.jpeg',
    alt: 'Σκύλος σε εξωτερικό χώρο',
    href: '/posts?type=LOST',
  },
  {
    title: 'Βρέθηκαν κατοικίδια',
    description:
      'Δες τα κατοικίδια που βρέθηκαν και βοήθησε να εντοπιστούν οι άνθρωποι που τα αναζητούν.',
    image: '/images/found-pet.jpeg',
    alt: 'Γάτα σε εξωτερικό χώρο',
    href: '/posts?type=FOUND',
  },
]

export function LostFoundSection() {
  return (
    <section
      id="lost-found"
      aria-labelledby="lost-found-title"
      className="mx-auto max-w-7xl px-5 pb-20 lg:px-10"
    >
      <div className="mb-9 max-w-2xl">
        <p className="mb-3 text-xs font-semibold tracking-[0.12em] text-[#52715C] uppercase">
          ΜΑΖΙ ΜΠΟΡΟΥΜΕ ΝΑ ΒΟΗΘΗΣΟΥΜΕ
        </p>

        <h2
          id="lost-found-title"
          className="font-serif text-4xl leading-tight tracking-tight text-[#253B2C] sm:text-5xl"
        >
          Μια μικρή βοήθεια μπορεί να κάνει μεγάλη διαφορά.
        </h2>

        <p className="mt-4 text-base leading-7 text-muted">
          Κάθε αγγελία είναι μια ευκαιρία να ξαναβρεθεί ένα κατοικίδιο
          με την οικογένειά του.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {categories.map(category => (
          <article
            key={category.href}
            className="group overflow-hidden rounded-lg border border-[#E5E7E2] bg-white"
          >
            <Link to={category.href} className="block">
              <div className="aspect-16/10 overflow-hidden bg-[#E8ECE6]">
                <img
                  src={category.image}
                  alt={category.alt}
                  loading="lazy"
                  className="
                    h-full w-full object-cover
                    transition-transform duration-300
                    group-hover:scale-[1.03]
                  "
                />
              </div>

              <div className="p-7 sm:p-8">
                <div className="flex items-start justify-between gap-4">
                  <h3 className="font-serif text-2xl text-[#253B2C] sm:text-3xl">
                    {category.title}
                  </h3>

                  <span
                    aria-hidden="true"
                    className="
                      flex h-10 w-10 shrink-0 items-center justify-center
                      rounded-full border border-line
                      text-lg text-forest
                      transition-colors
                      group-hover:bg-[#25533A]
                      group-hover:text-white
                    "
                  >
                    ↗
                  </span>
                </div>

                <p className="mt-3 max-w-md text-sm leading-6 text-muted">
                  {category.description}
                </p>

                <span className="mt-5 inline-block text-sm font-semibold text-forest">
                  Προβολή αγγελιών
                </span>
              </div>
            </Link>
          </article>
        ))}
      </div>
    </section>
  )
}
