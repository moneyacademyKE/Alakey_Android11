package com.example.alakey.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M66: plain runCatching turns a cancelled caller into Result.failure —
 * data-shaped failure for a lifecycle event. suspendRunCatching must
 * rethrow CancellationException and otherwise behave like runCatching.
 */
class SuspendRunCatchingTest {

    @Test
    fun successBehavesLikeRunCatching() = runBlocking {
        assertEquals(42, suspendRunCatching { 42 }.getOrNull())
    }

    @Test
    fun ordinaryFailureBehavesLikeRunCatching() = runBlocking {
        val boom = RuntimeException("boom")
        assertEquals(boom, suspendRunCatching<Any> { throw boom }.exceptionOrNull())
    }

    @Test
    fun cancellationIsRethrownNotReturnedAsFailure() = runBlocking {
        val thrown = runCatching {
            suspendRunCatching<Int> { throw CancellationException("cancelled") }
        }.exceptionOrNull()
        assertTrue("expected CancellationException to propagate, got $thrown", thrown is CancellationException)
    }
}
