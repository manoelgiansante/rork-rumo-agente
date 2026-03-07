package com.rumoagente.data.api

import com.rumoagente.data.models.AgentCommand
import com.rumoagente.data.models.AgentResult
import com.rumoagente.data.models.AgentStatusResponse
import com.rumoagente.data.models.DesktopStatusResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface AgentApi {

    @GET("/status")
    suspend fun getStatus(): Response<AgentStatusResponse>

    @GET("/desktop/status")
    suspend fun getDesktopStatus(
        @Header("Authorization") authorization: String
    ): Response<DesktopStatusResponse>

    @GET("/screenshot")
    suspend fun getScreenshot(
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @POST("/execute")
    suspend fun execute(
        @Header("Authorization") authorization: String,
        @Body command: AgentCommand
    ): Response<AgentResult>

    @POST("/start-desktop")
    suspend fun startDesktop(
        @Header("Authorization") authorization: String
    ): Response<AgentResult>

    @POST("/stop-desktop")
    suspend fun stopDesktop(
        @Header("Authorization") authorization: String
    ): Response<AgentResult>
}
