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

## Look and feel

The UI follows the Sito Notes web app (`apps/notes`): same palette (light and
dark), Roboto 500 text with Poppins Bold headings, soft-cornered chips, pill
buttons and search, `rounded-xl` cards. Android conventions are kept: bottom
navigation bar with labels, FAB for new items, system back, edge-to-edge,
48dp touch targets.

## Third-party assets

- Icons: [Font Awesome Free](https://fontawesome.com) 6.3.0 (the version the web
  uses), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
  Only the icons in use are vendored, converted from the SVG path data to
  VectorDrawables in `app/src/main/res/drawable/fa_*.xml`, and drawn with
  `ui/elements/FaIcon`.
- Font: [Poppins](https://github.com/itfoundry/Poppins) Bold
  (`app/src/main/res/font/poppins_bold.ttf`), Copyright 2020 The Poppins Project
  Authors, licensed under the [SIL Open Font License 1.1](https://openfontlicense.org).
