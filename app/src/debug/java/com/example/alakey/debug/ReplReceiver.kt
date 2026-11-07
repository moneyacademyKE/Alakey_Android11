package com.example.alakey.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.alakey.data.UniversalRepository
import com.example.alakey.system.AudioSystem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only ADB command receiver. Do not move this into the main source set. */
@AndroidEntryPoint
class ReplReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: UniversalRepository
    @Inject lateinit var audioSystem: AudioSystem

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.alakey.REPL_EVAL") return

        val cmd = intent.getStringExtra("cmd") ?: return
        Log.d("REPL", "Received command: $cmd")
        Toast.makeText(context, "REPL: $cmd", Toast.LENGTH_SHORT).show()

        // L67: onReceive returning makes the process kill-eligible while the
        // command is in flight (refresh, exec-sql, assert-fact all touch the
        // DB) — a dropped sync or half-applied fact write, silently. goAsync()
        // holds a PendingResult and finish() fires exactly when the command
        // completes (success, failure or cancellation). The scope is a
        // companion, not a per-instance field: manifest receivers get a fresh
        // instance per broadcast, so an instance scope leaked one SupervisorJob
        // per command forever.
        val pending = goAsync()
        scope.launch {
            try {
                evaluate(cmd.trim(), context)
            } catch (e: Exception) {
                Log.e("REPL", "Eval failed", e)
            }
        }.invokeOnCompletion { pending.finish() }
    }

    private suspend fun evaluate(cmd: String, context: Context) {
        when {
            cmd == "refresh" -> {
                repository.syncAll()
                Toast.makeText(context, "Refreshed all feeds", Toast.LENGTH_SHORT).show()
            }
            cmd.startsWith("subscribe") -> subscribe(cmd, context)
            cmd.startsWith("query-logs") -> queryLogs(cmd, context)
            cmd.startsWith("grep-logs") -> grepLogs(cmd, context)
            cmd.startsWith("assert-fact") -> assertFact(cmd, context)
            cmd == "inspect-state" -> inspectState(context)
            cmd.startsWith("exec-sql") -> execSql(cmd, context)
            cmd.startsWith("play-id") -> playId(cmd, context)
            cmd == "pause" -> audioSystem.player?.pause()
            cmd == "toggle" -> audioSystem.player?.let { if (it.isPlaying) it.pause() else it.play() }
            cmd == "diagnose" -> diagnose(context)
            else -> Log.w("REPL", "Unknown command: $cmd")
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private suspend fun subscribe(cmd: String, context: Context) {
        val url = cmd.substringAfter("subscribe").trim()
        if (url.isEmpty()) return

        repository.subscribe(url)
            .onSuccess { Toast.makeText(context, "Subscribed!", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private suspend fun queryLogs(cmd: String, context: Context) {
        val logs = repository.getLogsByType(cmd.substringAfter("query-logs").trim())
        logs.forEach { Log.i("REPL_RESULT", "[${it.status}] ${it.payload}") }
        Toast.makeText(context, "Dumped ${logs.size} logs to Logcat", Toast.LENGTH_LONG).show()
    }

    private suspend fun grepLogs(cmd: String, context: Context) {
        val q = cmd.substringAfter("grep-logs").trim()
        val logs = repository.grepLogs(q)
        logs.forEach { Log.i("REPL_RESULT", "[${it.type}] ${it.payload}") }
        Toast.makeText(context, "Found ${logs.size} matches", Toast.LENGTH_LONG).show()
    }

    private suspend fun assertFact(cmd: String, context: Context) {
        val parts = cmd.substringAfter("assert-fact").trim().split(Regex("\\s+"))
        if (parts.size < 3) return

        repository.assertFact(parts[0], parts[1], parts.drop(2).joinToString(" "))
        Toast.makeText(context, "Fact asserted: ${parts[1]}", Toast.LENGTH_SHORT).show()
    }

    private suspend fun inspectState(context: Context) {
        repository.getAllFacts().forEach {
            Log.i("REPL_RESULT", "[:${it.attribute} ${it.entityId}] -> \"${it.value}\"")
        }
        Toast.makeText(context, "Dumped Facts to Logcat", Toast.LENGTH_SHORT).show()
    }

    private suspend fun execSql(cmd: String, context: Context) {
        val sql = cmd.substringAfter("exec-sql").trim()
        if (sql.isEmpty()) return

        val results = repository.rawQuery(sql)
        results.forEach { Log.i("REPL_RESULT", it.toString()) }
        Toast.makeText(context, "SQL Executed. Check Logcat.", Toast.LENGTH_LONG).show()
    }

    private suspend fun playId(cmd: String, context: Context) {
        val id = cmd.substringAfter("play-id").trim()
        val podcast = repository.getPodcastById(id)
        if (podcast == null) {
            Log.e("REPL", "Podcast not found: $id")
            return
        }
        audioSystem.player?.let { player ->
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(podcast.audioUrl))
            player.prepare()
            player.play()
            Toast.makeText(context, "Playing: ${podcast.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun diagnose(context: Context) {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMem = runtime.maxMemory() / 1024 / 1024
        val nativeMem = android.os.Debug.getNativeHeapAllocatedSize() / 1024 / 1024
        val msg = "Mem: $usedMem MB / $maxMem MB | Native: $nativeMem MB | Threads: ${Thread.activeCount()}"
        Log.i("REPL_DIAGNOSE", msg)
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}
