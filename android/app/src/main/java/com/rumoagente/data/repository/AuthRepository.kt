package com.rumoagente.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Auth repository matching iOS SupabaseService.swift behavior exactly.
 *
 * Key parity points:
 * - signUp sends {email, password, data: {display_name}} to auth/v1/signup
 * - signIn sends {email, password} to auth/v1/token?grant_type=password
 * - loadUserProfile fetches from auth/v1/user, then fetchProfile from profiles table
 * - fetchProfile queries profiles?id=eq.{userId} (NOT user_id)
 * - fetchTransactions queries credit_transactions table (NOT transactions)
 * - checkSession tries token, if 401 tries refresh, if both fail keeps session
 * - signOut clears token/user/isAuthenticated
 * - deleteAccount calls Vercel endpoint then signOut
 */
class AuthRepository(private val context: Context) {

    companion object {
        private const val TAG = "AuthRepository"
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }

    private val api = RetrofitInstance.supabaseApi

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // ── Sign Up (matches iOS SupabaseService.signUp) ─────────────────────────

    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthTokenResponse> {
        return try {
            val body = SignUpRequest(
                email = email,
                password = password,
                data = mapOf("display_name" to displayName)
            )
            val response = api.signUp(body = body)
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                loadUserProfile()
                Result.success(tokenResponse)
            } else {
                val errorBody = parseSupabaseError(response.errorBody()?.string())
                    ?: "Erro ao criar conta"
                Result.failure(ServiceError.AuthError(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Sign In (matches iOS SupabaseService.signIn) ─────────────────────────

    suspend fun signIn(email: String, password: String): Result<AuthTokenResponse> {
        return try {
            val response = api.signIn(body = SignInRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                loadUserProfile()
                Result.success(tokenResponse)
            } else {
                val errorBody = parseSupabaseError(response.errorBody()?.string())
                    ?: "Email ou senha incorretos"
                Result.failure(ServiceError.AuthError(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signIn failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Sign In with Google ID Token (matches iOS signInWithAppleToken pattern) ──

    suspend fun signInWithIdToken(idToken: String, displayName: String? = null): Result<AuthTokenResponse> {
        return try {
            val response = api.signInWithIdToken(body = IdTokenRequest(idToken = idToken))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                // Update display name if provided (matches iOS fullName logic)
                if (!displayName.isNullOrBlank()) {
                    try {
                        updateDisplayName(displayName)
                    } catch (_: Exception) {
                        // Non-fatal, continue
                    }
                }
                loadUserProfile()
                Result.success(tokenResponse)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                Result.failure(ServiceError.AuthError(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "signInWithIdToken failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Handle OAuth Callback (matches iOS handleOAuthCallback) ──────────────

    suspend fun handleOAuthCallback(accessToken: String, refreshToken: String?): Result<Unit> {
        return try {
            _authToken.value = accessToken
            RetrofitInstance.authToken = accessToken

            context.dataStore.edit { prefs ->
                prefs[KEY_ACCESS_TOKEN] = accessToken
                refreshToken?.let { prefs[KEY_REFRESH_TOKEN] = it }
            }

            loadUserProfile()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "handleOAuthCallback failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Exchange Auth Code (matches iOS exchangeCodeForSession) ──────────────

    suspend fun exchangeCodeForSession(code: String): Result<Unit> {
        return try {
            val response = api.exchangeCode(body = mapOf("auth_code" to code))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                loadUserProfile()
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                Result.failure(ServiceError.AuthError(errorBody))
            }
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCodeForSession failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Sign Out (matches iOS SupabaseService.signOut) ───────────────────────

    suspend fun signOut() {
        _authToken.value = null
        _currentUser.value = null
        _isAuthenticated.value = false
        RetrofitInstance.authToken = null
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
        }
    }

    // ── Password Recovery (matches iOS SupabaseService.resetPassword) ────────

    suspend fun recoverPassword(email: String): Result<Unit> {
        return try {
            val response = api.recover(body = RecoverRequest(email))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(ServiceError.NetworkError())
            }
        } catch (e: Exception) {
            Log.e(TAG, "recoverPassword failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Delete Account (matches iOS SupabaseService.deleteAccount) ───────────

    suspend fun deleteAccount(): Result<Unit> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Usuário não autenticado"))

        return try {
            val response = api.deleteAccount(authorization = "Bearer $token")
            if (response.isSuccessful) {
                signOut()
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Erro desconhecido"
                Result.failure(ServiceError.AuthError("Falha ao excluir conta: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Check / Restore Session (matches iOS SupabaseService.checkSession) ───

    /**
     * iOS behavior:
     * 1. If no token, return
     * 2. Try loadUserProfile
     * 3. If not authenticated after load, try refreshSession then loadUserProfile again
     *
     * Key difference from previous Android: iOS does NOT sign out on failure,
     * it just leaves the session as-is.
     */
    suspend fun checkSession(): Boolean {
        val savedToken = context.dataStore.data
            .map { prefs -> prefs[KEY_ACCESS_TOKEN] }
            .firstOrNull()

        if (savedToken.isNullOrBlank()) return false

        _authToken.value = savedToken
        RetrofitInstance.authToken = savedToken

        // Try loading the user with the current token
        val userLoaded = loadUserProfile()
        if (userLoaded) return true

        // Token may have expired — try refreshing (matches iOS checkSession)
        Log.d(TAG, "Token expired, attempting refresh...")
        val refreshed = refreshSession()
        if (refreshed) {
            return loadUserProfile()
        }

        // iOS does not sign out on failure — keep existing session data
        return false
    }

    // ── Refresh Session (matches iOS SupabaseService.refreshSession) ─────────

    suspend fun refreshSession(): Boolean {
        val savedRefreshToken = context.dataStore.data
            .map { prefs -> prefs[KEY_REFRESH_TOKEN] }
            .firstOrNull()

        if (savedRefreshToken.isNullOrBlank()) return false

        return try {
            val response = api.refreshToken(
                body = RefreshTokenRequest(refreshToken = savedRefreshToken)
            )
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                Log.d(TAG, "Session refreshed successfully")
                true
            } else {
                Log.w(TAG, "Refresh failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshSession failed", e)
            false
        }
    }

    // ── Update User Metadata (matches iOS updateUserMetadata) ────────────────

    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Não autenticado"))

        return try {
            val body = UpdateUserMetadataRequest(
                data = mapOf("display_name" to displayName)
            )
            val response = api.updateUserMetadata(
                authorization = "Bearer $token",
                body = body
            )
            if (response.isSuccessful) {
                loadUserProfile()
                Result.success(Unit)
            } else {
                Result.failure(ServiceError.InvalidResponse())
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateDisplayName failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Fetch Profile from REST (matches iOS fetchProfile) ───────────────────
    // iOS queries: profiles?id=eq.{userId}&select=*&limit=1

    suspend fun fetchProfile(): Result<UserProfile> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Não autenticado"))
        val userId = _currentUser.value?.id
            ?: return Result.failure(ServiceError.AuthError("Usuário não encontrado"))

        return try {
            val response = api.getProfile(
                authorization = "Bearer $token",
                idFilter = "eq.$userId"
            )
            if (response.isSuccessful && response.body() != null) {
                val profiles = response.body()!!
                if (profiles.isNotEmpty()) {
                    _currentUser.value = profiles.first()
                    Result.success(profiles.first())
                } else {
                    Result.failure(ServiceError.InvalidResponse())
                }
            } else {
                Result.failure(ServiceError.InvalidResponse())
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchProfile failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Fetch Tasks (matches iOS fetchTasks) ─────────────────────────────────

    suspend fun fetchTasks(): Result<List<AgentTask>> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Não autenticado"))
        val userId = _currentUser.value?.id
            ?: return Result.failure(ServiceError.AuthError("Usuário não encontrado"))

        return try {
            val response = api.getAgentTasks(
                authorization = "Bearer $token",
                userIdFilter = "eq.$userId"
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(ServiceError.InvalidResponse())
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTasks failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Fetch Transactions (matches iOS fetchTransactions) ───────────────────
    // iOS queries: credit_transactions?user_id=eq.{userId}&select=*&order=created_at.desc&limit=50

    suspend fun fetchTransactions(): Result<List<Transaction>> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Não autenticado"))
        val userId = _currentUser.value?.id
            ?: return Result.failure(ServiceError.AuthError("Usuário não encontrado"))

        return try {
            val response = api.getTransactions(
                authorization = "Bearer $token",
                userIdFilter = "eq.$userId"
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(ServiceError.InvalidResponse())
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchTransactions failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Fetch Cloud Apps (matches iOS fetchApps) ─────────────────────────────

    suspend fun fetchApps(): Result<List<CloudApp>> {
        val token = _authToken.value
            ?: return Result.failure(ServiceError.AuthError("Não autenticado"))

        return try {
            val response = api.getCloudApps(authorization = "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(ServiceError.InvalidResponse())
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchApps failed", e)
            Result.failure(ServiceError.NetworkError())
        }
    }

    // ── Build Google OAuth URL (matches iOS signInWithGoogleOAuth) ────────────

    fun getGoogleOAuthUrl(): String {
        val redirectURL = "com.rumoagente://login-callback"
        return "${com.rumoagente.data.api.Config.SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=$redirectURL"
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Loads user info from Supabase auth, then fetches the full profile.
     * Matches iOS loadUserProfile exactly:
     * 1. GET auth/v1/user
     * 2. Extract id, email, display_name from user_metadata
     * 3. Set initial profile with plan=free, credits=10
     * 4. Call fetchProfile to get real plan/credits from profiles table
     */
    private suspend fun loadUserProfile(): Boolean {
        val token = _authToken.value ?: return false

        return try {
            val response = api.getUser(authorization = "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val authUser = response.body()!!
                val metadata = authUser.userMetadata
                val displayName = (metadata?.get("display_name") as? String)
                    ?: authUser.email?.substringBefore("@")
                    ?: ""

                // Set initial profile from auth data (matches iOS exactly)
                _currentUser.value = UserProfile(
                    id = authUser.id,
                    email = authUser.email ?: "",
                    displayName = displayName,
                    avatarUrl = metadata?.get("avatar_url") as? String,
                    plan = SubscriptionPlan.FREE,
                    credits = 10,
                    createdAt = authUser.createdAt
                )
                _isAuthenticated.value = true

                // Fetch full profile with plan/credits from profiles table
                fetchProfile()

                true
            } else if (response.code() == 401) {
                // iOS returns false on 401, does NOT sign out
                false
            } else {
                Log.w(TAG, "loadUserProfile: auth/v1/user returned ${response.code()}")
                false
            }
        } catch (e: Exception) {
            // iOS: Network error — don't sign out, keep existing session
            Log.e(TAG, "loadUserProfile failed", e)
            false
        }
    }

    private suspend fun saveSession(tokenResponse: AuthTokenResponse) {
        _authToken.value = tokenResponse.accessToken
        RetrofitInstance.authToken = tokenResponse.accessToken

        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = tokenResponse.accessToken
            tokenResponse.refreshToken?.let { prefs[KEY_REFRESH_TOKEN] = it }
            tokenResponse.user?.let { user ->
                prefs[KEY_USER_ID] = user.id
                user.email?.let { prefs[KEY_USER_EMAIL] = it }
            }
        }
    }

    /**
     * Parses Supabase error responses matching iOS parseSupabaseError.
     * Tries: msg -> error_description -> message
     */
    private fun parseSupabaseError(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = org.json.JSONObject(errorBody)
            json.optString("msg", null)
                ?: json.optString("error_description", null)
                ?: json.optString("message", null)
        } catch (_: Exception) {
            null
        }
    }
}
