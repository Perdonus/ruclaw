package com.perdonus.ruclaw.android

import android.content.Context
import com.perdonus.ruclaw.android.data.local.LocalStateRepository
import com.perdonus.ruclaw.android.data.remote.ruclaw.RuClawLauncherClient
import java.time.Duration
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val sharedOkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .pingInterval(Duration.ofSeconds(12))
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    val localStateRepository by lazy { LocalStateRepository(appContext) }

    fun newLauncherClient(): RuClawLauncherClient = RuClawLauncherClient(sharedOkHttpClient)
}
