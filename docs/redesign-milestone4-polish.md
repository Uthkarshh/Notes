# Redesign milestone 4 — motion, Board and quality pass

This increment follows the [adaptive foundation](redesign-milestone4.md). Motion, Board, accessibility and privacy implementation and the local verification pass are complete. Physical-device, full assistive-input, Recent Apps visual review and release acceptance gates remain below.

## Delivered behavior

- **Motion:** Settings adds a persisted Reduce motion option, included in appearance restore and compatible with older archives. A live observer also respects Android’s animator scale. Navigation fades, cover transitions, capture handoff and draw reveals follow that policy. Saving and navigation do not depend on decorative animation callbacks.
- **Covers:** List thumbnails, grid covers and the compact Item Detail cover share one renderer and stable entity keys. The fallback remains visible during image loading or failure. Shared bounds use a common Navigation Compose transition scope; the persistent large-window list remains in place.
- **Capture:** The FAB and capture header use matched color, shape and icon treatment. Normal motion retains Material sheets. With reduced motion, sheets use an immediate modal Dialog with Back handling, dismiss semantics, inherited secure flags and no window animation. This avoids unsupported access to Material 1.4’s internal motion APIs.
- **Completion:** Reflections queue behind existing app sheets and decision dialogs. Completing a Today draw closes its sheet before presenting the reflection. A completion commits independently of that presentation.
- **Board:** Tap the drag handle to choose a status, or hold it and drag. Pickup raises and outlines a preview, valid columns are marked, and their edges scroll in both axes at bounded speed. The board pauses snap scrolling during pickup. Cancellation and outside drops leave status unchanged. A valid drop uses the existing reversible status command. The repository rechecks the original collection/status and archive/pending-deletion eligibility. No within-column ordering is introduced.
- **Back:** Item, note and fact editors consume predictive Back progress without leaving early. Cancellation retains the draft and route; a completed gesture uses the existing save-before-navigation guard.
- **Accessibility and themes:** Ratings support keyboard focus, arrows, Home/End, clearing, assistive range actions and named switch actions. Pointer values follow RTL star order. Collection badge/progress colors are contrast-adjusted; selected swatches use readable black or white ink. Essential numeric metadata wraps separately from long descriptive text. Status labels can wrap. Settings switches expose one labeled target and wrap beside their controls, paired transfer/backup actions can flow onto another line, and metadata checkboxes announce their field names.
- **Privacy:** Protected activities start with `FLAG_SECURE` before preferences load and retain it while the lock policy is unknown. Locked content is removed together with its sheets; secure flags remain active during an unlocked session when App Lock is enabled.

The shared-cover implementation follows [Android’s shared-element navigation guidance](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation). Capture uses a matched handoff because [shared elements do not span Dialog/ModalBottomSheet windows](https://developer.android.com/develop/ui/compose/animation/shared-elements).

## Compatibility

Room remains at schema **4**, with no record migration. The optional `reduceMotion` appearance field defaults to false when reading older archives. Release signing identity is unchanged. Production release output remains unsigned unless configured separately.

## Verification

Verification date: **November 8, 2026**, on Android 14/API 34 at 420 dpi, using JDK 22.0.1, Gradle 9.6.1, compile API 37 and target API 36.

| Check | Result |
| --- | --- |
| JVM suite | **188 passed**, zero failures/errors/skips |
| Debug app and instrumentation APKs | Built successfully |
| Android lint | **Zero errors, eight existing advisories** |
| Minified release APK | Built successfully; production output unsigned |
| Functional coverage across full run and rechecks | **238 distinct cases verified**; initial 232/238, then 27/27 UI and 1/1 SAF rechecks |
| Normal debug/minified runtime checks | **Passed** in both normal installed apps; minified capture Save/editor reopen also verified |
| Normal installed-app privacy checks | **Behavior checks and authenticated cleanup recheck passed**; Recents visual preview not verified |
| Final retained images | **Seven captured and inspected** from the passing UI recheck |

The [final build log](../build/redesign-m4-polish-final-build.log) records the successful build, JVM and lint checks. The [initial full regression](../build/redesign-m4-polish-regression.log) completed all 238 cases: 232 passed and six failed. The [27-case UI recheck](../build/redesign-m4-polish-recheck.log) and [isolated SAF recheck](../build/redesign-m4-polish-saf-recheck.log) passed, resolving all six failures against the same production APK. These overlapping runs cover **238 distinct cases**; their counts must not be added together.

The test harness now replaces text atomically, so a separate clear cannot commit a legitimate empty value before invalid replacement input arrives. Primary list-row actions are distinguished from nested status controls, with explicit pointer navigation retained in the wide-pane selection case. Detail-group reveal invokes the header’s semantic action and waits for the field. Product assertions remain intact. The SAF integration case uses a bounded three-minute timeout and phase timings for real provider/Keystore I/O and three unchanged, production-strength key derivations. Its [measured test body](../build/redesign-m4-polish-saf-timings.log) took approximately 85 seconds on this emulator, exceeding the previous one-minute limit. These harness changes leave the production APK unchanged. Initial failures and diagnostic runs remain available in the build logs.

## Normal installed-app verification

The [normal debug smoke](../build/redesign-m4-polish-debug-smoke.log) and [normal minified smoke](../build/redesign-m4-polish-minified-smoke.log) passed cold launch, four destinations, capture open/dismiss with normal motion and system animator scale zero, collection context at 600/840/1200 dp, Vault panes, and return to Today at the original window size. The initial minified launch returned Android’s launch timeout while the process later displayed correctly; a clean cold-launch retry completed. The helper also needed the current **Save to Inbox** label rather than the obsolete **Save** label. Both diagnostic failures are retained separately.

An additional [normal minified replay](../build/redesign-m4-polish-native-rtl.log) saved one uniquely named temporary record, opened and reopened its editor, retained it through actual font/locale/window changes, and opened its grid source at 200% font in RTL. The first and settled native captures showed readable dark status-bar icons; a light-icon frame from the immediate instrumentation capture did not reproduce. During configuration restoration, Android reported an [ANR in `com.android.systemui`](../build/redesign-m4-polish-native-system-diagnostic.log). Recovery used a cold start of the same AVD with its existing data. [Separate cleanup verification](../build/redesign-m4-polish-native-rtl-cleanup.log) confirms that the exact QA record is absent, the original collection view/font/locale/window are restored, and the app returned to Today. The native replay’s interrupted cleanup is preserved rather than labeled a clean complete run.

In the [normal debug privacy check](../build/redesign-m4-polish-privacy-smoke.log), credential-authenticated enable, the secure unlocked window, explicit lock, locked capture/voice/share entry, notification/random entry, and cold-start device-PIN unlock passed. Its initial label-based cleanup check was insufficient: the final cold start remained locked. The [authenticated cleanup recheck](../build/redesign-m4-polish-auth-recovery.log) used the actual checkable switch, verified that the secure-window flag cleared, and confirmed a fresh unlocked launch **before** removing the temporary emulator PIN. Another launch after removal confirmed the app remained accessible. The original lock-screen setting was restored. Native Recents capture attempts did not yield a usable image; actual secure flags are verified, but visual preview privacy remains a manual gate.

The [machine-checked evidence summary](../build/redesign-m4-polish-summary.json) validates the distinct test union, APK identities, retained image dimensions/black samples, runtime checks and cleanup. The [final device state](../build/redesign-m4-polish-final-device-state.json) confirms Today in the normal debug app, 1080 × 2400 px at 420 dpi, normal font scale, no app-locale override, no device credential and App Lock disabled. The [seven-image gallery](screenshots/redesign-m4-polish/README.md) retains synthetic fixtures separately from native diagnostic images.

These emulator launch and timing observations do not establish representative-device performance or transition quality.

## APK artifacts

The current installable developer build is [app-debug.apk](../app/build/outputs/apk/debug/app-debug.apk). The [minified release output](../app/build/outputs/apk/release/app-release-unsigned.apk) remains unsigned for production signing.

A [minified verification copy](../build/redesign-m4-polish-runtime/app-minified-verification.apk) uses the existing Android debug certificate solely for local installed-app checks. Its application payload is byte-for-byte identical to the unsigned minified output; signing adds certificate metadata. It is not a configured distribution signing identity.

| Artifact | SHA-256 |
| --- | --- |
| Debug | `9C3E66D666C9400765951107C422C4BE7FFB129F73410C49FC14F42049AE9161` |
| Minified unsigned | `6FD9C8FD9DECCD2EB688BB1E95DF395EA82302AF4158AA69E02DCEFEF8A7938A` |
| Minified verification, debug certificate | `D5E4FB7D26B60C50DD288ADD578EF43A941AFCE388DF61CF37E8AC4D980046D9` |

## Remaining acceptance gates

- A complete human TalkBack/keyboard/switch assessment across all tasks, beyond the targeted semantics and keyboard checks.
- Human Recent Apps preview/privacy review. Native screenshot capture was unavailable during the locked-app checks; a usable preview image was not retained.
- Physical tablet/foldable posture and hinge rendering, predictive gesture feel, and motion/haptics with system feedback enabled and disabled.
- OLED inspection and multiple real wallpaper palettes; the emulator can establish pixel values and behavior for its current palette only.
- Release signing with the intended distribution key. Local verification signing, if used, is not a production signing identity.
- Representative-device response/frame profiling and the moderated product-validation study proposed in the [roadmap](design/04-roadmap.md). Emulator measurements cannot establish those targets.
- Live provider credentials, cloud document-provider accounts and speech hardware/service checks remain environment-dependent integration assessments.
