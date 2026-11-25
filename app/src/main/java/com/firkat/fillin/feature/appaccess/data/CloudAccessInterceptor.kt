package com.firkat.fillin.feature.appaccess.data

import com.firkat.fillin.feature.appaccess.domain.AppAccess
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response

internal class DeviceAccessDeniedIOException : IOException("Cloud access disabled")

internal class CloudAccessInterceptor(private val access: () -> AppAccess) : Interceptor {
    override fun intercept(
        chain: Interceptor.Chain
    ): Response {
        if (!access().cloudAllowed) throw DeviceAccessDeniedIOException()
        val response = chain.proceed(chain.request())
        val denied = response.code == 403 &&
            runCatching {
                Json.parseToJsonElement(response.peekBody(8192).string()).jsonObject["code"]?.jsonPrimitive?.content ==
                    "device_access_denied"
            }.getOrDefault(false)
        if (denied || !access().cloudAllowed) {
            response.close()
            throw DeviceAccessDeniedIOException()
        }
        return response
    }
}
