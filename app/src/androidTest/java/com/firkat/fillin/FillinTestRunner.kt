package com.firkat.fillin

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Instrumentation must never start a worker against a configured production API. */
class FillinTestApplication : FillinApplication() {
    override val automaticSyncEnabled: Boolean = false
}

class FillinTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context
    ): Application = super.newApplication(cl, FillinTestApplication::class.java.name, context)
}
