package com.rumoagente.data.api

import com.rumoagente.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // ── Auth ────────────────────────────────────────────────────────────────

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: SignUpRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun signIn(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("grant_type") grantType: String = "password",
        @Body body: SignInRequest
    ): Response<AuthTokenResponse>

    @GET("auth/v1/user")
    suspend fun getUser(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Authorization") authorization: String
    ): Response<AuthUser>

    @POST("auth/v1/recover")
    suspend fun recover(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: RecoverRequest
    ): Response<Unit>

    // ── REST ────────────────────────────────────────────────────────────────

    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("id") idFilter: String? = null
    ): Response<List<UserProfile>>

    @GET("rest/v1/cloud_apps")
    suspend fun getCloudApps(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*"
    ): Response<List<CloudApp>>

    @GET("rest/v1/agent_tasks")
    suspend fun getAgentTasks(
        @Header("apikey") apiKey: String = Config.SUPABASE_ANON_KEY,
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("user_id") userIdFilter: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<AgentTask>>
}
