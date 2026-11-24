# Changelog

## Unreleased

### Features

- Font Awesome Free 6.3.0 icons and Poppins Bold headings, as the web (`e62421d`).
- Every screen restyled after the Sito Notas web app: web palette (light and
  dark), typography, radii, cards, filter tabs, editor header and tags, trash
  purge countdown, settings cards, empty states (`00fa908`).

### Fixes

- Launcher label is now "Sito Notas" (`7e0a141`).

### Build

- Removed `material-icons-extended` (`ed743b9`).

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
