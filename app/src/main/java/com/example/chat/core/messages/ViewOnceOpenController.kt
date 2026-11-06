package com.example.chat.core.messages

import kotlinx.coroutines.CancellationException

/** Orchestrates opening a view-once message: consumed guard, single
 *  in-flight gate, durable-consume-before-reveal ordering, fail-closed
 *  write-failure and exception paths, and identity revalidation around
 *  suspensions. Pure Kotlin so the ordering is JVM-tested; the Compose
 *  screen holds one instance per remember(account, session, conversation)
 *  and delegates to it.
 *
 *  Session model (both must hold at every check):
 *  - The identity tuple captured at creation ([expectedSession]) must equal
 *    the tuple currently on screen ([currentSession]): a controller that
 *    survives an account switch must not validate against the new session.
 *  - The session checker captured at creation ([sessionStillValid]) must
 *    still report valid: live auth revocation is honored even when the
 *    on-screen tuple has not changed yet.
 *
 *  Contract:
 *  - An id that is already consumed yields AlreadyConsumed and no write:
 *    a duplicate tap landing after the first write but before the UI swaps
 *    the locked card cannot reopen consumed media.
 *  - A tap while another open is in flight is rejected (Busy).
 *  - The session is checked BEFORE the durable write too: an initially
 *    stale open never marks the message consumed.
 *  - Media is revealed (RevealMedia) only after the durable consume write
 *    succeeds AND both session checks still pass.
 *  - A failed write, or a non-cancellation exception from storage, yields
 *    ConsumeFailed: nothing is revealed; the screen keeps the card locked
 *    and shows a safe error. CancellationException always propagates.
 *  - Voice reveals immediately (RevealVoice): tapping only swaps the locked
 *    card for the in-bubble player; the note is consumed on playback
 *    completion via [consumeAfterPlayback], which is best-effort — playback
 *    already happened, so that write cannot be a precondition. A failed or
 *    throwing write is reported as false; cancellation propagates. */
class ViewOnceOpenController(
    /** Identity tuple captured when this controller was created. */
    private val expectedSession: Any,
    /** Identity tuple currently on screen; re-checked around suspensions. */
    private val currentSession: () -> Any?,
    /** Session checker captured at creation (NOT updated on recomposition):
     *  live auth revocation for the original session. */
    private val sessionStillValid: () -> Boolean,
    /** Durable consumed-state read (cheap; SharedPreferences). Guards
     *  duplicate taps racing the locked-card removal. */
    private val isConsumed: (String) -> Boolean,
    /** Synchronous durable consume, run off the main thread by the caller's
     *  coroutine context; returns false when the write did not reach disk. */
    private val persistConsumed: suspend (String) -> Boolean,
) {
    /** True between gate acquire and settle. */
    var openInFlight: Boolean = false
        private set

    sealed interface Result {
        val id: String

        /** Voice note: show the in-bubble player. */
        data class RevealVoice(override val id: String) : Result

        /** Media write succeeded and identity still matches: reveal now. */
        data class RevealMedia(override val id: String) : Result

        /** Already consumed: nothing to reveal or write. */
        data class AlreadyConsumed(override val id: String) : Result

        /** Durable write failed or threw: keep locked, show a safe error. */
        data class ConsumeFailed(override val id: String) : Result

        /** Identity mismatch or revoked session: nothing happened. */
        data class StaleSession(override val id: String) : Result

        /** A tap arrived while another open was in flight. */
        data class Busy(override val id: String) : Result
    }

    private fun sessionMatches(): Boolean =
        currentSession() == expectedSession && sessionStillValid()

    /** Opens a view-once message. Suspends only for the media write. */
    suspend fun open(id: String, isVoice: Boolean): Result {
        if (openInFlight) return Result.Busy(id)
        // Read failures fail closed: unknown consumed state blocks the open
        // (ConsumeFailed -> storage error), never treated as "not consumed".
        val consumed = try {
            isConsumed(id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            return Result.ConsumeFailed(id)
        }
        if (consumed) return Result.AlreadyConsumed(id)
        // Checked before the write: an initially stale open must not mark
        // the message consumed.
        if (!sessionMatches()) return Result.StaleSession(id)
        openInFlight = true
        try {
            if (isVoice) {
                return if (sessionMatches()) Result.RevealVoice(id) else Result.StaleSession(id)
            }
            val persisted = try {
                persistConsumed(id)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Storage failure is fail-closed: same handling as a false
                // return. Never let a prefs exception escape the launch.
                return Result.ConsumeFailed(id)
            }
            if (!persisted) return Result.ConsumeFailed(id)
            return if (sessionMatches()) Result.RevealMedia(id) else Result.StaleSession(id)
        } finally {
            openInFlight = false
        }
    }

    /** Voice playback completed: best-effort durable consume. The caller
     *  treats the id as consumed in memory regardless of the result; a
     *  throwing write reports false; cancellation propagates. */
    suspend fun consumeAfterPlayback(id: String): Boolean = try {
        persistConsumed(id)
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        false
    }
}
