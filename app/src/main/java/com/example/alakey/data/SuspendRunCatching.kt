package com.example.alakey.data

import kotlinx.coroutines.CancellationException

/**
 * M66: `runCatching` catches `Throwable`, so a cancelled caller's
 * `CancellationException` surfaces as a data-shaped `Result.failure` —
 * the caller looks *failed*, not *cancelled*, and callers that report
 * failures to the UI dispatch error state for a coroutine that was simply
 * cleared. Every suspend error boundary goes through here so cancellation
 * stays a lifecycle event; the non-suspend catches in the tree are fine
 * because cancellation cannot reach a non-suspend function.
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
