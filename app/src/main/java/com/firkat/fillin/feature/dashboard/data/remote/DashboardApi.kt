package com.firkat.fillin.feature.dashboard.data.remote

import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface DashboardApi {
    @Headers("Accept: application/json")
    @GET("api/v1/dashboard")
    suspend fun getDashboard(
        @Header("Authorization") authorization: String,
    ): DashboardDto
}
