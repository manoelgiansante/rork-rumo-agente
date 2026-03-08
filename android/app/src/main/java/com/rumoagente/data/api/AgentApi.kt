package com.rumoagente.data.api

import com.rumoagente.data.models.AgentCommand
import com.rumoagente.data.models.AgentResult
import com.rumoagente.data.models.AgentStatusResponse
import com.rumoagente.data.models.DesktopStatusResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Agent/VPS API — matches iOS AgentService.swift endpoints exactly.
 * Base URL: Config.AGENT_URL (https://vps.agrorumo.com)
 */
interface AgentApi {

    @GET("/status")
    suspend fun getStatus(): Response<AgentStatusResponse>

    @GET("/desktop/status")
    suspend fun getDesktopStatus(): Response<DesktopStatusResponse>

    @GET("/screenshot")
    suspend fun getScreenshot(): Response<ResponseBody>

    @POST("/execute")
    suspend fun execute(
        @Body command: AgentCommand
    ): Response<AgentResult>

    @POST("/start-desktop")
    suspend fun startDesktop(): Response<AgentResult>

    @POST("/stop-desktop")
    suspend fun stopDesktop(): Response<AgentResult>
}
