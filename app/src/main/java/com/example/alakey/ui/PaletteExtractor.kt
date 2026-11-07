package com.example.alakey.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.alakey.data.PodcastPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PaletteExtractor @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun extract(url: String): PodcastPalette? {
        return try {
            // M62: the app-wide Coil singleton — a fresh ImageLoader per call
            // built its own OkHttpClient, memory/disk caches and executors, and
            // fetched+stored the artwork a SECOND time beside the AsyncImage
            // that renders it. Reuse the shared stack; no teardown is ours to do.
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request) as? SuccessResult ?: return null
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
            PodcastPalette(
                dominant = palette.getDominantColor(AndroidColor.CYAN),
                vibrant = palette.getVibrantColor(AndroidColor.CYAN),
                muted = palette.getMutedColor(AndroidColor.GRAY)
            )
        } catch (e: Exception) {
            // L13 family: a palette failure is invisible by design fallback, but
            // it is at least KNOWN now instead of a silent catch(e) -> null.
            android.util.Log.d("PaletteExtractor", "Palette extraction failed for $url", e)
            null
        }
    }
}
