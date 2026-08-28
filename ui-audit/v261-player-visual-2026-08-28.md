# v2.6.1 Player Visual Smoke — 2026-08-28

**Target:** API 36 emulator (`alakey_api36`, 1080x2400) · **Build:** local debug off `fix/miniplayer-compact-height` (v2.6.1 base + fixes) · **Method:** uiautomator bounds + `dumpsys media_session` (vision unavailable this session; bounds are exact)

## Findings → fixes (PR #69)

1. **#67 — Compact mini-player stretched full-screen.** `heightIn(min = 76.dp)` set only a floor; `AnimatedVisibility` offers children the viewport as max height and `Row(fillMaxSize)` grabbed it. Card spanned y=362→2337. **Fix:** fixed `height(76.dp)`. Verified: strip now exactly `[42,362][1038,562]` = 200px ≈ 76dp.
2. **#68 — Cold start never restored.** `resumeLastPlayed()` orphaned since the MainActivity refactor; `Action.Restore` (#49) never dispatched → no strip, empty session on cold start. Restore also copied Play's `isPlayerOpen=true` (would auto-open the sheet). **Fix:** wired into `init`; Restore sets `current` only. Verified: force-stop → relaunch → strip present, sheet closed, session `PAUSED` at saved 539s.

## Gauntlet results

| Check | Result |
|---|---|
| Collapsed strip compact + pinned below header | ✅ 76dp, content flows below |
| Tap strip → fullscreen sheet opens | ✅ `Episode artwork` node appears |
| Back → returns to compact strip | ✅ sheet dismissed, strip at same bounds |
| Cold start: restored paused, no autoplay | ✅ `PAUSED(2), position=539282` |
| Resume plays from restored position | ✅ `PLAYING(3), position=542708` |
| Playback survives backgrounding | ✅ position advanced while at launcher |

## Evidence

- `evidence-2026-08-28/collapsed.png` — compact strip in layout flow
- `evidence-2026-08-28/expanded.png` — fullscreen sheet open
- `evidence-2026-08-28/playing.png` — resumed playback

## Notes

- Hero/sheet transport icons lack `content-desc` (a11y gap, also noted 08-24) — automating taps on the expanded transport required media keyevents.
- uiautomator dumps intermittently catch torn frames (10Hz position polling never lets the UI idle); verdicts were cross-checked against `dumpsys media_session`.
