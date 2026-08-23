package com.example.alakey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.alakey.ui.MainContent
import com.example.alakey.workers.FeedSyncWorker
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject lateinit var dbSystem: com.example.alakey.system.DatabaseSystem
    @Inject lateinit var networkSystem: com.example.alakey.system.NetworkSystem

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        dbSystem.start()
        networkSystem.start()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        FeedSyncWorker.schedule(this)
        WorkManager.getInstance(this).enqueueUniqueWork(
            "sync_immediate",
            androidx.work.ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FeedSyncWorker>().build()
        )

        setContent {
            com.example.alakey.ui.theme.AlakeyTheme(darkTheme = true, dynamicColor = false) {
                MainContent()
            }
        }
    }
}
