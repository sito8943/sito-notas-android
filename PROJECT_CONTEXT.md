# SitoNotas — Project context

Native Android port of the Sito Notes web app (`apps/notes`), offline-first.
Architecture, versions, auth and design system copied from `../SitoWallet`
(Wallet* components renamed Notes*).

## Identity and config

| Item | Value |
|---|---|
| Package / namespace | `dev.sito8943.sitonotas` |
| applicationId | `com.sito8943.notas` (debug: `.debug` suffix) |
| Version | 0.1.0 (code 1), minSdk 26, target 36, compile 37 |
| BuildConfig | `API_BASE_URL` (notes API), `AUTH_BASE_URL` (sito-auth), `SHARE_BASE_URL` (public links; empty = `<api>/s/<token>`), `WEB_APP_URL` (`/note/<id>` links) |
| Database | Room `sito-notas.db`, version 2, schema in `app/schemas/` (1→2: `client_id`) |

## Code map (`app/src/main/java/dev/sito8943/sitonotas/`)

| Path | Responsibility |
|---|---|
| `SitoNotasApplication`, `MainActivity` | Restore session, start sync; theme/locale root; foreground → `requestSync()` |
| `core/di/AppContainer` | Manual DI. Two `RestClient`s (auth + notes) sharing one `SessionRepository` |
| `core/auth`, `core/network` | Copied from SitoWallet: encrypted session, single-flight refresh, refresh-on-401 |
| `core/data` | Room: `notes`, `categories`, `note_categories`; DAOs; `VersionClock` |
| `core/sync` | `NotesApi`, `SyncPush`, `SyncPull`, `SyncEngine`, `SyncCoordinator`, `AccountGuard`, `RealtimeClient` (STOMP) |
| `core/settings` | DataStore: theme, language, pull cursor per user, last sync |
| `core/model` | Search and category-name rules ported from the web |
| `feature/auth` | Sign in, forgot password, continue without account (no sign-up) |
| `feature/notes` | List (search, favorites/category filters), editor with autosave. `editor/`: rich-text editor over Markdown (one block per line, original syntax kept byte for byte), formatting toolbar, Markdown source mode |
| `feature/sharing` | Online only, no Room: share with users (`/notes/{id}/shares`), public link (`/notes/{id}/link`, `/public/notes/{token}`), "Compartidas conmigo" (`/notes/shared`, unseen badge), shared note view/edit (`GET`/`PATCH /notes/{id}`), deep link `https://share.notes.sito8943.com/<token>` |
| `feature/categories`, `feature/trash`, `feature/settings` | Their screens and ViewModels |
| `feature/backup` | Local import/export (Room) in the server `ExportNoteDTO` format, via SAF from Settings |
| `ui/` | Design system: tokens ported from the web (`apps/notes/src/styles/global.css`), Font Awesome icons (`FaIcon`, `res/drawable/fa_*.xml`), Poppins headings |
| `navigation/` | Single NavHost with a bottom bar on the 4 tabs |

## Sync design

Room is the source of truth for the UI. Each row has a local `id`, `remote_id`,
`dirty`, `purged`, `local_version`, `client_id` (UUID sent as `clientId` on
create, so a replayed create is not duplicated; renewed when the row is
re-created); notes also `deleted_at` and `remote_deleted`.

A row is **pending** when: `remote_id IS NULL` (create), `dirty` (PATCH),
`purged` (permanent delete), or for notes `(deleted_at IS NOT NULL) != remote_deleted`
(trash/restore). `SyncDao.observePendingStats()` emits count + max version.

`SyncEngine.sync(userId)` runs, in order:

0. If a stored tombstone cursor is older than 85 days (server keeps 90), all
   cursors are reset: full pulls.
1. **Pull categories**: `GET /categories/deleted?since=` (tombstones) deletes
   local rows by `remoteId` (a dirty one is re-created), then `GET /categories`
   incremental (`filters=updatedAt>=<until>`, trash included). Without cursor,
   or on the old server (400/404/405/501 on `/deleted`), a full pull that also
   deletes categories missing on the server and not pending. Upsert by
   `remoteId`; local offline categories with the same name key are *linked*
   instead of re-created (server rejects duplicate names with 409).
2. **Push categories**: `POST /categories/batch` (ids in request order →
   `remoteId`), `PATCH /categories/batch`, `DELETE /categories/permanent`
   (categories are deleted forever, like the web).
3. **Push notes**: `POST /notes/batch` (category ids translated to remote ids;
   `favorite` is not in the create request, so a favorite stays dirty and is
   PATCHed in the same run), `PATCH /notes/batch`, `DELETE /notes` (trash),
   `PATCH /notes/restore`, `DELETE /notes/permanent` (after trash).
   Batch PATCH: the new server returns the updated ids and skips missing ones;
   the old one returns a count and 404s the whole batch (then row by row). A
   row the server no longer has is re-created (active) or dropped (trashed).
4. **Pull notes** incrementally: `GET /notes?softDeleteScope=ALL&sort=id&order=ASC
   &filters=updatedAt>=<cursor>` paged; first time without filter (full
   download). Upsert by `remoteId`. If a note references an unknown category,
   categories are pulled first. Cursor = highest server `updatedAt` seen
   (server clock, not device clock), stored per user. Dates are parsed with
   offset (`...Z`) or as local UTC; cursors are sent back as the server wrote
   them. A complete full pull drops synced, non-pending notes the server lacks.
5. **Note tombstones**: `GET /notes/deleted?since=` deletes local rows (a pending
   active one is re-created). `until` is the next `since`.

Optional `extraSteps` (`SyncStep`) run between push notes and pull notes.

Account switch (`AccountGuard`, inside the coordinator mutex): the owner of the
synced data is stored. Local-only rows go to the first account. Another account
signing in wipes the previous account's synced rows and resets cursors; if the
previous account left pending changes, sync waits on `SyncStatus.AccountSwitch`
and `AccountSwitchDialog` asks: upload to the new account or discard.

Conflict rule: on pull, a pending local row is skipped (local wins, it gets
pushed); otherwise the server overwrites it. `dirty` is cleared only if
`local_version` did not change while the request was in flight.

`SyncCoordinator` triggers: pending changes (debounce 1.5 s, only when count > 0),
sign-in, app foreground (`MainActivity.onStart`), manual "Sincronizar ahora".
A mutex serializes runs. Failures are shown (offline vs server) and retried on
the next trigger. No WorkManager.

Extra steps: `SyncEngine(extraSteps: List<SyncStep>)` runs them after pushing
notes and before pulling them. `ReorderSync` is one: a drag-reorder sets
`position = index` locally (not dirty), stores the local ids in DataStore
(`pending_reorder`) and asks for a sync; the step maps them to `remoteId`s,
sends `PATCH /notes/reorder` (all active notes, top first) and clears the
queue only if no newer order arrived meanwhile.

Realtime: `RealtimeClient` opens STOMP over OkHttp WebSocket at `<API>/ws`
(bearer in CONNECT), only signed in and in the foreground, subscribes to
`/user/queue/notes` and requests a sync per event (debounce 1 s).
`/user/queue/sync-status` is ignored. Backoff 1–60 s; one token refresh on an
ERROR before CONNECTED. The open editor takes a pulled change only when it has
no unsaved edits.

## Known gaps

- Against the old server (no tombstones), hard deletes from the web are not
  visible to an incremental pull. Synced trash older than 30 days is dropped
  locally to mirror the server purge job.
- The server sends no realtime events for creates, batch edits, reorder,
  permanent deletes or categories: those arrive on the next trigger.
- `PATCH /notes/reorder` does not bump `updatedAt`, so manual order changed on
  the web only arrives with a full pull. No reorder UI in v1.
- View configs: the web does not use them; list/grid is local only. Sharing is online only: shared-with-me
  notes are never stored in Room and an EDIT-share edit is lost if the PATCH
  fails and the user leaves (the error is shown).
