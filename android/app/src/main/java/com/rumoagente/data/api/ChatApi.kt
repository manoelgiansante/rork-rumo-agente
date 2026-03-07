package com.rumoagente.data.api

import com.rumoagente.data.models.ChatMessage
import com.rumoagente.data.models.ChatResponse
import com.rumoagente.data.models.CheckoutResponse
import retrofit2.Response
import retrofit2.http.*

interface ChatApi {

    @POST("/api/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body messages: List<ChatMessage>
    ): Response<ChatResponse>

    @POST("/api/create-checkout")
    suspend fun createCheckout(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<CheckoutResponse>

    @POST("/api/buy-credits")
    suspend fun buyCredits(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<CheckoutResponse>
}
