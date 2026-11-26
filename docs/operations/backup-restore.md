# PawSpot local PostgreSQL backup and restore verification

## Scope

This runbook covers a manual local backup and an isolated restore check for
the PostgreSQL service defined in `docker-compose.yml`. It uses the existing
`pawspot-postgres` container and stores the archive on the local disk. No
additional paid storage service is required for this procedure.

Production automation and off-host backup arrangements remain pending. This
procedure does not provide disaster recovery for loss of the local machine or
disk, and it does not guarantee recoverability.

## Create a backup

The commands in this section are for Bash. Run them on the Docker host. They
read `POSTGRES_USER` and `POSTGRES_DB` from the container environment; do not
copy database passwords into the commands or this document.

```bash
set -euo pipefail
umask 077

BACKUP_DIR="$HOME/pawspot-backups"
BACKUP_TIMESTAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
BACKUP_FILE="$BACKUP_DIR/pawspot-$BACKUP_TIMESTAMP.dump"
PARTIAL_FILE="$BACKUP_FILE.partial"

install -d -m 700 "$BACKUP_DIR"

docker exec pawspot-postgres sh -c \
  'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "$PARTIAL_FILE"

chmod 600 "$PARTIAL_FILE"
mv -- "$PARTIAL_FILE" "$BACKUP_FILE"
printf 'Backup created: %s\n' "$BACKUP_FILE"
```

`pg_dump -Fc` creates a compressed custom-format archive. Compression is not
encryption. The restrictive umask protects the archive from other local users
while it is being written, the directory is mode `700`, and the completed
archive is mode `600`.

The dump is first written to a `.partial` path. With `set -e`, Bash stops if
`pg_dump` or another command fails, so the rename happens only after a
successful dump. Treat a remaining `.partial` file as incomplete; do not use
or rename it as a valid backup.

## Verify a restore in an isolated database

Use the same Bash session after creating a backup, or set `BACKUP_FILE` to the
specific archive to verify. Never restore this check into the normal PawSpot
database. Do not start the application or background workers against the
temporary copy.

### Shell commands (Bash)

```bash
set -euo pipefail
umask 077

# If needed, select one specific archive created by the backup procedure.
# BACKUP_FILE="$HOME/pawspot-backups/pawspot-YYYYMMDDTHHMMSSZ.dump"
test -f "$BACKUP_FILE"

readonly RESTORE_DB="pawspot_restore_$(date -u +'%Y%m%dT%H%M%SZ')_$$"
printf 'Temporary restore database: %s\n' "$RESTORE_DB"

docker exec \
  -e RESTORE_DB="$RESTORE_DB" \
  pawspot-postgres sh -c \
  'exec createdb -U "$POSTGRES_USER" --template=template0 "$RESTORE_DB"'

docker exec -i \
  -e RESTORE_DB="$RESTORE_DB" \
  pawspot-postgres sh -c \
  'exec pg_restore -U "$POSTGRES_USER" -d "$RESTORE_DB" --exit-on-error --single-transaction' \
  < "$BACKUP_FILE"

docker exec -it \
  -e RESTORE_DB="$RESTORE_DB" \
  pawspot-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$RESTORE_DB"'
```

`createdb --template=template0` creates a clean, uniquely named destination.
`pg_restore --exit-on-error --single-transaction` stops at the first restore
error and rolls back that restore transaction. If any shell command fails,
stop and investigate; do not proceed blindly to verification or report the
restore as successful.

### SQL entered inside `psql`

After the final shell command opens `psql`, run these statements at its
prompt. Confirm that `current_database` exactly matches the value printed by
`printf '%s\n' "$RESTORE_DB"` in the Bash session before relying on any other
result.

```sql
SELECT current_database();

SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT 'users' AS table_name, count(*) AS row_count FROM users
UNION ALL
SELECT 'pet_posts', count(*) FROM pet_posts
UNION ALL
SELECT 'conversations', count(*) FROM conversations
UNION ALL
SELECT 'messages', count(*) FROM messages
UNION ALL
SELECT 'post_reports', count(*) FROM post_reports
UNION ALL
SELECT 'image_deletion_tasks', count(*) FROM image_deletion_tasks
ORDER BY table_name;
```

Check that the destination is the temporary database, every expected Flyway
row is successful, and the table counts are plausible for the selected
archive. Counts vary over time and are not fixed acceptance values. Exit
`psql` with `\q` only after reviewing the results.

### Remove the verified temporary database

Return to Bash. Run this only after successful verification. The prefix guard
prevents the command from targeting the normal PawSpot database; it does not
replace checking the value yourself.

```bash
case "$RESTORE_DB" in
  pawspot_restore_*) ;;
  *)
    printf 'Refusing to remove unexpected database: %s\n' "$RESTORE_DB" >&2
    exit 1
    ;;
esac

docker exec \
  -e RESTORE_DB="$RESTORE_DB" \
  pawspot-postgres sh -c \
  'exec dropdb -U "$POSTGRES_USER" "$RESTORE_DB"'
```

This removes only the specifically named temporary database. If verification
failed, retain it only as long as needed for investigation and remove that
same explicitly checked database afterward.

## Verification record

On 2026-11-26, a manual local restore completed with exit code `0`. The
restored `flyway_schema_history` contained successful entries for V1
`BASELINE`, V2 `SQL`, and V3 `SQL`.

The restored table counts were:

| Table | Rows |
|---|---:|
| `users` | 5 |
| `pet_posts` | 4 |
| `conversations` | 2 |
| `messages` | 10 |
| `post_reports` | 4 |
| `image_deletion_tasks` | 0 |

The temporary database was subsequently removed. These values are historical
verification results, not expected counts for future backups.

## Limitations and pending decisions

- A backup on the same machine is not disaster recovery for loss of that
  machine or disk.
- PostgreSQL dumps contain personal data. Keep them outside the repository and
  never add them to Git.
- Cloudinary image files are not included in the PostgreSQL dump.
- Production backup frequency, retention, encryption, access and off-host
  transfer are not finalized or automated.
- Restoring an older backup may restore data that was previously deleted. A
  production procedure for reapplying or otherwise handling prior deletions
  remains an open item.
- This procedure does not establish legal compliance or guarantee that every
  backup can be recovered.
