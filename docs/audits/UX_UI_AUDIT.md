> **Status (2026-11-07): HISTORICAL.** Findings below are as originally filed; several were
> later verified FALSE at HEAD (see the audit ledger: L66 in AUDIT_20261107-1007).
> Superseded by the numbered ledger in the alakey-audit-pulse reports — fixed/open
> status lives there, not here. Two-finding item 2 also had heading/body disagreement.

# Alakey — UX/UI Audit (2026-08-23)

Scope: full Compose UI surface (~2.9k LOC) plus a live-device pass on Samsung SM-F956B (Android 16/API 36) over USB ADB. Source findings are backed by file + symptom; live findings are backed by UIAutomator hierarchies, screenshots, interactions, and logcat. Evidence: `~/.opencrabs/projects/alakey-debug/ui-audit/`.



## Critical — functional bugs

1. **Unplugging headphones RESUMES playback out loud.**
   `AlakeyUI.kt` (~L90): the headset receiver calls `vm.resumePlayback()` on *any* `ACTION_HEADSET_PLUG` event without reading the `state` extra (0 = unplug, 1 = plug). Pull your headphones → audio blasts from the speaker. Worse: `ACTION_HEADSET_PLUG` is sticky — the receiver fires on *registration*, so opening the app with headphones connected can auto-resume playback uninvited. Fix: check `intent.getIntExtra("state", -1) == 1`, and for Bluetooth check `EXTRA_STATE == STATE_CONNECTED`.

2. **Mini-player play/pause button is black-on-dark = invisible.**
   `GlassSystem.kt` `MorphingPlayPauseButton` draws `Color.Black` paths. In `MiniPlayerDock` and the `FluxPlayerContinuum` mini layout it sits on `PrismaticGlass` (white 5% over black) — effectively black-on-black. Only the full player's white 80dp circle gives it contrast. Fix: pass a tint.

3. **Full player has no swipe-to-dismiss; the only on-screen close is a 40×4dp handle.**
   The drag-to-close gesture lives in `GlassPlayerScreen` — which is **dead code** (defined, never called; 1 grep match = its own definition). The live path is `FluxPlayerContinuum` → `GlassPlayerMechanism`, where `dragOffset` is always `0f`. The drag "handle" is `width(40).height(4)` — a 4dp-tall tap target. Users close via system back or by hitting a 4dp sliver. Fix: delete dead screen, add real drag-to-dismiss to the mechanism.

4. **Scrubbing seeks on every pixel — audio stutters while dragging.**
   `GlassPlayerMechanism` Slider `onValueChange` calls `onSeek(it.toLong())` directly; no `onValueChangeFinished`. Every drag frame issues a real player seek. Fix: track local value, seek on release.

5. **Skip icons lie about what they do.**
   Player: `Replay10` icon → `onSkip(-15)`. Car mode: `Replay30` icon → wired in `AlakeyUI.kt` to `vm.skip(-15)`. Both show the wrong number; car mode also disagrees with the player (30 vs 15). Fix: one icon source of truth for skip amounts.

6. **Per-keystroke search with a last-write-wins race.**
   `AddPodcastDialog`: `onValueChange = { text = it; onSearch(it) }` fires a network search on every keypress; `AppViewModel.searchPodcasts` has no debounce and no request sequencing — a slow early response overwrites a fresh one (stale/flickering results). Fix: debounce ~300ms + cancel/sequence requests.

7. **Unsubscribe is a one-tap destructive trash icon.**
   `GlassFolderRow.kt`: red `Delete` IconButton beside every show folder → `vm.unsubscribe(title)` immediately. No confirmation, small target, sits exactly where a mis-tap during scroll lands. Icon semantics also wrong (delete ≠ unsubscribe). Fix: confirm dialog or undo snackbar.

8. **Marketplace rows show a spinner forever and a checkmark that can lie.**
   `GlassMarketplace.kt`: on tap, `subscribingQuery` is set and `subscribedQueries.add(query)` — the spinner never clears (no completion callback), and the checkmark is purely local state with no rollback when the subscribe search fails (VM only toasts "No results found"). Also `subscribedQueries` is `remember`ed, so the checkmarks vanish on recreation while the spinner state persists in the same composition. Fix: derive state from the DB, clear `subscribingQuery` on completion/failure.

9. **Hidden debug trigger + time-travel overlay shipped in release.**
   `AlakeyUI.kt`: invisible 64×64 clickable top-left (`zIndex(10f)`) toggles `DebugOverlay`, whose slider *rewinds app state live*. It overlaps the header/title tap area, has zero semantics for screen readers, and lives in the `main` source set — reachable in production builds. Fix: debug-only gating + build-type source set.

## Layout & form factor (this is a Fold app)

10. **Zero inset handling anywhere.** No `statusBarsPadding`/`WindowInsets`/cutout handling (grep: 0 matches; only the player lerps nav-bar padding). Header is hardcoded `top = 48.dp`. On the cover display, notched devices, or landscape, the title collides with or floats under system bars. Fix: `Modifier.statusBarsPadding()` etc.

11. **Fixed sizes break on real screens.** Glass dock is `width(336.dp)` — overflows a 320dp cover screen. Add-dialog is `height(400.dp)` — clips at large font scale/keyboard. Player column (300dp artwork + titles + slider + two control rows ≈ 670dp) doesn't fit landscape or half-open fold postures — no scrolling, no adaptive layout, no posture/hinge awareness at all.

12. **"All" isn't all, and "New" isn't new.** `AlakeyUI.kt` filters: the `else`/"All" branch also applies `progress < duration*0.95` — finished episodes silently vanish from the library with no view showing them; and "All" applies **no sort** (DB order), while "New" uses the identical predicate (a half-played episode appears under "New"). Fix: All = everything sorted; New = `progress == 0`.

13. **UI state dies on any configuration change.** No `rememberSaveable` anywhere (grep: 0). Fold↔unfold, rotation, process death → folder expansions collapse, dialog tabs/text lost. On a foldable this is a core flow bug, not an edge case.

## Touch targets & accessibility

14. **The entire app is unlabeled for TalkBack.** Every single `Icon(...)` passes `contentDescription = null` — nav dock, transport controls, download/queue actions, all of it. Additionally `NebulaText` renders the text twice (blur layer + crisp layer) → double announcement per element.

15. **Sub-minimum touch targets on primary controls.** Drag handle 40×4dp (see #3), dock items ≈ 28–36dp tall for the four main destinations, row trailing icons 24dp. Material minimum is 48dp.

16. **System back ignores car mode.** `BackHandler` handles player-then-nav-stack; while `CarModeScreen` is up, back pops the navigation stack *underneath* the car overlay instead of exiting car mode. In a car, the expected behavior is exit.

## Visual & polish

17. **Theme is defined but never applied.** `AlakeyTheme` + custom `Typography` are dead code — `MainActivity.setContent { MainContent() }` renders with stock defaults while every component hardcodes `Color.White`/`0xFF00F0FF`. Two sources of truth, neither actually governing.

18. **Sleep-timer overlay dims the whole artwork 40%** just to show a countdown (`GlassPlayerMechanism`), and the hero shows the "NEBULA FEATURE" badge even for the currently-playing episode.

19. **Papercuts:** "Currently 1 minutes remaining" (integer-div + no plural); `formatMs` has no hours (a 75-min episode renders `75:20`); expanded folder header swaps "$count episodes" for the redundant instruction "Tap to collapse"; search-result rows have an empty subtitle (`artistName` never mapped) and a dead download button (`onDownload = {}`); shake-to-extend fires "Sleep Timer Extended" toasts from pocket motion (threshold 800 is low); "Syncing…" button in the URL tab can stick forever (no completion signal).

20. **Always-animating UI = battery/jank.** Full-screen `RuntimeShader` FluxBackground animates continuously on every screen, plus a `glassShimmer` infinite animation per list row. On the Fold's main display this is a battery tax. Consider pausing when the window isn't focused / not interactive, and shimmer only on sync states.



## Additional live-device findings

21. **Full player becomes functionally blank in landscape (live P0).**
    On the Fold cover display, `full-player-landscape.xml` contains no episode title, artist, seek times, or speed text; transport hit regions are clipped to `y=869..992`. The live player is a fixed portrait `Column` in `GlassPlayerMechanism`. Fix: an adaptive landscape split (artwork left, controls right) with safe insets.

22. **Two collapsed players render at once (live).**
    `MainContent` mounts `FluxPlayerContinuum` whenever `state.current != null`, then also mounts `MiniPlayerDock` when the player is closed. UIAutomator showed the same title/artist in two overlapping bottom cards. Fix: keep exactly one collapsed-player implementation and delete the other.

23. **Bottom navigation is an accidental history stack.**
    `AppReducer.Navigate` appends every destination. Library → Inbox → Marketplace → Queue requires repeated Back presses through tabs rather than exiting. Fix: bottom destinations replace the active root; reserve stacks for drill-down screens.

24. **First launch is blocked twice before product value.**
    Android 16 showed an app-compatibility warning because `libtensorflowlite_jni.so` is not 16 KB aligned; after that, the app immediately requests notification permission with no rationale and ignores denial. Fix the native dependency, then request notifications contextually when playback makes the value clear.

25. **Errors and long operations lack durable UI state.**
    App success/error events are Toast-only; manual download failures only reach logcat; `PodcastRowSpec` can represent only booleans, not byte progress or failure. Fix: explicit async values in UI state and inline/Snackbar retry surfaces.

26. **`pressScale()` creates a phantom nested click action.**
    The modifier ends with `.clickable { }`, then callers add the real `.clickable`, producing duplicate semantics/gesture nodes. Fix: make it visual-only with a shared interaction source, or make it own the real callback—never both.

27. **Destructive bulk actions have no confirmation or undo.**
    Long-press menu items “Archive All Older” and “Delete Download” execute immediately, just like unsubscribe. Fix: confirm bulk scope and offer Snackbar undo where the operation is reversible.

28. **No meaningful UI regression suite.**
    The sole instrumented test verifies only the package name; CI runs unit tests and assembles the APK. Rotation clipping, duplicate players, IME behavior, navigation, and semantic labels have no witness. Fix: Compose UI tests for critical portrait/landscape flows and semantics.
## Suggested fix order

1. Stop unsafe playback resume and fix the invisible mini-player control.
2. Fix landscape player geometry; keep one collapsed player.
3. Move marketplace/search async state into the ViewModel; debounce and cancel stale search.
4. Add labels/roles/states to every actionable control and enforce 48dp targets.
5. Add confirmation/undo for unsubscribe, archive, and delete.
6. Replace bottom-nav history stacking; then add portrait/landscape/IME/semantics UI tests.

## Rich Hickey verdict

The recurring defect is complected truth: local composable flags claim network success while repositories own the real result, and multiple player components represent one playback state simultaneously. Fix state ownership and component multiplicity before polishing the plasma. One fact, one owner, one visible representation.
