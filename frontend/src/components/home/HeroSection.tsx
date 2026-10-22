import { Link } from 'react-router'
import { useState } from 'react'

export function HeroSection() {
  const [city, setCity] = useState('')
  const [type, setType] = useState('')
  const [species, setSpecies] = useState('')

  return (
    <section
      aria-labelledby="hero-title"
      className="mx-auto max-w-7xl px-5 pt-8 pb-16 lg:px-10 lg:pt-12"
    >
      <div className="grid gap-5 lg:grid-cols-2">

        {/* Left content */}
        <div
          className="
            flex min-h-[590px] flex-col justify-between
            rounded-lg bg-white
            px-6 py-10
            sm:px-9 sm:py-14
            lg:px-10
          "
        >
          <div>
            <p className="mb-6 text-xs font-semibold tracking-[0.12em] text-[#52715C] uppercase">
              ΚΑΘΕ ΚΑΤΟΙΚΙΔΙΟ ΑΞΙΖΕΙ ΝΑ ΕΠΙΣΤΡΕΨΕΙ ΣΠΙΤΙ
            </p>

            <h1
              id="hero-title"
              className="
                max-w-lg font-serif text-[40px]
                leading-[1.12] tracking-[-0.04em]
                text-[#253B2C]
                sm:text-[52px]
                lg:text-[54px]
              "
            >
              Κάθε ζωάκι αξίζει να βρει τον δρόμο για το σπίτι.
            </h1>

            <p className="mt-7 max-w-md text-[15px] leading-7 text-[#626C63]">
              Χάθηκε το κατοικίδιό σου ή βρήκες κάποιο που αναζητά
              την οικογένειά του; Μοιράσου μια αγγελία και βοήθησε
              να ξανασμίξουν.
            </p>
          </div>

          {/* Search area */}
          <div className="mt-12">
            <h2 className="mb-4 text-xl font-semibold text-[#253B2C]">
              Βρες το κατοικίδιο που ψάχνεις
            </h2>

            <div className="rounded-xl bg-[#F3F5F3] p-4 sm:p-5">
              <form
                onSubmit={(event) => event.preventDefault()}
                className="grid gap-3 sm:grid-cols-2"
              >
                {/* City */}
                <label className="flex flex-col gap-2">
                  <span className="text-sm text-muted">
                    Περιοχή
                  </span>

                  <input
                    type="text"
                    value={city}
                    onChange={(event) => setCity(event.target.value)}
                    placeholder="Πόλη ή περιοχή"
                    className="
                      min-w-0 rounded-full border border-transparent
                      bg-white px-4 py-3 text-sm text-[#253B2C]
                      outline-none placeholder:text-[#929B93]
                      focus:border-[#52715C]
                    "
                  />
                </label>

                {/* Lost / Found */}
                <label className="flex flex-col gap-2">
                  <span className="text-sm text-muted">
                    Κατηγορία
                  </span>

                  <select
                    value={type}
                    onChange={(event) => setType(event.target.value)}
                    className="
                      min-w-0 rounded-full border border-transparent
                      bg-white px-4 py-3 text-sm text-[#253B2C]
                      outline-none focus:border-[#52715C]
                    "
                  >
                    <option value="">Όλες</option>
                    <option value="LOST">Χάθηκε</option>
                    <option value="FOUND">Βρέθηκε</option>
                  </select>
                </label>

                {/* Species */}
                <label className="flex flex-col gap-2">
                  <span className="text-sm text-[#626C63]">
                    Είδος ζώου
                  </span>

                  <select
                    value={species}
                    onChange={(event) => setSpecies(event.target.value)}
                    className="
                      min-w-0 rounded-full border border-transparent
                      bg-white px-4 py-3 text-sm text-[#253B2C]
                      outline-none focus:border-[#52715C]
                    "
                  >
                    <option value="">Όλα</option>
                    <option value="DOG">Σκύλος</option>
                    <option value="CAT">Γάτα</option>
                  </select>
                </label>

                {/* Submit */}
                <button
                  type="submit"
                  className="
                    self-end rounded-full bg-[#25533A]
                    px-5 py-3 text-sm font-semibold text-white
                    transition-colors hover:bg-[#1E432E]
                  "
                >
                  Αναζήτηση
                </button>
              </form>
            </div>

            <p className="mt-5 text-sm text-[#626C63]">
              Έχεις πληροφορίες για κάποιο κατοικίδιο;{' '}
              <Link
                to="/posts/create"
                className="font-semibold text-[#34483D] underline underline-offset-4"
              >
                Δημιούργησε αγγελία
              </Link>
            </p>
          </div>
        </div>

        {/* Right image */}
        <div
          className="
            relative min-h-[360px] overflow-hidden
            rounded-lg bg-[#E8ECE6]
            lg:min-h-[590px]
          "
        >
          <img
            src="/images/hero-pets.png"
            alt="Σκύλος που περνά χρόνο με τον ιδιοκτήτη του"
            className="absolute inset-0 h-full w-full object-cover"
          />

          <div
            className="
              absolute inset-x-0 bottom-0
              bg-gradient-to-t from-black/55 to-transparent
              px-8 pt-24 pb-8 text-white
            "
          >
            <p className="max-w-xs text-lg font-medium leading-snug">
              Μαζί, βοηθάμε κάθε ζωάκι να βρει τον δρόμο της επιστροφής.
            </p>
          </div>
        </div>
      </div>
    </section>
  )
}