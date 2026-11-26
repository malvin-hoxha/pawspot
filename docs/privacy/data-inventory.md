# PawSpot data inventory

Internal working document. This describes the current implementation
and records decisions required before public launch.

## Data and purposes

| Category | Data | Storage | Purpose |
|---|---|---|---|
| Accounts | Name, email, password hash, role, creation date | PostgreSQL | Account management and authentication |
| Listings | Title, description, city, area, owner, timestamps | PostgreSQL | Publishing lost and found pet listings |
| Images | Uploaded image | Cloudinary | Displaying listing photographs |
| Image deletion tasks | Cloudinary public ID, failed-attempt count, creation timestamp, next-attempt timestamp | PostgreSQL | Reliable asynchronous image deletion |
| Conversations | Participants, listing reference, timestamps | PostgreSQL | Private communication about listings |
| Messages | Sender, message body, creation date | PostgreSQL | Private communication between participants |
| Reports | Reporter, listing reference, reason, description, status, creation date | PostgreSQL | Reviewing reported content |
| Authentication | Refresh sessions and token records | PostgreSQL | Session management and token revocation |
| Login limits | IP addresses, normalized emails, counters | Backend memory | Limiting repeated login attempts |

## Current account deletion

- The password is verified before deletion.
- Account records, owned listings, refresh sessions and tokens are deleted.
- Conversations involving the user and their messages are deleted through database cascades.
- Authentication cookies are cleared.
- Image deletion tasks are persisted in the same database transaction as account and listing deletion.
- Cloudinary deletion is subsequently handled by the background worker.
- Reports remain, with references to deleted users and listings set to null.
- Report descriptions may still contain personal data.

## Persistent image deletion

- The mechanism covers listing removal by its owner or an admin, and account deletion.
- Tasks have no foreign keys to deleted users or listings.
- Task insertion rolls back if the enclosing deletion transaction rolls back.
- Persisted tasks survive application restarts.
- The job processes up to 20 due tasks per batch by default.
- Processing within each batch is sequential in the current implementation.
- The default initial delay is 60 seconds.
- The default fixed delay after each completed batch is 60 seconds.
- Each worker invocation uses a database transaction and a row lock.
- Confirmed deletion or an already-missing image allows the task to be removed.
- Failures increment the attempt count and defer processing by five minutes.
- There is currently no maximum-attempt limit; defining one is deferred work.
- Processing may repeat after external success followed by database rollback or a crash; execution is not exactly once.
- Image cleanup is enabled by default, subject to the global scheduling switch and `app.images.cleanup.enabled`.
- Cloudinary deletion requests include CDN invalidation, but this does not guarantee immediate removal from every cache or backup.

### Failed post creation

- After a successful Cloudinary upload, a mapping or database save failure queues the uploaded public ID for deletion.
- Failed-creation task insertion uses a new transaction (`REQUIRES_NEW`), so it commits independently of the failed post creation.
- The existing image deletion worker processes the task and retries failed deletion attempts.
- If task insertion also fails, the original post-creation exception is preserved and the queueing exception is attached as suppressed.

## Authentication retention

- Refresh sessions currently expire seven days after creation.
- Token rotation preserves the original expiration time.
- Expired refresh token and session records are deleted by a scheduled
  cleanup job, daily at 03:00 UTC.
- Cleanup requires the application to be running with global scheduling
  enabled.
- Records are eligible for deletion when their expiration timestamp is
  earlier than the cleanup execution time.
- Revoked but unexpired records remain until expiration.
- Account deletion removes the user's refresh tokens and sessions
  within the account deletion transaction.
- Expiration prevents authentication use; physical database deletion
  happens during a later cleanup execution.
- Application downtime or cleanup failures can delay physical deletion.

## Application logging

### Current implementation

- The repository does not configure an application log file. Console output may still be captured by the runtime or hosting platform.
- Hibernate SQL output is disabled by default and can be enabled explicitly for local debugging.
- Cleanup logs record task IDs, attempt counts, deletion counts and, where applicable, exception class names.
- The image deletion job does not directly log exception messages or stack traces.
- Framework, database and future reverse-proxy logs require separate review. These changes do not guarantee that all logs exclude personal data.

### Proposed production policy

- Restrict operational log access to the operator.
- Do not deliberately log passwords, tokens, cookies, private messages or report descriptions.
- Define the production log retention period before public launch.
- Configure and verify log rotation and deletion during deployment.
- Review hosting-platform and reverse-proxy logging separately.
- Production log retention is not currently enforced by the repository configuration.

## Database backup and restore

- PostgreSQL backup and isolated restore verification are currently manual.
- The verified local procedure uses the existing `pawspot-postgres` container and stores custom-format archives on local disk outside the repository.
- A manual isolated restore completed successfully on 2026-11-26. The historical verification details and commands are recorded in the [backup and restore runbook](../operations/backup-restore.md).
- The local procedure is not automated or off-host, and it does not protect against loss of the machine or disk.
- Database archives contain personal data and must not be committed to Git. Cloudinary image files are not included.
- Production frequency, retention, encryption, access, off-host transfer and handling of deletions restored from older backups remain open decisions.

## Retention decisions and verification

- Finalize the proposed 30-day retention period for resolved reports before enabling automatic cleanup.
- Verify the production logging policy and deployment configuration.
- Define backup retention and handling of deletions after restoration.
- Review Cloudinary storage, deletion and delivery-cache behavior.
- Decide how to handle inactive accounts and old listings.

## Known deletion gaps

- Failed-post-creation cleanup is not atomic across Cloudinary and PostgreSQL. A process crash before task insertion, or database unavailability preventing insertion, can still leave an orphan image.
- Clearing a report's user reference does not anonymize its free-text description.

## Before publication

- Confirm who operates PawSpot and provide a privacy contact.
- Document the legal basis for each processing purpose.
- Review hosting and Cloudinary processor arrangements and transfers.
- Document procedures for handling data-subject requests.
- Prepare the public privacy and cookie information.

## Report retention implementation

- Reports have a nullable `resolvedAt` timestamp.
- Pending reports have no resolution timestamp.
- Dismissing a report records its resolution time.
- Removing a reported post resolves its pending reports with the same timestamp.
- Repeated resolution requests are rejected without changing that timestamp.
- Existing resolved reports use the V2 migration time as their retention starting point because their original resolution times are unknown.

### Cleanup

- Cleanup deletes only `DISMISSED` and `POST_REMOVED` reports whose resolution timestamp is at or before the calculated cutoff.
- Pending reports and reports without a resolution timestamp are preserved.
- Cleanup deletes the entire report, including its description.
- The proposed retention period is 30 days and is configurable.
- Automatic cleanup is disabled by default.
- When enabled, the default schedule is daily at 03:00 UTC while the application is running.
- Both global scheduling and report cleanup must be enabled for scheduled execution.
- The retention policy must be finalized before enabling cleanup for public operation.
