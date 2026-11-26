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

## Retention decisions and verification

- Finalize the proposed 30-day retention period for resolved reports before enabling automatic cleanup.
- Verify cleanup of expired refresh sessions and token records.
- Define production log contents, access and retention.
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
