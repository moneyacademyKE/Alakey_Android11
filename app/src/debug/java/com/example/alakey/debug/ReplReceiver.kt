package com.example.alakey.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.alakey.BuildConfig
import com.example.alakey.data.UniversalRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * REPL-Driven Development Receiver (DEBUG BUILDS ONLY — declared in src/debug/AndroidManifest.xml,
 * so release APKs contain neither the receiver nor this class).
 * Example ADB Command (explicit -n form — Android 16 drops implicit shell broadcasts):
 * adb shell am broadcast -n com.example.alakey/.debug.ReplReceiver -a com.example.alakey.REPL_EVAL --es cmd "'subscribe https://feed.rss'"
 * adb shell am broadcast -n com.example.alakey/.debug.ReplReceiver -a com.example.alakey.REPL_EVAL --es cmd "'download <episode-id>'"
 */
@AndroidEntryPoint
class ReplReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: UniversalRepository
    @Inject lateinit var audioSystem: com.example.alakey.system.AudioSystem
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return  // dev REPL never runs in release builds
        if (intent.action == "com.example.alakey.REPL_EVAL") {
            val cmd = intent.getStringExtra("cmd") ?: return
            Log.d("REPL", "Received command: $cmd")
            Toast.makeText(context, "REPL: $cmd", Toast.LENGTH_SHORT).show()
            
            evaluate(cmd, context)
        }
    }

    private fun evaluate(cmd: String, context: Context) {
        val trimmedCmd = cmd.trim()
        scope.launch {
            try {
                when {
                    trimmedCmd == "refresh" -> {
                        repository.syncAll()
                        Toast.makeText(context, "Refreshed all feeds", Toast.LENGTH_SHORT).show()
                    }
                    trimmedCmd.startsWith("subscribe") -> {
                        val url = trimmedCmd.substringAfter("subscribe").trim()
                        if (url.isNotEmpty()) {
                            repository.subscribe(url)
                                .onSuccess { Toast.makeText(context, "Subscribed!", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                    trimmedCmd.startsWith("download") -> {
                        val id = trimmedCmd.substringAfter("download").trim()
                        if (id.isNotEmpty()) {
                            repository.downloadAudio(id)
                                .onSuccess { Toast.makeText(context, "Downloaded: $it", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                    trimmedCmd == "nuke" -> {
                        Toast.makeText(context, "Nuke not implemented", Toast.LENGTH_SHORT).show()
                    }
                    trimmedCmd.startsWith("query-logs") -> {
                        val type = trimmedCmd.substringAfter("query-logs").trim()
                        val logs = repository.getLogsByType(type)
                        Log.i("REPL_RESULT", "--- Logs for $type ---")
                        logs.forEach { Log.i("REPL_RESULT", "[${it.status}] ${it.payload}") }
                        Toast.makeText(context, "Dumped ${logs.size} logs to Logcat", Toast.LENGTH_LONG).show()
                    }
                    trimmedCmd.startsWith("grep-logs") -> {
                        val q = trimmedCmd.substringAfter("grep-logs").trim()
                        val logs = repository.grepLogs(q)
                        Log.i("REPL_RESULT", "--- Grep for '$q' ---")
                        logs.forEach { Log.i("REPL_RESULT", "[${it.type}] ${it.payload}") }
                        Toast.makeText(context, "Found ${logs.size} matches", Toast.LENGTH_LONG).show()
                    }
                    trimmedCmd.startsWith("assert-fact") -> {
                        val payload = trimmedCmd.substringAfter("assert-fact").trim()
                        val parts = payload.split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val entityId = parts[0]
                            val attr = parts[1]
                            val value = parts.drop(2).joinToString(" ")
                            repository.assertFact(entityId, attr, value)
                            Toast.makeText(context, "Fact asserted: $attr", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.w("REPL", "Assert-fact usage: assert-fact <id> <attr> <val>")
                        }
                    }
                    trimmedCmd == "inspect-state" -> {
                        val facts = repository.getAllFacts()
                        Log.i("REPL_RESULT", "--- Current Facts (Information Model) ---")
                        facts.forEach { Log.i("REPL_RESULT", "[:${it.attribute} ${it.entityId}] -> \"${it.value}\"") }
                        Toast.makeText(context, "Dumped Facts to Logcat", Toast.LENGTH_SHORT).show()
                    }
                    trimmedCmd.startsWith("exec-sql") -> {
                        val sql = trimmedCmd.substringAfter("exec-sql").trim()
                        if (sql.isNotEmpty()) {
                             try {
                                 val results = repository.rawQuery(sql)
                                 Log.i("REPL_RESULT", "--- SQL Result for: $sql ---")
                                 results.forEach { Log.i("REPL_RESULT", it.toString()) }
                                 Toast.makeText(context, "SQL Executed. Check Logcat.", Toast.LENGTH_LONG).show()
                             } catch (e: Exception) {
                                 Log.e("REPL", "SQL Failed", e)
                                 Toast.makeText(context, "SQL Error: ${e.message}", Toast.LENGTH_LONG).show()
                             }
                        }
                    }
                    trimmedCmd.startsWith("play-id") -> {
                        val id = trimmedCmd.substringAfter("play-id").trim()
                        scope.launch(Dispatchers.IO) {
                            val p = repository.getPodcastById(id)
                            if (p != null) {
                                scope.launch(Dispatchers.Main) {
                                    audioSystem.player?.let { player ->
                                        val mediaItem = androidx.media3.common.MediaItem.fromUri(p.audioUrl)
                                        player.setMediaItem(mediaItem)
                                        player.prepare()
                                        player.play()
                                        Toast.makeText(context, "Playing: ${p.title}", Toast.LENGTH_SHORT).show()
                                    } ?: Toast.makeText(context, "Player not ready", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.e("REPL", "Podcast not found: $id")
                            }
                        }
                    }
                    trimmedCmd == "pause" -> {
                        audioSystem.player?.pause()
                        Toast.makeText(context, "Paused", Toast.LENGTH_SHORT).show()
                    }
                    trimmedCmd == "toggle" -> {
                        audioSystem.player?.let { 
                            if (it.isPlaying) it.pause() else it.play()
                            Toast.makeText(context, "Toggle: ${it.isPlaying}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    trimmedCmd == "diagnose" -> {
                        val runtime = Runtime.getRuntime()
                        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                        val maxMem = runtime.maxMemory() / 1024 / 1024
                        val heapC = android.os.Debug.getNativeHeapAllocatedSize() / 1024 / 1024
                        val msg = "Mem: $usedMem MB / $maxMem MB | Native: $heapC MB | Threads: ${Thread.activeCount()}"
                        Log.i("REPL_DIAGNOSE", msg)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    else -> Log.w("REPL", "Unknown command: $trimmedCmd")
                }
            } catch (e: Exception) {
                Log.e("REPL", "Eval failed", e)
            }
        }
    }
}
