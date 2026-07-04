package com.example.alakey.data

import android.util.Log
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
                } catch (e: Exception) {
                    Log.w("NetworkCall", "Call failed. Retrying in ${currentDelay}ms", e)
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }

            try {
                Result.success(block())
            } catch (e: Exception) {
                Log.e("NetworkCall", "Call failed after retries", e)
                Result.failure(e)
            }
        }
    }
}
