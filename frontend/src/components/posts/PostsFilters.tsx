import { useSearchParams } from 'react-router'
import { useState, useEffect } from 'react'

export function PostsFilters() {
    const [searchParams, setSearchParams] = useSearchParams();

    const [cityInput, setCityInput] = useState(
        searchParams.get('city') ?? ''
    );

    const cityFromUrl = searchParams.get('city') ?? '';

    const [searchInput, setSearchInput] = useState(searchParams.get('search') ?? '');
    const searchFromUrl = searchParams.get('search') ?? '';

    useEffect(() => {
      setSearchInput(searchFromUrl);
    }, [searchFromUrl]);

    useEffect(() => {
        setCityInput(cityFromUrl)
    }, [cityFromUrl]);

    function updateFilter(key: string, value: string) {
        setSearchParams(previous => {
            const next = new URLSearchParams(previous);

            if(value) {
                next.set(key, value);
            } else {
                next.delete(key);
            }

            next.delete('page');

            return next;
        });
    }


  return (
    <div className="mb-9 rounded-lg border border-[#E5E7E2] bg-white p-5">
      <form
        className="grid gap-4 md:grid-cols-3"
        onSubmit={e => {
          e.preventDefault()

          setSearchParams(previous => {
            const next = new URLSearchParams(previous)
            const city = cityInput.trim()
            const search = searchInput.trim()

            if (city) next.set('city', city)
            else next.delete('city')

            if (search) next.set('search', search)
            else next.delete('search')

            next.delete('page')
            return next
          })
        }}
      >
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-forest">Κατηγορία</span>
          <select
            value={searchParams.get('type') ?? ''}
            onChange={e => updateFilter('type', e.target.value)}
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C]"
          >
            <option value="">Όλες</option>
            <option value="LOST">Χάθηκαν</option>
            <option value="FOUND">Βρέθηκαν</option>
          </select>
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-forest">Πόλη</span>
          <input
            type="text"
            value={cityInput}
            onChange={e => setCityInput(e.target.value)}
            placeholder="π.χ. Θεσσαλονίκη"
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C]"
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-forest">Είδος ζώου</span>
          <select
            value={searchParams.get('species') ?? ''}
            onChange={e => updateFilter('species', e.target.value)}
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C]"
          >
            <option value="">Όλα</option>
            <option value="DOG">Σκύλος</option>
            <option value="CAT">Γάτα</option>
            <option value="OTHER">Άλλο</option>
          </select>
        </label>

        <label className="flex flex-col gap-2 md:col-span-2">
          <span className="text-sm font-medium text-forest">Λέξη κλειδί</span>
          <input
            type="search"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Αναζήτηση σε τίτλο και περιγραφή"
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C]"
          />
        </label>

        <button
          type="submit"
          className="h-11 self-end rounded-md bg-[#25533A] px-4 text-sm font-medium text-white hover:bg-[#1E432E]"
        >
          Αναζήτηση
        </button>
      </form>
    </div>
  )
}