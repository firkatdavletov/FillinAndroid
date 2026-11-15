package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.ServerUser
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class ServerUserDto(
    val id: String,
    val firebaseUid: String,
    val displayName: String? = null,
    val email: String? = null
) {
    fun toDomain() = ServerUser(id, firebaseUid, displayName, email)
}

interface FirebaseAuthApi {
    @POST("api/v4/auth/firebase")
    suspend fun authenticate(
        @Header("Authorization") authorization: String
    ): Response<ServerUserDto>
}
