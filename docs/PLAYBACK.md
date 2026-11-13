# Playback Architecture and Behavior

## Core Components
- `Player`: Main playback interface implemented by `PlayerImpl`.
- `QueueController`: Manages playback queue, candidate pool, and reverse play history stack.
- `RepeatPolicy`: Multi-layer repetition defense (session deduplication, 48-hour cooldown, 1-hour quick-skip rest, canonical key normalization).
- `MixPolicy`: Language and mood balance for automatic radio picks.
- `PlayHistoryStore`: Local SQLite persistence (schema version 8) storing play sessions and `repeat_events`.

## Playback Navigation
1. **Smart Previous Track Navigation**:
   - If played time exceeds 3 seconds (`SMART_PREV_THRESHOLD_MS`), pressing previous seeks to the beginning of the track (or sponsor/intro skip offset).
   - If played time is within 3 seconds, pressing previous pops from `QueueController.playHistory` and loads the previous song in reverse play order.
2. **Quick-Skip Rest**:
   - If an automatically picked song is skipped within 10 seconds (`QUICK_SKIP_THRESHOLD_MS`), it is rested for 1 hour (`SKIP_REST_DURATION_MS = 3_600_000L`).
   - The in-memory rest is applied synchronously in `PlayerImpl.skipNext()` before the next candidate is selected, guaranteeing the skipped track is never picked immediately.
   - The persistent record is written asynchronously to SQLite `repeat_events` via `PlaybackCoordinator.onTrackQuickSkipped`.
   - Manually selected tracks (user search taps, explicit queue additions) are never rested.

## Database Schema
The SQLite play history database uses schema version 8 (`PlayHistorySchema.version = 8L`).
The `repeat_events` table contains:
- `item_key`: Primary item identifier.
- `played_at`: Timestamp of playback or skip event.
- `weight`: Cooldown weight.
- `rest_until`: Timestamp until which the song is suppressed from auto-picks.
- `canonical_key`: Normalized title and artist key for multi-upload deduplication.

## Known Limits
1. **Heuristic Language Detection**:
   - Language detection is heuristic and relies on native scripts, verified exclusive record labels, unique vocabulary, and multilingual artist groups.
   - Latin-script titles without decisive starter vocabulary or exclusive labels resolve to Unknown. The system intentionally avoids guessing or defaulting to English.
2. **Mood Classification**:
   - Mood classification (Calm vs Energetic) operates strictly on title keywords (e.g. "lofi", "acoustic", "melody", "remix", "edm").
   - Titles without explicit mood keywords resolve to Unknown, and the mix policy allows natural queue progression.
3. **Similarity and Discovery**:
   - YouTube radio recommendation order remains the primary similarity signal for next-track discovery. Mix and repeat policies act as safety filters, not generative recommenders.
4. **Per-Device Repeat History**:
   - Play history and repeat cooldowns are stored locally on each device. Listening history is not synchronized across devices.

## Note on QuickJS in CI
- **Compilation Cost**: Building the QuickJS native JNI library on Linux (`ubuntu-latest`) requires CMake and GCC, adding approximately 10 seconds of build time.
- **Test Suite Runtime**: Running the full 65 tests in `:commonUi:jvmTest` requires `xvfb-run` for Compose Desktop UI tests and extends CI test execution by 1.5 to 2.5 minutes.
- **Flakiness Considerations**: Compose Desktop UI tests (such as `FeedbackScreenTest`) can exhibit intermittent timing flakiness across virtualized CI runners without hardware display acceleration.
- **Recommendation**: If QuickJS and full UI tests are added to CI in the future, they should run as an optional or informational workflow rather than a blocking PR merge gate.
