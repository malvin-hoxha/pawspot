import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import {
  reportReasons,
  submitPostReport,
  type ReportReason,
} from '../../api/reports'

export function PostReportForm({ postId }: { postId: number }) {
    const [open, setOpen] = useState(false);
    const [reason, setReason] = useState<ReportReason | ''>('');
    const [description, setDescription] = useState('');

    const report = useMutation({
        mutationFn: (selectedReason: ReportReason) => 
            submitPostReport(postId, selectedReason, description.trim() || undefined)
    });

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if (!reason || report.isPending) return;

        report.mutate(reason);
    }

    if (report.isSuccess) {
        return <p role="status" className="text-sm text-forest">Η αναφορά καταχωρίστηκε.</p>
    }

    if (!open) {
        return (
            <button
                type="button"
                onClick={() => setOpen(true)}
                className="text-sm text-muted underline hover:text-forest"
            >
                Αναφορά αγγελίας
            </button>
        )
    }

    return (
        <form onSubmit={handleSubmit} className="rounded-lg border border-line p-4">
            <fieldset disabled={report.isPending} className="space-y-3">
                <label className="block text-sm">
                    Λόγος αναφοράς
                    <select
                        required
                        value={reason}
                        onChange={(event) => setReason(event.target.value as ReportReason | '')}
                        className="mt-1 block w-full rounded-md border border-line bg-white p-2"
                    >
                        <option value="" disabled>Επίλεξε λόγο</option>
                        {Object.entries(reportReasons).map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                        ))}
                    </select>
                </label>

                <label className="block text-sm">
                    Περιγραφή (προαιρετική)
                    <textarea
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                        maxLength={1000}
                        rows={3}
                        className="mt-1 block w-full rounded-md border border-line bg-white p-2"
                    />
                </label>

                {report.isError && (
                    <p role="alert" className="text-sm text-red-700">
                        Η αναφορά δεν καταχωρίστηκε. Προσπάθησε ξανά.
                    </p>
                )}

                <div className="flex flex-wrap gap-3">
                    <button
                        type="submit"
                        disabled={!reason}
                        className="rounded-md bg-forest px-4 py-2 text-sm text-white disabled:opacity-50"
                    >
                        {report.isPending ? 'Υποβολή…' : 'Υποβολή αναφοράς'}
                    </button>
                    <button type="button" onClick={() => setOpen(false)} className="text-sm">
                        Ακύρωση
                    </button>
                </div>
            </fieldset>
        </form>
    )
}