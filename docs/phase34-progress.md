# Phases 3 and 4 implementation

Scope follows the original attached product spec. Phase 4 cloud support means encrypted scheduled backups to an Android document-provider folder (local or Google Drive), with restore/merge; no separate account server or automatic two-way record merge is specified.

- [x] Typed metadata, local cover images, context filters, and metadata sorting
- [x] Opt-in Open Library/TMDB lookup with offline fallback and explicit result selection
- [x] Voice capture parser, launcher shortcuts, capture tile, and two Glance widgets
- [x] Biometric/device-credential lock across entry points and private external surfaces
- [x] Portable encrypted archives, Keystore-protected scheduled backup credentials, SAF folder backups and restore
- [x] Monthly/year insights, streaks, tag activity, and forgotten-backlog cleanup
- [ ] Meaningful unit/device regressions, full suite, lint, debug/release builds, and emulator smoke checks
- [ ] Updated README, user guides, screenshots, and verification evidence

Implementation decisions: preserve the existing Room schema (metadata/image fields already exist); use stable Glance 1.2.0 and Biometric 1.1.0; use platform AES-256-GCM/PBKDF2 and Android Keystore because Jetpack Security Crypto is deprecated. Lookup is disabled by default; TMDB credentials are configured privately in the app. Cloud destination access is granted through Android's folder picker.

Validation in progress: 144 JVM tests pass. Focused Android runs cover metadata, encrypted record/image restore, Keystore storage, actual SAF folder retention, app-lock entry points, notification redaction, launcher actions, and a real widget host's privacy transition. Full regression, release build, lint, screenshots, and the normal installed-app smoke check are being completed.

Review fixes applied: metadata Save is disabled while enrichment is busy and reports success after persistence; an optional-cover download failure retains applied metadata and explains the partial result. Launcher capture follows theme-aware system bars. Cover orientation uses AndroidX ExifInterface, and the tile's pre-API-34 fallback is explicitly guarded.
