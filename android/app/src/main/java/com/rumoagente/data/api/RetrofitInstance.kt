package com.rumoagente.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // ── Token holder (set by AuthRepository) ────────────────────────────────

    @Volatile
    var authToken: String? = null

    // ── OkHttp clients ──────────────────────────────────────────────────────

    private val supabaseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(supabaseHeaderInterceptor())
            .build()
    }

    private val agentClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val vercelClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authBearerInterceptor())
            .build()
    }

    // ── Interceptors ────────────────────────────────────────────────────────

    private fun supabaseHeaderInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("apikey", Config.SUPABASE_ANON_KEY)

        authToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        chain.proceed(builder.build())
    }

    private fun authBearerInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        authToken?.let { token ->
            if (original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        chain.proceed(builder.build())
    }

    // ── Retrofit instances ──────────────────────────────────────────────────

    val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.SUPABASE_URL + "/")
            .client(supabaseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    val agentApi: AgentApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.AGENT_URL + "/")
            .client(agentClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgentApi::class.java)
    }

    val chatApi: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.API_URL.removeSuffix("/api") + "/")
            .client(vercelClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }
}
