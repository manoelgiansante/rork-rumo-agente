package com.rumoagente.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rumoagente.data.api.Config
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthRepository(private val context: Context) {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    }

    private val api = RetrofitInstance.supabaseApi

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    // ── Public methods ──────────────────────────────────────────────────────

    suspend fun signUp(email: String, password: String): Result<AuthTokenResponse> {
        return try {
            val response = api.signUp(body = SignUpRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Sign up failed: ${response.code()} ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthTokenResponse> {
        return try {
            val response = api.signIn(body = SignInRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveSession(tokenResponse)
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Sign in failed: ${response.code()} ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        _authToken.value = null
        _currentUser.value = null
        RetrofitInstance.authToken = null
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
        }
    }

    suspend fun getUser(): Result<AuthUser> {
        val token = _authToken.value
            ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getUser(authorization = "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Get user failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recoverPassword(email: String): Result<Unit> {
        return try {
            val response = api.recover(body = RecoverRequest(email))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Recovery failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Attempts to restore a previous session from DataStore.
     * Returns true if a valid session was found and the user could be fetched.
     */
    suspend fun checkSession(): Boolean {
        val savedToken = context.dataStore.data
            .map { prefs -> prefs[KEY_ACCESS_TOKEN] }
            .firstOrNull()

        if (savedToken.isNullOrBlank()) return false

        _authToken.value = savedToken
        RetrofitInstance.authToken = savedToken

        return getUser().isSuccess
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private suspend fun saveSession(tokenResponse: AuthTokenResponse) {
        _authToken.value = tokenResponse.accessToken
        _currentUser.value = tokenResponse.user
        RetrofitInstance.authToken = tokenResponse.accessToken

        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = tokenResponse.accessToken
            prefs[KEY_REFRESH_TOKEN] = tokenResponse.refreshToken
            tokenResponse.user?.let { user ->
                prefs[KEY_USER_ID] = user.id
                user.email?.let { prefs[KEY_USER_EMAIL] = it }
            }
        }
    }
}
