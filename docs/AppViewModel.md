fix # `AppViewModel.kt` code review

Reviewed: 9 October 2026  
Scope: `app/src/main/kotlin/app/melosome/claimconnect/ui/AppViewModel.kt` only  
Code changes: none

## Summary

The ViewModel has a clear immutable state model, serialises repository commands, rethrows coroutine cancellation, and keeps save failures visible. Before production, the save/navigation contract and decrypted share-file lifecycle should be tightened. The remaining findings improve resilience, performance, error accuracy, and maintainability.

## Findings

### Resolved: navigation waits while an edit is being saved

Locations: lines 25, 75-81, and 97-106.

`edit()` increments `pendingWrites` without setting `busy`. Previously, `canLeaveEditor()` checked `busy` and `saveFailed` but not `pendingWrites`, so it could return `true` before the latest edit reached storage.

Resolution: `canLeaveEditor()` now returns `false` while `pendingWrites > 0`. The editor already displays `Saving…` for this state, and an inline code comment records why the navigation guard is required. A focused navigation test is still recommended.

### High: decrypted share files have no explicit cleanup lifecycle

Locations: lines 145-157.

`shareWithAi()` writes decrypted photographs into the persistent app cache `share` directory. The method neither clears files from earlier shares nor schedules the new files for deletion. App-private cache is safer than public storage, but the plaintext copies may remain until Android evicts them.

Recommendation: use a per-share temporary directory, remove stale share files before creating a new share, and define cleanup after sharing or during the next startup. Document the lifecycle so the implementation matches the comment's claim that the copy is temporary.

### Medium: photograph decryption and file writes can block the main thread

Locations: lines 38-43 and 148-156.

The command consumer runs in `viewModelScope`, whose default dispatcher is Main. `File.mkdirs()` and `File.writeBytes()` are synchronous, and several full-size photographs may be processed in one command. This can make the UI unresponsive or cause an application-not-responding event.

Recommendation: perform decryption and file I/O on an injected I/O dispatcher, returning to Main only for state updates and UI events.

### Medium: rapid edits can create an unbounded persistence backlog

Locations: lines 36, 63, and 97-106.

Every call to `edit()` adds a complete save operation to `Channel.UNLIMITED`. If text fields call `edit()` for each keystroke, the queue can grow without a limit and write stale intermediate revisions before reaching the current value.

Recommendation: coalesce pending edits so only the latest complete incident snapshot is saved, while retaining an explicit flush/await mechanism before navigation. Use a bounded strategy rather than an unlimited queue.

### Medium: a successful later save can leave a stale error visible

Locations: lines 101-105 and 108-115.

When an automatic save succeeds, it clears `saveFailed` but not `error`. If one queued revision fails and a later revision succeeds, leaving is allowed again while the earlier failure message remains visible. `retrySave()` already clears both fields, so the two success paths behave differently.

Recommendation: make successful-save state handling consistent and associate failures with a revision so an older result cannot describe newer state.

### Medium: the incidents stream stops permanently after one exception

Locations: lines 57-60.

The `collect` call is wrapped in a single `try/catch`. A non-cancellation exception displays an error and then ends the collector, so the incident list will not receive later updates during that ViewModel's lifetime.

Recommendation: use a deliberate retry/re-subscription policy for recoverable failures. Keep permanent corruption failures terminal, but represent that state explicitly.

### Medium: UI callbacks can turn successful data operations into apparent failures

Locations: lines 83-88, 117-126, 144, 148-157, and 163-170.

Navigation and file-use callbacks execute inside the queued command. If a callback throws after a repository mutation succeeds, the shared command handler reports the entire action as failed. In `importPhotos()`, an exception thrown by `onComplete` in `finally` can also mask the original import exception.

Recommendation: publish one-time UI events after repository work succeeds, or invoke callbacks outside the repository error boundary. Do not allow callback failures to replace persistence failures.

### Medium: exception text is exposed through brittle message matching

Locations: lines 214-218.

`IllegalArgumentException.message` is shown directly. Selected `IllegalStateException` messages are also exposed when they contain `storage`, `pack`, or `photograph`. Internal exception text can be technical, inconsistent, or unsuitable for users, and keyword matching can misclassify unrelated failures. The fallback also assumes device storage is the cause of every other exception.

Recommendation: map typed domain failures to approved UI messages. Log the original exception separately and use a neutral fallback when the cause is unknown.

### Low: ignored channel-send failures can leave state stuck

Locations: lines 63-73 and 97-106.

`commands.trySend(block)` discards its result. If the channel cannot accept a command, `operation()` has already set `busy = true`, while `edit()` has already incremented `pendingWrites`; neither state is rolled back.

Recommendation: handle the send result and restore state on failure, or use structured coroutine APIs that make command acceptance explicit.

### Low: public state transitions are not consistently guarded

Locations: lines 93-96 and 136-142.

`step()` can change navigation state while another operation is busy or after a save failure. It can also race with `generate()`, which assigns step `5` when its queued work finishes.

Recommendation: route step changes through the same transition guard or make valid transitions explicit in one state machine.

### Low: UI text, keys, and step identifiers are embedded in the ViewModel

Locations: lines 50-53, 60, 66, 70, 80, 90, 94, 141, 161-196, and 214-218.

Hard-coded user messages bypass Android resources and make localisation and copy testing harder. Repeated saved-state keys and numeric steps (`0`, `4`, and `5`) are also easy to mistype or invalidate during future flow changes.

Recommendation: represent UI messages with resource-backed identifiers, centralise saved-state keys, and replace numeric steps with named values or a constrained type.

## Suggested order of work

1. Make navigation wait for pending saves and test the failure paths.
2. Move share-file work off Main and add deterministic plaintext cleanup.
3. Bound or coalesce edit saves and make save results revision-aware.
4. Separate repository outcomes from UI callbacks and retry the incidents stream where appropriate.
5. Replace exception-message heuristics, then centralise resources, keys, and step identifiers.

## Suggested focused tests

- Navigation requested while `pendingWrites` is greater than zero.
- An earlier revision fails and a later revision succeeds.
- Channel submission fails after state has been marked busy or pending.
- The incidents flow fails once and then becomes available again.
- A repository operation succeeds but its UI callback throws.
- Share preparation processes several large photographs and removes stale plaintext files.
