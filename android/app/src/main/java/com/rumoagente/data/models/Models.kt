package com.rumoagente.data.models

import com.google.gson.annotations.SerializedName

// ── User Profile ────────────────────────────────────────────────────────────

data class UserProfile(
    val id: String,
    val email: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val plan: String = SubscriptionPlan.FREE.name,
    val credits: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Chat ────────────────────────────────────────────────────────────────────

enum class MessageRole {
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("system") SYSTEM
}

data class ChatMessage(
    val id: String? = null,
    val role: MessageRole,
    val content: String,
    @SerializedName("is_confirmation") val isConfirmation: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Cloud Apps ──────────────────────────────────────────────────────────────

enum class AppStatus {
    @SerializedName("installed") INSTALLED,
    @SerializedName("not_installed") NOT_INSTALLED,
    @SerializedName("running") RUNNING,
    @SerializedName("installing") INSTALLING
}

enum class AppCategory {
    @SerializedName("agro") AGRO,
    @SerializedName("finance") FINANCE,
    @SerializedName("productivity") PRODUCTIVITY,
    @SerializedName("communication") COMMUNICATION,
    @SerializedName("other") OTHER
}

data class CloudApp(
    val id: String,
    val name: String,
    @SerializedName("icon_name") val iconName: String? = null,
    val status: AppStatus = AppStatus.NOT_INSTALLED,
    val category: AppCategory = AppCategory.OTHER,
    @SerializedName("is_selected") val isSelected: Boolean = false
)

// ── Agent Tasks ─────────────────────────────────────────────────────────────

enum class TaskStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("running") RUNNING,
    @SerializedName("completed") COMPLETED,
    @SerializedName("failed") FAILED,
    @SerializedName("waiting") WAITING
}

data class AgentTask(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val status: TaskStatus = TaskStatus.PENDING,
    @SerializedName("app_name") val appName: String? = null,
    @SerializedName("credits_used") val creditsUsed: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null
)

// ── Agent Command / Result ──────────────────────────────────────────────────

data class AgentCommand(
    val action: String,
    @SerializedName("app_context") val appContext: String? = null,
    val parameters: Map<String, Any>? = null
)

data class AgentResult(
    val success: Boolean,
    val message: String? = null,
    val screenshot: String? = null,
    @SerializedName("task_id") val taskId: String? = null
)

// ── Agent Status ────────────────────────────────────────────────────────────

data class AgentStatusResponse(
    val status: String,
    val desktop: String? = null,
    val uptime: Long? = null,
    val timestamp: String? = null
)

// ── Subscription Plans ──────────────────────────────────────────────────────

enum class SubscriptionPlan(
    val displayName: String,
    val credits: Int,
    val price: Double
) {
    FREE("Gratis", 10, 0.0),
    STARTER("Starter", 100, 29.90),
    PRO("Pro", 500, 79.90),
    ENTERPRISE("Enterprise", 2000, 199.90)
}

// ── API response wrappers ───────────────────────────────────────────────────

data class ChatResponse(
    val message: String
)

data class CheckoutResponse(
    val url: String,
    @SerializedName("session_id") val sessionId: String? = null
)

data class SignUpRequest(
    val email: String,
    val password: String
)

data class SignInRequest(
    val email: String,
    val password: String,
    @SerializedName("grant_type") val grantType: String = "password"
)

data class RecoverRequest(
    val email: String
)

data class AuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: AuthUser? = null
)

data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)
