package com.example.alakey.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkCall @Inject constructor() {
    suspend fun <T> run(retries: Int = 3, initialDelay: Long = 2000, block: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            var currentDelay = initialDelay
            repeat(retries - 1) {
                try {
                    return@withContext Result.success(block())
                } catch (e: CancellationException) {
                    // M66: a cancelled caller is not a failed call — rethrow so
                    // cancellation stays a lifecycle event up the chain.
                    throw e
                } catch (e: Exception) {
                    Log.w("NetworkCall", "Call failed. Retrying in ${currentDelay}ms", e)
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }

            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NetworkCall", "Call failed after retries", e)
                Result.failure(e)
            }
        }
    }
}
