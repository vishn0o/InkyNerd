# InkyNerd — Optimization & Audit Report

Scope: whole `app` module (Compose UI, widget/RemoteViews, Room, WorkManager, Retrofit).
Baseline: lint 0 errors / 85 warnings, 47 unit tests. After: lint 0 errors / ~50 warnings, 78 unit tests + 7 instrumented tests.

## Phase 1 — Architecture, rendering, memory & concurrency

| # | Finding | Severity | Fix |
|---|---|---|---|
| 1 | `AppContainer` built OkHttp, both Retrofit services, Room, 3 SharedPreferences files and DataStore eagerly in `Application.onCreate` — which also runs for every widget broadcast and the trampoline activity. Main-thread disk I/O on cold start. | High | Every heavy field is now `by lazy` (`WikiInkApp.kt`). |
| 2 | `WikiRepository` hard-coded `Dispatchers.IO` → not unit-testable. | Medium | Injected `io: CoroutineDispatcher = Dispatchers.IO`. |
| 3 | Reader: every Next/Prev/Rabbit Hole launched an independent load. Rapid taps raced and a slow, stale article could land on top of the newer one. | High | Single `loadJob`, previous one cancelled. |
| 4 | Reader lost its place after process death (only the nav-arg title was restored). | Medium | Current title / fact id mirrored into `SavedStateHandle`. |
| 5 | Quiz: double-tapping NEXT fired two network fetches (guard was set inside the coroutine). Same for Daily generation. Ending a round left the fetch running, which could pop an error onto the setup screen. | Medium | `rankedJob` / `dailyJob` guards; `endRanked()` cancels. |
| 6 | Double-tapped "Create folder" (Folders and Reader) created duplicate folders. | Medium | In-flight guards; names trimmed. |
| 7 | Widget prefs only had a `runBlocking` getter. All callers were already off-main (verified), but coroutine callers paid for a nested event loop. | Low | Added `suspend fun current()`; provider coroutine paths use it. |
| 8 | Periodic sync ran on any battery level. | Low | `setRequiresBatteryNotLow(true)` on the 12 h job (manual sync unaffected); policy `UPDATE` so existing installs pick it up. |

Already good (no change): MVVM with sealed `ReaderUiState`; StateFlow + `stateIn(WhileSubscribed)`; `collectAsStateWithLifecycle` everywhere; stable `key =` on every Lazy list; widget work runs in `goAsync()` + app scope; `CancellationException` rethrown in all catch blocks; no `GlobalScope`; no Activity/Context leaks (ViewModels hold only repository/prefs).

## Phase 2 — Bugs, security, performance

### Security
| Finding | Fix |
|---|---|
| `WidgetActionActivity` must be exported (Android 17 drops widget taps otherwise), and the provider receivers export NEXT/TOPIC filters → any app could drive or bookmark arbitrary widget ids / titles. | Widget id must be one this app placed (`isOwnWidget`); titles trimmed, ≤ 255 chars, no control characters; fact id length-capped. |
| No explicit cleartext policy. | `network_security_config.xml` (HTTPS only, system CAs) + `usesCleartextTraffic="false"`. |
| Backup rules included `wikiink.db` but not its WAL/SHM files → restores could miss recent writes. | `-wal` / `-shm` included in both rule files. |
| Secrets / keys | None stored (Wikipedia APIs are keyless; UA string is public by design). EncryptedSharedPreferences is unnecessary and `security-crypto` is deprecated — not added. |
| Runtime permissions | Only INTERNET / ACCESS_NETWORK_STATE (normal). Nothing to request. |

### Performance / data
| Finding | Fix |
|---|---|
| Widget deck query `WHERE sourceCategory = ?` and topic-count `GROUP BY sourceCategory` were unindexed. | `Index("sourceCategory")`, DB v3 + `MIGRATION_2_3` (pure `CREATE INDEX`, no data change). |
| No wake locks, no foreground services; images go through one shared OkHttp/Coil client. | — |
| User-Agent hard-coded "1.0". | Uses `BuildConfig.VERSION_NAME`. |

### Lint (85 → ~50)
Fixed: UseKtx ×15 (`SharedPreferences.edit {}`, `createBitmap`, `toUri`), UnusedResources ×14 (colors/dimens/strings and 3 drawables removed), ContentDescription ×8 (drawn-text twins), RtlSymmetry ×5, ObsoleteSdkInt (`mipmap-anydpi-v26` → `mipmap-anydpi`). Also removed 4 dead widget drawables (`widget_bg_border*`, `widget_item_bg*`) that referenced colors deleted in that pass and broke resource linking.
Left deliberately: dependency/AGP/targetSdk version notices (upgrade as a separate, tested change), UnusedAttribute (API-31 widget attributes, ignored safely on older OS), SmallSp (10 sp chips are a design choice), UselessParent in the widget container (restructuring risks the launcher-specific Stack layout).

### Edge cases verified by tests
Offline article with/without cache, empty archive, rotation (`recreate`), process death, rapid Next, double taps (folder, ranked, daily), default folder undeletable, widget bookmark toggle on/off, missing default folder recreated, DB upgrade v1 → v3 preserving facts and bookmarks.

## Tests added
Unit (`app/src/test`, JUnit4 + MockK + Turbine + coroutines-test) — all passing:
- `ui/reader/ReaderViewModelTest` (9)
- `ui/quiz/QuizViewModelTest` (6)
- `ui/bookmarks/BookmarksViewModelTest` (5)
- `data/repository/WikiRepositoryTest` (9)
- `widget/WidgetActionInputTest` (2)

Instrumented (`app/src/androidTest`):
- `data/local/MigrationTest` — Room v1 → v3
- `ui/bookmarks/BookmarksScreenTest` — Compose UI against in-memory Room (4)
- `MainActivitySmokeTest` — launch, recreate (rotation), background/foreground

Run: `./gradlew testDebugUnitTest` and, **with only an emulator connected**, `./gradlew connectedDebugAndroidTest` (it uninstalls the app from every connected device afterwards).
