package com.rumoagente.data.api

import com.rumoagente.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // ── Auth (matches iOS SupabaseService.swift) ─────────────────────────────

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body body: SignUpRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun signIn(
        @Query("grant_type") grantType: String = "password",
        @Body body: SignInRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body body: RefreshTokenRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun signInWithIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body body: IdTokenRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun exchangeCode(
        @Query("grant_type") grantType: String = "pkce",
        @Body body: Map<String, String>
    ): Response<AuthTokenResponse>

    @GET("auth/v1/user")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): Response<AuthUser>

    @PUT("auth/v1/user")
    suspend fun updateUserMetadata(
        @Header("Authorization") authorization: String,
        @Body body: UpdateUserMetadataRequest
    ): Response<AuthUser>

    @POST("auth/v1/recover")
    suspend fun recover(
        @Body body: RecoverRequest
    ): Response<Unit>

    // ── REST: Profiles (matches iOS: profiles?id=eq.{userId}) ────────────────

    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("Authorization") authorization: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 1
    ): Response<List<UserProfile>>

    // ── REST: Cloud Apps (matches iOS: cloud_apps?select=*&order=name.asc) ───

    @GET("rest/v1/cloud_apps")
    suspend fun getCloudApps(
        @Header("Authorization") authorization: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): Response<List<CloudApp>>

    // ── REST: Agent Tasks (matches iOS: agent_tasks?user_id=eq.{userId}) ─────

    @GET("rest/v1/agent_tasks")
    suspend fun getAgentTasks(
        @Header("Authorization") authorization: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 20
    ): Response<List<AgentTask>>

    // ── REST: Transactions (matches iOS: credit_transactions table) ──────────

    @GET("rest/v1/credit_transactions")
    suspend fun getTransactions(
        @Header("Authorization") authorization: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<Transaction>>

    // ── Account deletion (matches iOS: Vercel endpoint) ──────────────────────

    @DELETE
    suspend fun deleteAccount(
        @Url url: String = "${Config.AGENT_URL}/api/delete-account",
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
