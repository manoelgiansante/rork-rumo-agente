package com.rumoagente.data.models

import com.google.gson.annotations.SerializedName

// ── User Profile ────────────────────────────────────────────────────────────

data class UserProfile(
    val id: String,
    val email: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val credits: Int = 10,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Subscription Plans (matches iOS exactly) ────────────────────────────────

enum class SubscriptionPlan(
    val displayName: String,
    val includedCredits: Int,
    val monthlyPrice: Double,
    val planDescription: String
) {
    @SerializedName("free")
    FREE(
        displayName = "Gratuito",
        includedCredits = 10,
        monthlyPrice = 0.0,
        planDescription = "Teste o agente com 10 créditos"
    ),

    @SerializedName("starter")
    STARTER(
        displayName = "Starter",
        includedCredits = 100,
        monthlyPrice = 49.90,
        planDescription = "Ideal para pequenos produtores"
    ),

    @SerializedName("pro")
    PRO(
        displayName = "Pro",
        includedCredits = 500,
        monthlyPrice = 149.90,
        planDescription = "Para fazendas de médio porte"
    ),

    @SerializedName("enterprise")
    ENTERPRISE(
        displayName = "Enterprise",
        includedCredits = 2000,
        monthlyPrice = 499.90,
        planDescription = "Operações de grande escala"
    )
}

// ── Chat (matches iOS ChatMessage.swift) ────────────────────────────────────

enum class MessageRole {
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("system") SYSTEM
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("conversation_id") val conversationId: String = "",
    val role: MessageRole,
    val content: String,
    @SerializedName("screenshot_url") val screenshotUrl: String? = null,
    @SerializedName("is_confirmation") val isConfirmation: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Cloud Apps (matches iOS CloudApp.swift) ─────────────────────────────────

enum class AppStatus {
    @SerializedName("installed") INSTALLED,
    @SerializedName("installing") INSTALLING,
    @SerializedName("not_installed") NOT_INSTALLED,
    @SerializedName("running") RUNNING;

    val displayName: String
        get() = when (this) {
            INSTALLED -> "Instalado"
            INSTALLING -> "Instalando..."
            NOT_INSTALLED -> "Não instalado"
            RUNNING -> "Em uso"
        }
}

enum class AppCategory {
    @SerializedName("Agronegócio") AGRO,
    @SerializedName("Financeiro") FINANCE,
    @SerializedName("Produtividade") PRODUCTIVITY,
    @SerializedName("Comunicação") COMMUNICATION,
    @SerializedName("Outros") OTHER
}

data class CloudApp(
    val id: String,
    val name: String,
    @SerializedName("icon_name") val iconName: String = "app.fill",
    val status: AppStatus = AppStatus.INSTALLED,
    val category: AppCategory = AppCategory.OTHER,
    @SerializedName("is_selected") val isSelected: Boolean = false
)

// ── Agent Tasks (matches iOS AgentTask.swift) ───────────────────────────────

enum class TaskStatus {
    @SerializedName("pending") PENDING,
    @SerializedName("running") RUNNING,
    @SerializedName("completed") COMPLETED,
    @SerializedName("failed") FAILED,
    @SerializedName("waiting_confirmation") WAITING_CONFIRMATION;

    val displayName: String
        get() = when (this) {
            PENDING -> "Pendente"
            RUNNING -> "Executando"
            COMPLETED -> "Concluída"
            FAILED -> "Falhou"
            WAITING_CONFIRMATION -> "Aguardando"
        }

    val iconResName: String
        get() = when (this) {
            PENDING -> "ic_clock"
            RUNNING -> "ic_play_circle"
            COMPLETED -> "ic_check_circle"
            FAILED -> "ic_cancel"
            WAITING_CONFIRMATION -> "ic_help_circle"
        }
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

// ── Transactions (matches iOS Transaction.swift exactly) ────────────────────

enum class TransactionType {
    @SerializedName("purchase") PURCHASE,
    @SerializedName("usage") USAGE,
    @SerializedName("bonus") BONUS,
    @SerializedName("refund") REFUND;

    val displayName: String
        get() = when (this) {
            PURCHASE -> "Compra de créditos"
            USAGE -> "Uso de créditos"
            BONUS -> "Bônus"
            REFUND -> "Reembolso"
        }

    val iconResName: String
        get() = when (this) {
            PURCHASE -> "ic_add_circle"
            USAGE -> "ic_remove_circle"
            BONUS -> "ic_gift"
            REFUND -> "ic_undo"
        }
}

data class Transaction(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val amount: Int,
    val type: TransactionType,
    @SerializedName("description") val transactionDescription: String? = null,
    @SerializedName("stripe_payment_id") val stripePaymentId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Agent Command / Result (matches iOS AgentService.swift) ─────────────────

data class AgentCommand(
    val action: String,
    @SerializedName("app_context") val appContext: String? = null,
    val parameters: Map<String, String>? = null
)

data class AgentResult(
    val success: Boolean,
    val message: String? = null,
    @SerializedName("screenshot_url") val screenshotUrl: String? = null,
    @SerializedName("task_id") val taskId: String? = null
)

// ── Agent Status ────────────────────────────────────────────────────────────

data class AgentStatusResponse(
    val status: String,
    val desktop: String? = null,
    val uptime: Long? = null,
    val timestamp: String? = null,
    val activeDesktops: Int? = null
)

data class DesktopStatusResponse(
    val desktop: Boolean = false,
    val status: String? = null,
    val noVncPort: Int? = null,
    val lastActivity: Long? = null
)

// ── Chat API request/response (matches iOS ClaudeService.swift) ─────────────

data class ChatRequest(
    val message: String,
    val appContext: String = "",
    val history: List<Map<String, String>> = emptyList()
)

data class ChatResponse(
    val response: String? = null,
    val message: String? = null
) {
    /** Returns the response text matching iOS logic: response ?? message ?? fallback */
    val text: String
        get() = response ?: message ?: "Desculpe, não consegui processar seu comando."
}

data class CheckoutResponse(
    val url: String,
    @SerializedName("session_id") val sessionId: String? = null
)

// ── Auth request / response wrappers ────────────────────────────────────────

data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

data class SignInRequest(
    val email: String,
    val password: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RecoverRequest(
    val email: String
)

data class IdTokenRequest(
    val provider: String = "google",
    @SerializedName("id_token") val idToken: String
)

data class UpdateUserMetadataRequest(
    val data: Map<String, String>
)

data class AuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null
)

data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerializedName("user_metadata") val userMetadata: Map<String, Any>? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Error handling (matches iOS ServiceError enum) ──────────────────────────

sealed class ServiceError(message: String) : Exception(message) {
    class AuthError(msg: String) : ServiceError(msg)
    class NetworkError : ServiceError("Erro de conexão. Verifique sua internet.")
    class InvalidResponse : ServiceError("Resposta inválida do servidor.")
    class InsufficientCredits : ServiceError("Créditos insuficientes.")
    class AgentOffline : ServiceError("O agente está offline no momento.")
}

// ── Agent errors (matches iOS AgentError enum) ──────────────────────────────

sealed class AgentError(message: String) : Exception(message) {
    class NotConfigured : AgentError("O agente ainda não está configurado.")
    class ExecutionFailed(msg: String) : AgentError("Falha ao executar: $msg")
    class Timeout : AgentError("O comando demorou demais para executar.")
}
