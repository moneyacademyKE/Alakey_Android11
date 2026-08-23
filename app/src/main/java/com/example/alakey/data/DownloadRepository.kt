package com.example.alakey.data

import android.content.Context
import android.util.Log
import com.example.alakey.domain.InformationModel
import com.example.alakey.domain.PureLogic
import com.example.alakey.system.DatabaseSystem
import com.example.alakey.system.NetworkSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request

@Singleton
class DownloadRepository @Inject constructor(
    private val dbSystem: DatabaseSystem,
    private val networkSystem: NetworkSystem,
    private val factStore: FactStore,
    private val networkCall: NetworkCall,
    @ApplicationContext private val context: Context
) {
    private val dao get() = dbSystem.db.dao()
    private val client get() = networkSystem.client

    suspend fun runSmartDownloads() {
        withContext(Dispatchers.IO) {
            val library = factStore.hydrateAll(dao.getAllPodcasts().first())
            PureLogic.determineDownloadCandidates(library).forEach { id ->
                downloadAudio(id).onFailure { Log.e("DownloadRepository", "Smart download failed for $id", it) }
            }
        }
    }

    suspend fun downloadAudio(podcastId: String): Result<String> = networkCall.run {
        val podcast = dao.getPodcastById(podcastId) ?: throw Exception("Podcast not found")
        if (podcast.audioUrl.isEmpty()) throw Exception("No audio URL")

        val file = File(context.filesDir, "${podcastId.safeFileName()}.mp3")
        val partial = if (file.exists()) file.length() else 0L
        val request = Request.Builder().url(podcast.audioUrl).apply {
            if (partial > 0) header("Range", "bytes=$partial-")
        }.build()

        client.newCall(request).execute().use { response ->
            when {
                // 416: server says we already have the full range — download complete
                response.code == 416 -> Unit
                // 206: resume from the partial file, append the remaining bytes
                response.code == 206 && partial > 0 -> copyBody(response, file, append = true)
                // 200: server ignored the Range header — start over from byte 0
                response.isSuccessful -> copyBody(response, file, append = false)
                else -> throw Exception("Download failed: ${response.code}")
            }
            factStore.assert(podcastId, InformationModel.ATTR_AUDIO_PATH, file.absolutePath)
            factStore.assert(podcastId, InformationModel.ATTR_DOWNLOADED, "true")
            file.absolutePath
        }
    }

    private fun copyBody(response: okhttp3.Response, file: File, append: Boolean) {
        val body = response.body ?: throw Exception("Empty response body")
        body.byteStream().use { input ->
            FileOutputStream(file, append).use { output -> input.copyTo(output) }
        }
    }

    suspend fun deleteDownload(id: String) {
        withContext(Dispatchers.IO) {
            val podcast = factStore.hydrate(dao.getPodcastById(id) ?: return@withContext)
            if (podcast.isDownloaded && podcast.audioUrl.startsWith("/")) {
                File(podcast.audioUrl).takeIf { it.exists() }?.delete()
            }
            factStore.assert(id, InformationModel.ATTR_DOWNLOADED, "false")
            factStore.assert(id, InformationModel.ATTR_AUDIO_PATH, "")
        }
    }

    private fun String.safeFileName(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
