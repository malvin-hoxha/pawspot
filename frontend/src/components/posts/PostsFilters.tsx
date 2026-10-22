import { useSearchParams } from 'react-router'
import { useState, useEffect } from 'react'

export function PostsFilters() {
    const [searchParams, setSearchParams] = useSearchParams();

    const [cityInput, setCityInput] = useState(
        searchParams.get('city') ?? ''
    );

    const cityFromUrl = searchParams.get('city') ?? '';

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
      <div className="grid gap-4 md:grid-cols-3">

        {/* Κατηγορία */}
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-forest">
            Κατηγορία
          </span>

          <select
            value={searchParams.get('type') ?? ''}
            onChange={e => updateFilter('type', e.target.value)}
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C] focus:outline-none focus:ring-2 focus:ring-[#52715C]"
          >
            <option value="">Όλες</option>
            <option value="LOST">Χάθηκαν</option>
            <option value="FOUND">Βρέθηκαν</option>
          </select>
        </label>

        {/* Περιοχή */}
        <form
            onSubmit={e => {
                e.preventDefault();
                updateFilter('city', cityInput.trim());
            }}
            className="flex flex-col gap-2"
            >
            <label
                htmlFor="city-filter"
                className="text-sm font-medium text-forest"
            >
                Πόλη
            </label>

            <div className="flex gap-2">
                <input
                    id="city-filter"
                    type="text"
                    value={cityInput}
                    onChange={e => setCityInput(e.target.value)}
                    placeholder="π.χ. Θεσσαλονίκη"
                    className="h-11 min-w-0 flex-1 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C] 
                        focus:outline-none focus:ring-2 focus:ring-[#52715C]"
                />

                <button
                    type="submit"
                    className="rounded-md bg-[#25533A] px-4 text-sm font-medium text-white hover:bg-[#1E432E]"
                >
                    Αναζήτηση
                </button>
            </div>
        </form>

        {/* Είδος ζώου */}
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-forest">
            Είδος ζώου
          </span>

          <select
            value={searchParams.get('species') ?? ''}
            onChange={e => updateFilter('species', e.target.value)}
            className="h-11 rounded-md border border-line bg-white px-3 text-sm text-[#253B2C] focus:outline-none focus:ring-2 focus:ring-[#52715C]"
          >
            <option value="">Όλα</option>
            <option value="DOG">Σκύλος</option>
            <option value="CAT">Γάτα</option>
            <option value="OTHER">Άλλο</option>
          </select>
        </label>
      </div>
    </div>
  )
}