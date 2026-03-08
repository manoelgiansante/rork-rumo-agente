package com.rumoagente.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.data.models.UserProfile
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

private object SettingsKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    val APPEARANCE = stringPreferencesKey("appearance")
}

enum class AppLanguage(val label: String, val code: String) {
    PT_BR("Portugues (BR)", "pt-BR"),
    EN("English", "en")
}

enum class AppAppearance(val label: String) {
    DARK("Escuro"),
    LIGHT("Claro"),
    AUTO("Automatico")
}

@Composable
fun ProfileScreen(
    onNavigateToSubscription: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf("Carregando...") }
    var displayEmail by remember { mutableStateOf("") }
    var displayPlan by remember { mutableStateOf("Free") }
    var displayCredits by remember { mutableStateOf(0) }
    var displayInitial by remember { mutableStateOf("?") }

    // Settings state
    var language by remember { mutableStateOf(AppLanguage.PT_BR) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var appearance by remember { mutableStateOf(AppAppearance.DARK) }

    // Dialogs
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Load saved preferences
    LaunchedEffect(Unit) {
        val prefs = context.settingsDataStore.data.firstOrNull()
        if (prefs != null) {
            val langCode = prefs[SettingsKeys.LANGUAGE]
            language = AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.PT_BR
            notificationsEnabled = prefs[SettingsKeys.NOTIFICATIONS] ?: true
            val appearanceVal = prefs[SettingsKeys.APPEARANCE]
            appearance = AppAppearance.entries.find { it.name == appearanceVal } ?: AppAppearance.DARK
        }
    }

    // Load profile
    LaunchedEffect(Unit) {
        val token = RetrofitInstance.authToken ?: return@LaunchedEffect
        try {
            val response = RetrofitInstance.supabaseApi.getProfiles(
                authorization = "Bearer $token"
            )
            if (response.isSuccessful) {
                val profile = response.body()?.firstOrNull()
                if (profile != null) {
                    displayName = profile.displayName ?: profile.email.substringBefore("@")
                    displayEmail = profile.email
                    displayPlan = profile.plan.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    displayCredits = profile.credits
                    displayInitial = displayName.firstOrNull()?.uppercase() ?: "?"
                }
            }
        } catch (_: Exception) { }
    }

    fun saveLanguage(newLang: AppLanguage) {
        language = newLang
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.LANGUAGE] = newLang.code
            }
        }
    }

    fun toggleNotifications() {
        notificationsEnabled = !notificationsEnabled
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.NOTIFICATIONS] = notificationsEnabled
            }
        }
    }

    fun cycleAppearance() {
        appearance = when (appearance) {
            AppAppearance.DARK -> AppAppearance.LIGHT
            AppAppearance.LIGHT -> AppAppearance.AUTO
            AppAppearance.AUTO -> AppAppearance.DARK
        }
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.APPEARANCE] = appearance.name
            }
        }
    }

    fun handleDeleteAccount() {
        isDeleting = true
        coroutineScope.launch {
            try {
                // Clear local data
                context.settingsDataStore.edit { it.clear() }
                RetrofitInstance.authToken = null
                isDeleting = false
                showDeleteConfirmDialog = false
                onLogout()
            } catch (_: Exception) {
                isDeleting = false
            }
        }
    }

    // Delete account first dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = RumoColors.CardBg,
            titleContentColor = Color.White,
            textContentColor = RumoColors.SubtleText,
            title = { Text("Excluir conta") },
            text = {
                Text("Tem certeza que deseja excluir sua conta? Esta acao e irreversivel e todos os seus dados serao perdidos.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showDeleteConfirmDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.Red)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete account second confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            containerColor = RumoColors.CardBg,
            titleContentColor = RumoColors.Red,
            textContentColor = RumoColors.SubtleText,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = RumoColors.Red,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Confirmacao final") },
            text = {
                Text("Esta e sua ultima chance. Ao confirmar, sua conta e todos os dados associados serao permanentemente excluidos.")
            },
            confirmButton = {
                Button(
                    onClick = { handleDeleteAccount() },
                    colors = ButtonDefaults.buttonColors(containerColor = RumoColors.Red),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Excluir permanentemente", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Avatar + info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(RumoColors.Accent, RumoColors.AccentBlue)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayInitial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = displayEmail,
                color = RumoColors.SubtleText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(1.dp, RumoColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = RumoColors.Accent.copy(alpha = 0.08f)
            ),
            onClick = onNavigateToSubscription
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(RumoColors.Accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = RumoColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Plano $displayPlan",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "$displayCredits creditos restantes",
                            color = RumoColors.Accent,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = RumoColors.SubtleText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings section
        SettingsSection(title = "Configuracoes") {
            SettingsRow(
                icon = Icons.Default.Language,
                title = "Idioma",
                subtitle = language.label,
                iconColor = RumoColors.AccentBlue,
                onClick = {
                    saveLanguage(
                        if (language == AppLanguage.PT_BR) AppLanguage.EN
                        else AppLanguage.PT_BR
                    )
                }
            )
            SettingsRowWithSwitch(
                icon = Icons.Default.Notifications,
                title = "Notificacoes",
                isChecked = notificationsEnabled,
                iconColor = RumoColors.Orange,
                onToggle = { toggleNotifications() }
            )
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = "Aparencia",
                subtitle = appearance.label,
                iconColor = RumoColors.Purple,
                onClick = { cycleAppearance() }
            )
            SettingsRow(
                icon = Icons.Default.DeleteForever,
                title = "Excluir conta",
                subtitle = "Apagar todos os dados",
                iconColor = RumoColors.Red,
                onClick = { showDeleteDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Support section
        SettingsSection(title = "Suporte") {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Help,
                title = "Central de ajuda",
                iconColor = RumoColors.AccentBlue
            )
            SettingsRow(
                icon = Icons.Default.Feedback,
                title = "Enviar feedback",
                iconColor = RumoColors.Accent
            )
            SettingsRow(
                icon = Icons.Default.Info,
                title = "Sobre o app",
                subtitle = "Versao 1.0.0",
                iconColor = RumoColors.SubtleText
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout button
        Button(
            onClick = {
                RetrofitInstance.authToken = null
                onLogout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RumoColors.Red.copy(alpha = 0.12f)
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = RumoColors.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sair da conta",
                color = RumoColors.Red,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            color = RumoColors.SubtleText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = RumoColors.AccentBlue,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = RumoColors.SubtleText.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsRowWithSwitch(
    icon: ImageVector,
    title: String,
    isChecked: Boolean,
    iconColor: Color = RumoColors.AccentBlue,
    onToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isChecked) "Ativadas" else "Desativadas",
                color = RumoColors.SubtleText,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RumoColors.Accent,
                uncheckedThumbColor = RumoColors.SubtleText,
                uncheckedTrackColor = RumoColors.SurfaceVariant
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ProfileScreenPreview() {
    RumoAgenteTheme {
        ProfileScreen()
    }
}
