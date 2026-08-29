# Cache-clear crash root cause — 2026-08-29

## Scope

Physical Samsung Fold `SM-F966U1`, Android 16, running Alakey `v2.6.2` with retained app data. The emulator was not running. No `pm clear`, uninstall, or cache deletion was used during diagnosis.

## Observed failure

Android DropBox records repeated background crashes across v2.6.1 and v2.6.2:

```text
java.lang.IllegalStateException: DatabaseSystem not started
  at com.example.alakey.system.DatabaseSystem.getDb(DatabaseSystem.kt:16)
  at com.example.alakey.data.FactStore.getFactDao(FactStore.kt:9)
  at com.example.alakey.data.FactStore.<init>(FactStore.kt:11)
  … HiltWorkerFactory.createWorker … WorkerWrapper.runWorker
```

The device recorded repeated `APP CRASH(EXCEPTION)` exits while the process was background-only. The visible app could work after a foreground launch, which made clearing cache look like a cure: clearing deletes WorkManager's pending work state. It does not repair the lifecycle bug.

## Causal chain

1. `DatabaseSystem.start()` runs only in `MainActivity.onCreate()`.
2. `FeedSyncWorker` is a Hilt `CoroutineWorker`, so Android may create it in a process with no activity.
3. Hilt constructs `UniversalRepository`, which constructs `FactStore`.
4. `FactStore.facts` immediately reads `DatabaseSystem.db`.
5. The database has not started, so worker creation throws before sync work can run.

## Fix

`AlakeyApplication.onCreate()` now starts the process-wide `DatabaseSystem` and `NetworkSystem` before Android can construct a Hilt worker. Both startup paths remain idempotent:

- `DatabaseSystem.start()` returns after the first Room instance exists.
- `NetworkSystem.start()` returns after the first OkHttp client exists.

The activity still calls both start methods, but they are now harmless second requests rather than the only startup path.

## Physical-device proof

The fixed code was built as an isolated `com.example.alakey.qa` debug package and installed alongside the legacy `com.example.alakey` v2.6.2 app; the legacy package was neither uninstalled nor cleared.

1. The QA app cold-launched on the Samsung Fold and WorkManager completed the immediate `FeedSyncWorker` successfully (`sync_immediate`).
2. The QA package was force-stopped, then its Hilt-injected debug receiver was started with an explicit `--include-stopped-packages` broadcast. The receiver logged `REPL: Received command: inspect-state`; there was no `DatabaseSystem not started`, `NetworkSystem not started`, `FATAL EXCEPTION`, or new QA crash exit.
3. The legacy app's retained data stayed unchanged: 1,236 KB database, 653 KB cache, 307,684 KB downloads, and 556 KB WorkManager state.

This is the process-start ordering that previously failed: a background entry point constructs `UniversalRepository` and `FactStore` before `MainActivity` runs. The fixed `AlakeyApplication` starts the process-wide systems first, so the same dependency graph is now safe.

## Verification

- `./gradlew testDebugUnitTest`: 29 tests, 0 failures, 0 errors.
- `./gradlew assembleDebug`: successful.
- `./gradlew :app:assembleRelease` with the configured release keystore: successful; APK Signature Scheme v2 verified.
- Physical-device QA retest: passed on the Fold in isolated `com.example.alakey.qa` without clearing or altering the legacy app's data.

## Evidence

- Raw device captures: `~/.opencrabs/projects/alakey-device-debug/2026-08-29-pre-stress/`
- Worker trigger captures: `~/.opencrabs/projects/alakey-device-debug/2026-08-29-worker-baseline-clean/`
- Isolated fixed-code QA captures: `~/.opencrabs/projects/alakey-device-debug/2026-08-29-qa-fixed-worker/`
- UI screenshot: `ui-audit/evidence/cache-repro-device-2026-08-29.png`
