package com.example.alakey.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.ImageLoader
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
            val loader = ImageLoader(context)
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
            null
        }
    }
}
