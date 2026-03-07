package com.rumoagente.data.api

import com.rumoagente.data.models.AgentCommand
import com.rumoagente.data.models.AgentResult
import com.rumoagente.data.models.AgentStatusResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface AgentApi {

    @GET("/status")
    suspend fun getStatus(): Response<AgentStatusResponse>

    @GET("/screenshot")
    suspend fun getScreenshot(): Response<ResponseBody>

    @POST("/execute")
    suspend fun execute(
        @Body command: AgentCommand
    ): Response<AgentResult>

    @POST("/start-desktop")
    suspend fun startDesktop(): Response<AgentResult>
}
