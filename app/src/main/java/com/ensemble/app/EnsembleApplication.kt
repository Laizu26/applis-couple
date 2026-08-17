package com.ensemble.app

import android.app.Application
import com.ensemble.app.data.AppContainer

class EnsembleApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
