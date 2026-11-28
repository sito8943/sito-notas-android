# Changelog

## 0.2.0 — 2026-11-28

### Features

- Images in notes, like the web: pick from the gallery or paste from the
  clipboard, upload with progress to `POST /notes/{id}/images`, shown inline in
  the rich editor (tap for full screen, X to remove). HEIC or >5 MB photos are
  re-encoded to JPEG. Notes with images no longer force Markdown mode
  (`826ea3b`, `350ce33`, `5a742b6`).
- "Editado hace…" label in the editor (`5a742b6`).
- Sharing: share a note by email (view/edit), public links, "Compartidas
  conmigo" tab with unseen badge, public note screen and
  `share.notes.sito8943.com` deep links (`f286cdc`, `f92bf48`, `6015ae5`,
  `2521b35`).
- Drag to reorder notes (`PATCH /notes/reorder`, queued offline), columns by
  width like the web (1 on a phone) and counters on the filter tabs
  (`f2d0766`, `cb989ac`, `b944d11`).
- Typo-tolerant search like the web (`string-similarity`) (`4a22a3e`).
- Local import/export in the server `ExportNoteDTO` format (`b2a25a5`).
- Settings: change password, anonymous-usage choice (`d1d0a12`).
- Sign up with sito-auth from the sign-in screen (`f02c06f`).
- Realtime refresh over STOMP while the app is open (`5172268`).
- The open editor refreshes when another device changes the note and there
  are no local edits (`77f0002`).
- Font Awesome Free 6.3.0 icons and Poppins Bold headings, as the web (`e62421d`).
- Every screen restyled after the Sito Notas web app: web palette (light and
  dark), typography, radii, cards, filter tabs, editor header and tags, trash
  purge countdown, settings cards, empty states (`00fa908`).
- Notes open in a rich-text editor (like the web's Tiptap editor) with a
  formatting toolbar above the keyboard and a switch to the Markdown source;
  checklists are tappable checkboxes. Markdown is kept byte for byte: only the
  edited line changes, so opening a note never marks it as modified. Notes with
  tables or HTML open in Markdown mode (`c18c224`).
- Font Awesome icons for the editor toolbar (`b691fec`).
- Note cards show the start of the note formatted, like the web's
  `MarkdownPreview`: headings, lists, task checkboxes, quotes, code, inline
  marks; first 600 characters, fading out when taller than the card (`b9b9a15`);
  HTML entities shown as characters (`bd69243`).

### Sync

- Server dates with offset (`Z`) and the old local form both parsed (`3be8e40`).
- Idempotent creates with a local `clientId`: a lost response no longer
  duplicates notes. Room 1→2 (`404ab58`).
- Hard deletes from the web reach the phone (tombstones), incremental
  categories pull, PATCH batches that skip missing ids (`937846f`); full pull
  when tombstones expired (`d0b0852`). Falls back to the current behaviour on
  servers without these endpoints, including the 500 production returns
  (`7db174c`).
- Switching accounts on one phone no longer copies the previous account's
  notes; pending changes ask what to do (`8b3c5e9`).

### Fixes

- Launcher label is now "Sito Notas" (`7e0a141`).

### Tests

- New sync contract and old-server fallback, migration 1→2, account switch,
  STOMP frames, editor refresh (`6339a8c`, `8b3c5e9`, `5172268`, `77f0002`).
- Sharing DTOs and links (`ada6d26`); image rules and Markdown (`9013c4c`).
- Markdown round-trip and editor operation tests (`c18c224`).
- Card preview truncation and blocks (`b9b9a15`).

### Build

- Removed `material-icons-extended` (`ed743b9`).

### Docs

- Reorder step, backup, hardened sync and realtime (`30ff0a9`, `daae08e`).

## 0.1.0 — 2026-11-24

First version: offline-first notes that sync with Sito Notes.

### Features

- Gradle project scaffolded from SitoWallet (`defc11c`).
- Network client, encrypted session and preferences (`1fcee1d`).
- Room schema for notes, categories and their links (`d2c3b4f`).
- Sync: push (categories before notes, batch ids as `remoteId`, trash/restore/
  permanent delete) and incremental pull with local-wins-if-pending (`f6874af`).
- Notes design system copied from SitoWallet (`c147cc7`).
- Sign in with sito-auth, forgot password, use without account (`31dc176`).
- Notes list, editor with autosave and Markdown preview, categories, trash and
  settings (`e235dfc`).

### Tests

- Sync push/pull/coordinator, search and Markdown (`a231ad2`).

### Docs

- README, agent instructions and project context (`5cda779`).
