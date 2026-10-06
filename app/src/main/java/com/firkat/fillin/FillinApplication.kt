package com.firkat.fillin

import android.app.Application
import com.firkat.fillin.core.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FillinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FillinApplication)
            modules(appModule)
        }
    }
}
