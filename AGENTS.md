# Agent instructions

When editing code in this repo, follow these steps so all developers' agents behave consistently.

## After editing code

1. **Firebase Cloud Functions (`functions/`)**  
   - Run lint and fix any issues before considering the task done:
     - `cd functions && npm run lint`
   - ESLint enforces max line length (80), no non-null assertion, etc.  
   - `npm run lint -- --fix` only fixes some rules; fix remaining errors manually (e.g. shorten lines, replace `!` with safe optional access).

2. **Android app (`app/`)**  
   - Respect existing Kotlin/Compose patterns. Fix any new linter diagnostics in edited files.

## Project overview

- **App:** Android (Kotlin, Jetpack Compose), Firestore client.
- **Backend:** Firebase Cloud Functions (TypeScript) in `functions/` — Firestore triggers, Cloud Tasks for session expiry.
- **Data:** Stations (waitlist + config), users; attendees are a map keyed by `userId`; session times are set by the Cloud Function from server time.
