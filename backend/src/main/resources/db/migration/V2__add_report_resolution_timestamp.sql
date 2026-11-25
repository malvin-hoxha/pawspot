ALTER TABLE public.post_reports
    ADD COLUMN resolved_at timestamp(6) with time zone;

-- Historical resolution times are unknown.
-- Start their retention period from the migration time.
UPDATE public.post_reports
SET resolved_at = CURRENT_TIMESTAMP
WHERE status IN ('DISMISSED', 'POST_REMOVED')
  AND resolved_at IS NULL;

CREATE INDEX idx_post_reports_resolved_at
    ON public.post_reports (resolved_at)
    WHERE status IN ('DISMISSED', 'POST_REMOVED');