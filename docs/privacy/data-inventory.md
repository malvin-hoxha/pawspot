# PawSpot data inventory

Internal working document. This describes the current implementation
and records decisions required before public launch.

## Data and purposes

| Category | Data | Storage | Purpose |
|---|---|---|---|
| Accounts | Name, email, password hash, role, creation date | PostgreSQL | Account management and authentication |
| Listings | Title, description, city, area, owner, timestamps | PostgreSQL | Publishing lost and found pet listings |
| Images | Uploaded image | Cloudinary | Displaying listing photographs |
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
- Cloudinary image deletion is attempted after the database transaction commits.
- Reports remain, with references to deleted users and listings set to null.
- Report descriptions may still contain personal data.

## Retention decisions and verification

- Finalize the proposed 30-day retention period for resolved reports before enabling automatic cleanup.
- Verify cleanup of expired refresh sessions and token records.
- Define production log contents, access and retention.
- Define backup retention and handling of deletions after restoration.
- Review Cloudinary storage, deletion and delivery-cache behavior.
- Decide how to handle inactive accounts and old listings.

## Known deletion gaps

- Failed Cloudinary deletions are logged but are not queued for retry.
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
