# UX/UI Audit Resolution Ledger — 2026-08-23

Branch: `fix/ux-audit-2026-08-23`
Audit marker: `UXAUDIT-2026-08-23`

| Finding | GitHub | Resolution | Evidence |
|---:|---:|---|---|
| 1 | #5 | Wired/Bluetooth events are parsed; only a real disconnected→connected transition may resume. Sticky initial and disconnect events are inert. | `HeadsetResumeLogic.kt`; `UxPolicyTest` |
| 2 | #6 | Mini-player morph control takes an explicit white tint on dark glass. | `PlayerComponents.kt` |
| 3 | #7 | Live expanded player owns downward drag dismissal and a semantic 48dp close handle. Dead competing player path removed. | `PlayerComponents.kt`; `shouldDismissPlayer` tests |
| 4 | #8 | Slider keeps local scrub state and emits one seek on `onValueChangeFinished`. | `PlayerComponents.kt` |
| 5 | #9 | One 30-second constant drives regular/car icons, labels, and callbacks. | `PlayerTokens.kt`, `CarSystem.kt` |
| 6 | #10 | Search cancels the old job, waits 300ms, and guards result generations. | `AppViewModel.searchPodcasts` |
| 7 | #11 | Unsubscribe uses a named confirmation dialog. | `GlassFolderRow.kt` |
| 8 | #12 | Marketplace async state is reducer/ViewModel-owned; spinner clears to Done/Failed and supports retry. | `AsyncOp`, `AppReducer`, `GlassMarketplace` |
| 9 | #13 | Invisible trigger removed; overlay class moved from `main` to `debug`. | source-set inspection |
| 10 | #14 | Root, player, car, and dialogs use safe-drawing/IME insets. | `AlakeyUI.kt`, `PlayerComponents.kt`, `CarSystem.kt` |
| 11 | #15 | Dock is width-bounded, dialogs use `heightIn`, player branches by aspect ratio. | Compose source + device verification pending |
| 12 | #16 | All includes finished/sorted content; New is progress==0. | `LibraryFilters.kt`; `UxPolicyTest` |
| 13 | #17 | Dialog query/tab, sheets, prompt gate, marketplace tab, and folder expansion are saveable. | `rememberSaveable` source scan |
| 14 | #18 | All actionable icons and custom playback Canvas controls expose names/roles/state; decorative glow text is cleared. | semantics source scan; `AccessibilityUiTest` |
| 15 | #19 | Dock/header/player/row/car actions use ≥48dp interaction bounds. | component source + instrumentation build |
| 16 | #20 | Back priority is car mode → expanded player → navigation. | `AlakeyUI.kt` |
| 17 | #21 | Root is wrapped once in a stable dark `AlakeyTheme`; brand tokens centralized. | `MainActivity.kt`, `Theme.kt` |
| 18 | #22 | Sleep status is a compact chip; hero badge now says NOW PLAYING or FEATURED. | `PlayerComponents.kt`, `HomepageComponents.kt` |
| 19 | #23 | Singular copy and hour formatting fixed; artist mapped; no-op result download removed; shake threshold raised; import progress resolves by closing dialog. | `formatMs`; `UxPolicyTest`; `PodcastEntity.artistName` |
| 20 | #24 | Ambient plasma respects system animator scale; shimmer exists only for actual syncing. | `AnimationPolicy.kt`, `GlassSystem.kt` |
| 21 | #25 | Landscape player uses artwork/details split and retains all controls inside insets. | `PlayerComponents.kt`; device verification pending |
| 22 | #26 | Exactly one collapsed player is mounted. | `PlayerHost` call graph/source scan |
| 23 | #27 | Root tab navigation replaces rather than appends history. | `AppReducerTest` |
| 24 | #28 | Unused TFLite dependencies removed, eliminating the unaligned `.so`; notification prompt waits for first playback. | APK native-lib listing; Gradle manifest/build |
| 25 | #29 | Durable `AsyncOp` state exposes marketplace/download progress and failures with retry affordances. | reducer test + row components |
| 26 | #30 | `pressScale` is visual-only and contains no clickable. | source scan |
| 27 | #31 | Archive/delete menu actions require explicit confirmation. | `EpisodeRow.kt` |
| 28 | #32 | JVM policy/reducer tests expanded; Compose accessibility test added; CI builds instrumentation APK. | 21 JVM tests; `assembleDebugAndroidTest`; workflow |

## Rich Hickey certification

The remediation removes duplicate player/state representations and replaces composable-local guesses with explicit values. One operation has one owner, one player state has one visible projection, and one skip duration drives icon, label, and effect. The rejected alternative was layering more flags and compensating UI around the old duplication.
