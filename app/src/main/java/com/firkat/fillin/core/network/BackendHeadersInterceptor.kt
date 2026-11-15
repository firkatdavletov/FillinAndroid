package com.firkat.fillin.core.network

import okhttp3.Interceptor
import okhttp3.Response

internal class BackendHeadersInterceptor(private val installationId: () -> String, private val appVersion: String) :
    Interceptor {
    override fun intercept(
        chain: Interceptor.Chain
    ): Response {
        val request = chain.request().newBuilder()
            .header("X-Device-Id", installationId())
            .header("X-App-Version", appVersion)
            .build()
        return chain.proceed(request)
    }
}
