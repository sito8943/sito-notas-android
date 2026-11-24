# SitoNotas

Native Android notes app (Kotlin + Jetpack Compose), offline-first, that syncs
with the Sito Notes backend (`servers/notes`) using the same sito-auth account as
SitoWallet.

- Notes with title + Markdown content, autosaved locally, preview toggle.
- Favorites, multi-category notes, search and filters.
- Categories CRUD (name, description, color).
- Trash: soft delete, restore, delete forever, empty trash.
- Settings: theme, language (Spanish default, English), account, manual sync.
- Works without an account; signing in uploads local notes and downloads the rest.

## Setup

```bash
cp local.properties.example local.properties   # set sdk.dir, backend URLs
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

| Build | applicationId | Notes API | sito-auth |
|---|---|---|---|
| debug | `com.sito8943.notas.debug` | `http://10.0.2.2:8083` | `http://10.0.2.2:8081` |
| release | `com.sito8943.notas` | `https://api.notes.sito8943.com` | `https://api.auth.sito8943.com` |

Override with `sitonotas.{debug,release}.{apiUrl,authUrl}` in `local.properties`.

Release signing reads `keystore.properties` (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`). Without it the release APK is built **unsigned**.

See `PROJECT_CONTEXT.md` for structure and the sync design.
