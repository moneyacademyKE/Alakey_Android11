package com.example.alakey

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.alakey.system.DatabaseSystem
import com.example.alakey.system.NetworkSystem
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AlakeyApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var databaseSystem: DatabaseSystem

    @Inject
    lateinit var networkSystem: NetworkSystem

    override fun onCreate() {
        super.onCreate()
        databaseSystem.start()
        networkSystem.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
