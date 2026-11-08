package com.firkat.fillin.feature.profile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import retrofit2.Response
import retrofit2.http.*

@Serializable enum class DeletionStatus { PENDING, COMPLETED }
@Serializable data class DeletionStatusDto(val status: DeletionStatus)
interface ProfileDeletionApi {
    @DELETE("api/v4/profile") suspend fun delete(@Header("Authorization") authorization: String?, @Header("X-Deletion-Token") secret: String): Response<DeletionStatusDto>
    @GET("api/v4/profile/deletion") suspend fun status(@Header("X-Deletion-Token") secret: String): Response<DeletionStatusDto>
}
class DeletionFailure(val code: String) : Exception()
internal fun Response<DeletionStatusDto>.result(): DeletionStatus {
    if (isSuccessful) return body()?.status ?: throw DeletionFailure("invalid_response")
    val code = errorBody()?.use { body -> runCatching { Json.parseToJsonElement(body.string()).jsonObject["code"]?.jsonPrimitive?.content }.getOrNull() }
    throw DeletionFailure(code ?: when(code()) { 401 -> "unauthorized"; 503 -> "deletion_unavailable"; else -> "server_error" })
}
