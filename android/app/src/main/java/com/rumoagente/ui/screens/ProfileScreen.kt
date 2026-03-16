package com.rumoagente.ui.screens

import com.rumoagente.data.api.Config
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

private object SettingsKeys {
    val LANGUAGE = stringPreferencesKey("language")
    val NOTIFY_TASKS = booleanPreferencesKey("notifications_tasks")
    val NOTIFY_CREDITS = booleanPreferencesKey("notifications_credits")
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
    var displayPlan by remember { mutableStateOf("Gratuito") }
    var displayCredits by remember { mutableStateOf(10) }
    var displayInitial by remember { mutableStateOf("U") }

    // Settings state
    var appLanguage by remember { mutableStateOf("pt") }
    var notifyTasks by remember { mutableStateOf(true) }
    var notifyCredits by remember { mutableStateOf(true) }

    // Dialogs
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Sheets
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    val languageDisplayName = when (appLanguage) {
        "pt" -> "Portugu\u00eas"
        "en" -> "English"
        "es" -> "Espa\u00f1ol"
        else -> "Portugu\u00eas"
    }

    // Load saved preferences
    LaunchedEffect(Unit) {
        val prefs = context.settingsDataStore.data.firstOrNull()
        if (prefs != null) {
            appLanguage = prefs[SettingsKeys.LANGUAGE] ?: "pt"
            notifyTasks = prefs[SettingsKeys.NOTIFY_TASKS] ?: true
            notifyCredits = prefs[SettingsKeys.NOTIFY_CREDITS] ?: true
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
                    displayPlan = profile.plan.lowercase().replaceFirstChar { it.uppercase() }
                    displayCredits = profile.credits
                    displayInitial = (displayName.firstOrNull()?.uppercase() ?: "U")
                }
            }
        } catch (_: Exception) { }
    }

    fun saveLanguage(code: String) {
        appLanguage = code
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.LANGUAGE] = code
            }
        }
    }

    fun saveNotifyTasks(enabled: Boolean) {
        notifyTasks = enabled
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.NOTIFY_TASKS] = enabled
            }
        }
    }

    fun saveNotifyCredits(enabled: Boolean) {
        notifyCredits = enabled
        coroutineScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[SettingsKeys.NOTIFY_CREDITS] = enabled
            }
        }
    }

    fun handleDeleteAccount() {
        isDeleting = true
        coroutineScope.launch {
            try {
                context.settingsDataStore.edit { it.clear() }
                RetrofitInstance.authToken = null
                isDeleting = false
                showDeleteDialog = false
                onLogout()
            } catch (e: Exception) {
                deleteError = e.localizedMessage ?: "Erro desconhecido"
                isDeleting = false
            }
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    // ── Logout confirmation dialog ─────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = RumoColors.CardBg,
            titleContentColor = Color.White,
            textContentColor = RumoColors.SubtleText,
            title = { Text("Sair da conta?") },
            text = { Text("Voc\u00ea precisar\u00e1 fazer login novamente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        RetrofitInstance.authToken = null
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.Red)
                ) {
                    Text("Sair")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ── Delete account confirmation dialog ─────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            containerColor = RumoColors.CardBg,
            titleContentColor = Color.White,
            textContentColor = RumoColors.SubtleText,
            title = { Text("Excluir conta?") },
            text = {
                Text("Esta a\u00e7\u00e3o \u00e9 irrevers\u00edvel. Todos os seus dados ser\u00e3o apagados permanentemente.")
            },
            confirmButton = {
                TextButton(
                    onClick = { handleDeleteAccount() },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.Red)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RumoColors.Red,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ── Delete error dialog ────────────────────────────────────────────
    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            containerColor = RumoColors.CardBg,
            titleContentColor = Color.White,
            textContentColor = RumoColors.SubtleText,
            title = { Text("Erro ao excluir conta") },
            text = { Text(deleteError ?: "") },
            confirmButton = {
                TextButton(
                    onClick = { deleteError = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // ── Language picker dialog ─────────────────────────────────────────
    if (showLanguagePicker) {
        LanguagePickerDialog(
            selectedLanguage = appLanguage,
            onSelect = { code ->
                saveLanguage(code)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    // ── Notification settings dialog ───────────────────────────────────
    if (showNotificationSettings) {
        NotificationSettingsDialog(
            notifyTasks = notifyTasks,
            notifyCredits = notifyCredits,
            onToggleTasks = { saveNotifyTasks(it) },
            onToggleCredits = { saveNotifyCredits(it) },
            onDismiss = { showNotificationSettings = false }
        )
    }

    // ── Main content ───────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Profile header ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = RumoColors.Accent.copy(alpha = 0.3f),
                        spotColor = RumoColors.AccentBlue.copy(alpha = 0.3f)
                    )
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
                    fontSize = 34.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayEmail,
                color = RumoColors.SubtleText,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Plan card ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            RumoColors.Accent.copy(alpha = 0.06f),
                            RumoColors.CardBg
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(18.dp))
                .clickable { onNavigateToSubscription() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            RumoColors.Accent.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = RumoColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Plano $displayPlan",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$displayCredits cr\u00e9ditos restantes",
                        color = RumoColors.SubtleText,
                        fontSize = 14.sp
                    )
                }

                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = RumoColors.SubtleText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Configura\u00e7\u00f5es section (matches iOS: Idioma, Notifica\u00e7\u00f5es, Privacidade) ──
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ProfileSectionHeader(title = "Configura\u00e7\u00f5es")

            ProfileSettingsRow(
                icon = Icons.Default.Language,
                title = "Idioma",
                value = languageDisplayName,
                onClick = { showLanguagePicker = true }
            )

            ProfileSettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notifica\u00e7\u00f5es",
                value = if (notifyTasks) "Ativadas" else "Desativadas",
                onClick = { showNotificationSettings = true }
            )

            ProfileSettingsRow(
                icon = Icons.Default.Shield,
                title = "Privacidade",
                value = null,
                onClick = { openUrl("${Config.AGENT_URL}/privacidade") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Suporte section (matches iOS: Contato, Termos de Uso) ────
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ProfileSectionHeader(title = "Suporte")

            ProfileSettingsRow(
                icon = Icons.Default.Email,
                title = "Contato",
                value = null,
                onClick = { openEmail("suporte@rumoagente.com.br") }
            )

            ProfileSettingsRow(
                icon = Icons.Default.Description,
                title = "Termos de Uso",
                value = null,
                onClick = { openUrl("${Config.AGENT_URL}/termos") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Logout button ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    RumoColors.Red.copy(alpha = 0.08f),
                    RoundedCornerShape(14.dp)
                )
                .clickable { showLogoutDialog = true }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = RumoColors.Red,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sair da Conta",
                    color = RumoColors.Red,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Delete account button ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(enabled = !isDeleting) { showDeleteDialog = true }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = RumoColors.Red.copy(alpha = 0.7f),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = RumoColors.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Excluir Conta",
                    color = RumoColors.Red.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }

        // ── App version ────────────────────────────────────────────────
        Text(
            text = "Rumo Agente v1.0.0",
            color = RumoColors.SubtleText.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Settings row matching iOS SettingsRowContent ────────────────────────
@Composable
private fun ProfileSettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(RumoColors.CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = RumoColors.AccentBlue,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (value != null) {
            Text(
                text = value,
                color = RumoColors.SubtleText,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = RumoColors.SubtleText.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Section header matching iOS SectionHeader ───────────────────────────
@Composable
private fun ProfileSectionHeader(title: String) {
    Text(
        text = title,
        color = RumoColors.SubtleText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ── Language picker dialog (matches iOS LanguagePickerSheet) ────────────
@Composable
private fun LanguagePickerDialog(
    selectedLanguage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        Triple("pt", "Portugu\u00eas", "\uD83C\uDDE7\uD83C\uDDF7"),
        Triple("en", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
        Triple("es", "Espa\u00f1ol", "\uD83C\uDDEA\uD83C\uDDF8")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RumoColors.CardBg,
        titleContentColor = Color.White,
        title = { Text("Idioma") },
        text = {
            Column {
                languages.forEach { (code, name, flag) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = flag,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedLanguage == code) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RumoColors.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
            ) {
                Text("Fechar")
            }
        }
    )
}

// ── Notification settings dialog (matches iOS NotificationSettingsSheet) ──
@Composable
private fun NotificationSettingsDialog(
    notifyTasks: Boolean,
    notifyCredits: Boolean,
    onToggleTasks: (Boolean) -> Unit,
    onToggleCredits: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RumoColors.CardBg,
        titleContentColor = Color.White,
        title = { Text("Notifica\u00e7\u00f5es") },
        text = {
            Column {
                // Tarefas Conclu\u00eddas toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RumoColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Tarefas Conclu\u00eddas",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notifyTasks,
                        onCheckedChange = { onToggleTasks(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RumoColors.Accent,
                            uncheckedThumbColor = RumoColors.SubtleText,
                            uncheckedTrackColor = RumoColors.SurfaceVariant
                        )
                    )
                }

                // Cr\u00e9ditos Baixos toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = RumoColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cr\u00e9ditos Baixos",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notifyCredits,
                        onCheckedChange = { onToggleCredits(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RumoColors.Accent,
                            uncheckedThumbColor = RumoColors.SubtleText,
                            uncheckedTrackColor = RumoColors.SurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Receba alertas quando tarefas forem conclu\u00eddas ou quando seus cr\u00e9ditos estiverem acabando.",
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = RumoColors.SubtleText)
            ) {
                Text("Fechar")
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ProfileScreenPreview() {
    RumoAgenteTheme {
        ProfileScreen()
    }
}
