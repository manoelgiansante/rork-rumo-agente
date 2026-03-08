package com.rumoagente.data.api

import com.rumoagente.data.models.ChatRequest
import com.rumoagente.data.models.ChatResponse
import com.rumoagente.data.models.CheckoutResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Chat & checkout API — matches iOS ClaudeService.swift and SupabaseService.swift.
 * Base URL: Config.AGENT_URL (VPS backend at https://vps.agrorumo.com)
 *
 * iOS sends: { message, appContext, history: [{role, content}] }
 * iOS reads: response?.message ?? fallback
 */
interface ChatApi {

    @POST("/chat")
    suspend fun chat(
        @Body body: ChatRequest
    ): Response<ChatResponse>

    @POST("/api/create-checkout")
    suspend fun createCheckout(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<CheckoutResponse>

    @POST("/api/buy-credits")
    suspend fun buyCredits(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<CheckoutResponse>
}
