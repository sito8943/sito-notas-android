# SitoNotas (Kotlin) — Agent Instructions

Native Android client of Sito Notes, offline-first. Read before changing anything:

1. `PROJECT_CONTEXT.md` (structure and sync design)
2. The backend contract in `../../servers/notes` (controllers, DTOs, requests)
3. The web app `../../apps/notes/src/features/notes` (product behavior reference)
4. The real code path the request touches

Rules (inherited from SitoWallet):

- The web app is the product spec. If something there looks like a bug, say so
  before "fixing" it here.
- Do not invent backend URLs, DTOs, endpoints or ordering rules.
- Inspect Git state before editing. If there are changes that are not yours, ask.
- Do not overwrite or discard existing work.
- Keep scope: native Android, one Gradle module (`app`).

Architecture:

- Room is the local source of truth. The UI observes `Flow`s from DAOs through
  repositories; no in-memory cache on top of Room. Composables never touch a DAO.
- `core/data` is the only layer touching Room; `core/network` the only one doing
  HTTP; `core/sync` orchestrates and knows no screens; `core/di/AppContainer` is
  the only dependency root (no Hilt, no Retrofit).
- The local `id` and `remoteId` are different identities. PATCH/DELETE always
  target `remoteId`. Never fake a successful push the server did not confirm.
- No destructive migrations. Every schema change ships a `Migration` and a test;
  schemas are exported to `app/schemas/` and committed.
- State goes down, events go up: screen state lives in a `ViewModel` as an
  immutable `StateFlow`; reusable composables take values and callbacks.
- Fail explicitly: network/auth/parse failures become visible state.
- Never commit credentials, tokens, keystores or `local.properties`. Never log note
  contents or auth material. Cleartext only in the debug manifest. Tokens are
  stored encrypted with the Android Keystore. Sign the user out only when the
  server **rejects** the refresh token (`RefreshOutcome`).

Verification: use the narrowest command that applies:
`./gradlew :app:compileDebugKotlin`, `:app:assembleDebug`, `:app:testDebugUnitTest`.
