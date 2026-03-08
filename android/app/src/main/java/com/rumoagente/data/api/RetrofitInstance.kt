package com.rumoagente.data.api

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitInstance {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // ── Token holder (set by AuthRepository) ────────────────────────────────

    @Volatile
    var authToken: String? = null

    // ── Gson with proper config ─────────────────────────────────────────────

    private val gson by lazy {
        GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .setLenient()
            .create()
    }

    // ── Interceptors ────────────────────────────────────────────────────────

    /**
     * Adds Supabase apikey header and Bearer auth token to every request.
     * Matches iOS SupabaseService.swift header setup.
     */
    private fun supabaseHeaderInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")

        authToken?.let { token ->
            if (original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        chain.proceed(builder.build())
    }

    /**
     * Adds Bearer auth token to agent/VPS requests.
     * Matches iOS ClaudeService.swift and AgentService.swift header setup.
     */
    private fun agentAuthInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Content-Type", "application/json")

        authToken?.let { token ->
            if (original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        chain.proceed(builder.build())
    }

    // ── OkHttp clients ──────────────────────────────────────────────────────

    private val supabaseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(supabaseHeaderInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val agentClient: OkHttpClient by lazy {
        // Agent runs on a VPS with a self-signed cert — trust it for now
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)  // Match iOS 120s timeout for /execute
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(agentAuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // ── Retrofit instances ──────────────────────────────────────────────────

    val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.SUPABASE_URL.trimEnd('/') + "/")
            .client(supabaseClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SupabaseApi::class.java)
    }

    val agentApi: AgentApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.AGENT_URL.trimEnd('/') + "/")
            .client(agentClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AgentApi::class.java)
    }

    /**
     * Chat API uses the same VPS backend as AgentApi.
     * iOS ClaudeService sends to Config.EXPO_PUBLIC_AGENT_BACKEND_URL/chat
     * which maps to the same VPS (Config.AGENT_URL).
     */
    val chatApi: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(Config.AGENT_URL.trimEnd('/') + "/")
            .client(agentClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ChatApi::class.java)
    }
}
