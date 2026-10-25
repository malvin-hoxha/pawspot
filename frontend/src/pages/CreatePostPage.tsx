import { useEffect, useState, type FormEvent } from "react"
import { createPost, type CreatePostInput } from "../api/posts";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";

type FormValues = Omit<CreatePostInput, 'image' | 'area'> & {
  area: string
}

const fieldClass =
  'mt-2 w-full rounded-md border border-[#D5DDD4] bg-white px-4 py-2.5 text-[#253B2C] outline-none focus:border-[#25533A] focus:ring-2 focus:ring-[#DCE9DC]';

export function CreatePostPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [values, setValues] = useState<FormValues>({
    type: 'LOST',
    title: '',
    description: '',
    species: 'DOG',
    city: '',
    area: '',
  });

  const [image, setImage] = useState<File | null>(null);
  const [validationError, setValidationError] = useState('');
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    }
  }, [previewUrl]);

  const createMutation = useMutation({
    mutationFn: createPost,
    onSuccess: async created => {
      await queryClient.invalidateQueries({ queryKey: ['posts'] })
      void navigate(`/posts/${created.id}`)
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (
      !values.title.trim() ||
      !values.description.trim() ||
      !values.city.trim()
    ) {
      setValidationError('Συμπλήρωσε τίτλο, περιγραφή και πόλη.');
      return;
    }

    if (!image) {
      setValidationError('Επίλεξε φωτογραφία.');
      return;
    }

    if (!image.type.startsWith('image/')) {
      setValidationError('Το αρχείο πρέπει να είναι εικόνα.');
      return;
    }

    if (image.size > 5 * 1024 * 1024) {
      setValidationError('Η εικόνα πρέπει να είναι μικρότερη από 5 MB.');
      return;
    }

    if (createMutation.isPending) return;

    setValidationError('');
    createMutation.mutate({
      ...values,
      title: values.title.trim(),
      description: values.description.trim(),
      city: values.city.trim(),
      area: values.area.trim() || undefined,
      image,
    });
  }

    return (
      <section className="mx-auto max-w-3xl px-5 py-12">
        <h1 className="font-serif text-4xl text-[#253B2C]">
          Νέα αγγελία
        </h1>
        <p className="mt-3 text-sm text-muted">
          Πρόσθεσε τα στοιχεία του κατοικιδίου και μια φωτογραφία.
        </p>

        <form
          onSubmit={handleSubmit}
          className="mt-8 space-y-6 rounded-lg border border-[#E5E7E2] bg-white p-6 sm:p-8"
        >
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-[#253B2C]">
              Τύπος αγγελίας
              <select
                value={values.type}
                onChange={event =>
                  setValues(previous => ({
                    ...previous,
                    type: event.target.value as FormValues['type'],
                  }))
                }
                className={fieldClass}
              >
                <option value="LOST">Χάθηκε</option>
                <option value="FOUND">Βρέθηκε</option>
              </select>
            </label>

            <label className="block text-sm font-medium text-[#253B2C]">
              Ζώο
              <select
                value={values.species}
                onChange={event =>
                  setValues(previous => ({
                    ...previous,
                    species: event.target.value as FormValues['species'],
                  }))
                }
                className={fieldClass}
              >
                <option value="DOG">Σκύλος</option>
                <option value="CAT">Γάτα</option>
                <option value="OTHER">Άλλο</option>
              </select>
            </label>
          </div>

          <label className="block text-sm font-medium text-[#253B2C]">
            Τίτλος
            <input
              required
              maxLength={255}
              value={values.title}
              onChange={event =>
                setValues(previous => ({
                  ...previous,
                  title: event.target.value,
                }))
              }
              className={fieldClass}
            />
          </label>

          <label className="block text-sm font-medium text-[#253B2C]">
            Περιγραφή
            <textarea
              required
              rows={6}
              maxLength={2000}
              value={values.description}
              onChange={event =>
                setValues(previous => ({
                  ...previous,
                  description: event.target.value,
                }))
              }
              className={fieldClass}
            />
          </label>

          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-[#253B2C]">
              Πόλη
              <input
                required
                maxLength={255}
                value={values.city}
                onChange={event =>
                  setValues(previous => ({
                    ...previous,
                    city: event.target.value,
                  }))
                }
                className={fieldClass}
              />
            </label>

            <label className="block text-sm font-medium text-[#253B2C]">
              Περιοχή (προαιρετικά)
              <input
                maxLength={255}
                value={values.area}
                onChange={event =>
                  setValues(previous => ({
                    ...previous,
                    area: event.target.value,
                  }))
                }
                className={fieldClass}
              />
            </label>
          </div>

          <label className="block text-sm font-medium text-[#253B2C]">
            Φωτογραφία
            <input
              type="file"
              accept="image/*"
              required
              onChange={event => {
                const file = event.target.files?.[0] ?? null;
                setImage(file);
                setPreviewUrl(file ? URL.createObjectURL(file) : null);
              }}
              className={`${fieldClass} file:mr-4 file:rounded-md file:border-0 file:bg-[#E0EDE2] file:px-4 file:py-2
                file:text-sm file:font-semibold file:text-[#25533A] file:cursor-pointer`}
            />
            {previewUrl && (
              <img
                src={previewUrl}
                alt="Προεπισκόπηση φωτογραφίας αγγελίας"
                className="mt-4 aspect-4/3 w-full max-w-sm rounded-lg border border-[#D5DDD4] object-cover"
              />
            )}
            <span className="mt-2 block text-xs text-muted">
              Εικόνα έως 5 MB.
            </span>
          </label>

          {validationError && (
            <p role="alert" className="text-sm text-[#9A423A]">
              {validationError}
            </p>
          )}

          {createMutation.isError && (
            <p role="alert" className="text-sm text-[#9A423A]">
              Δεν δημοσιεύτηκε η αγγελία. Προσπάθησε ξανά.
            </p>
          )}

          <div className="flex justify-end border-t border-[#E5E7E2] pt-6">
            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                disabled={createMutation.isPending}
                onClick={() => void navigate('/posts/my')}
                className="rounded-md border border-[#D5DDD4] px-5 py-2.5 text-[#253B2C]
                  cursor-pointer hover:bg-[#F2F5F0] disabled:opacity-50"
              >
                Άκυρο
              </button>

              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-[#25533A] px-5 py-2.5 text-sm font-semibold cursor-pointer
                  text-white hover:bg-[#1E432E] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {createMutation.isPending
                  ? 'Δημοσίευση...'
                  : 'Δημοσίευση αγγελίας'}
              </button>
            </div>
          </div>
        </form>
      </section>
    )
  }
