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

        client.newCall(Request.Builder().url(podcast.audioUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

            val file = File(context.filesDir, "${podcastId.safeFileName()}.mp3")
            val body = response.body ?: throw Exception("Empty response body")
            body.byteStream().use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            factStore.assert(podcastId, InformationModel.ATTR_AUDIO_PATH, file.absolutePath)
            factStore.assert(podcastId, InformationModel.ATTR_DOWNLOADED, "true")
            file.absolutePath
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
