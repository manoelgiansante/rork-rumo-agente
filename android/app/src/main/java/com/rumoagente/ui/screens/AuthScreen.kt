package com.rumoagente.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.rumoagente.data.api.Config
import com.rumoagente.data.repository.AuthRepository
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }

    // Pulsing animation for the brain icon glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Reset password dialog
    if (showResetPasswordDialog) {
        ResetPasswordDialog(
            authRepository = authRepository,
            onDismiss = { showResetPasswordDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // ── Logo with radial glow (matches iOS brain.head.profile.fill + RadialGradient) ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 0.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                // Radial glow background
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .blur(30.dp)
                        .alpha(pulseAlpha)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    RumoColors.Accent.copy(alpha = 0.3f),
                                    RumoColors.AccentBlue.copy(alpha = 0.15f),
                                    RumoColors.DarkBg
                                ),
                                radius = 160f
                            ),
                            shape = CircleShape
                        )
                )

                // Brain icon styled like iOS
                Text(
                    text = "🧠",
                    fontSize = 52.sp,
                    modifier = Modifier.alpha(pulseAlpha)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Rumo Agente",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isSignUp) "Crie sua conta" else "Entre na sua conta",
                style = MaterialTheme.typography.bodyMedium,
                color = RumoColors.SubtleText
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Input fields ──
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name field (sign up only)
            AnimatedVisibility(
                visible = isSignUp,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AuthInputField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Nome completo",
                    icon = Icons.Default.Person,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
            }

            // Email field
            AuthInputField(
                value = email,
                onValueChange = { email = it },
                placeholder = "Email",
                icon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            // Password field
            AuthPasswordField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Senha",
                passwordVisible = passwordVisible,
                onToggleVisibility = { passwordVisible = !passwordVisible },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
        }

        // ── Error message ──
        AnimatedVisibility(visible = errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = RumoColors.Red,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp)
            )
        }

        // ── Terms checkbox (sign up only) ──
        AnimatedVisibility(
            visible = isSignUp,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { agreedToTerms = !agreedToTerms },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (agreedToTerms) {
                            Icons.Default.Person // placeholder - using CheckBox text below
                        } else {
                            Icons.Default.Person
                        },
                        contentDescription = null,
                        tint = Color.Transparent,
                        modifier = Modifier.size(0.dp)
                    )
                    Text(
                        text = if (agreedToTerms) "☑" else "☐",
                        fontSize = 22.sp,
                        color = if (agreedToTerms) RumoColors.Accent else RumoColors.SubtleText
                    )
                }

                val annotatedString = buildAnnotatedString {
                    withStyle(SpanStyle(color = RumoColors.SubtleText, fontSize = 12.sp)) {
                        append("Li e concordo com a ")
                    }
                    pushStringAnnotation("URL", "https://rork-rumo-agente.vercel.app/privacidade")
                    withStyle(
                        SpanStyle(
                            color = RumoColors.AccentBlue,
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Política de Privacidade")
                    }
                    pop()
                    withStyle(SpanStyle(color = RumoColors.SubtleText, fontSize = 12.sp)) {
                        append(" e os ")
                    }
                    pushStringAnnotation("URL", "https://rork-rumo-agente.vercel.app/termos")
                    withStyle(
                        SpanStyle(
                            color = RumoColors.AccentBlue,
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Termos de Uso")
                    }
                    pop()
                }

                ClickableText(
                    text = annotatedString,
                    modifier = Modifier.weight(1f),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Primary action button ──
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val buttonEnabled = !isLoading && !isGoogleLoading &&
                    (!isSignUp || agreedToTerms)

            Button(
                onClick = {
                    errorMessage = null

                    // Validation
                    if (email.isBlank() || password.isBlank() || (isSignUp && displayName.isBlank())) {
                        errorMessage = "Preencha todos os campos."
                        return@Button
                    }
                    val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                    if (!emailPattern.matches(email.trim())) {
                        errorMessage = "Digite um email válido."
                        return@Button
                    }
                    if (isSignUp && password.length < 6) {
                        errorMessage = "A senha deve ter pelo menos 6 caracteres."
                        return@Button
                    }

                    isLoading = true
                    coroutineScope.launch {
                        val result = if (isSignUp) {
                            authRepository.signUp(email.trim(), password, displayName.trim())
                        } else {
                            authRepository.signIn(email.trim(), password)
                        }
                        isLoading = false
                        if (result.isSuccess) {
                            onAuthSuccess()
                        } else {
                            errorMessage = result.exceptionOrNull()?.localizedMessage
                                ?: "Erro ao autenticar"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RumoColors.Accent.copy(
                        alpha = if (isSignUp && !agreedToTerms) 0.4f else 1.0f
                    ),
                    disabledContainerColor = RumoColors.Accent.copy(alpha = 0.4f)
                ),
                enabled = buttonEnabled
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isSignUp) "Criar Conta" else "Entrar",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }

            // Forgot password (login only)
            if (!isSignUp) {
                TextButton(
                    onClick = { showResetPasswordDialog = true }
                ) {
                    Text(
                        text = "Esqueci minha senha",
                        color = RumoColors.AccentBlue,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Divider "ou" ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = RumoColors.CardBorder
            )
            Text(
                text = "ou",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = RumoColors.SubtleText,
                fontSize = 12.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = RumoColors.CardBorder
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Social sign-in buttons ──
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Apple sign-in button (white bg, black text — matches iOS)
            Button(
                onClick = {
                    // Apple Sign-In via web on Android
                    uriHandler.openUri("https://rork-rumo-agente.vercel.app/auth/apple")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                enabled = !isLoading && !isGoogleLoading
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "",
                        fontSize = 20.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "Continuar com Apple",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                }
            }

            // Google sign-in button (dark bg, white text — matches iOS)
            Button(
                onClick = {
                    errorMessage = null
                    isGoogleLoading = true

                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(Config.GOOGLE_WEB_CLIENT_ID)
                        .setAutoSelectEnabled(true)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    coroutineScope.launch {
                        try {
                            val result: GetCredentialResponse = credentialManager.getCredential(
                                request = request,
                                context = context
                            )
                            handleGoogleSignInResult(
                                result = result,
                                authRepository = authRepository,
                                onSuccess = {
                                    isGoogleLoading = false
                                    onAuthSuccess()
                                },
                                onError = { message ->
                                    isGoogleLoading = false
                                    errorMessage = message
                                }
                            )
                        } catch (e: GetCredentialCancellationException) {
                            isGoogleLoading = false
                        } catch (e: Exception) {
                            isGoogleLoading = false
                            Log.e("AuthScreen", "Google sign-in failed", e)
                            errorMessage = "Falha no login com Google: ${e.localizedMessage}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF26292E)
                ),
                enabled = !isLoading && !isGoogleLoading
            ) {
                if (isGoogleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = "Continuar com Google",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Toggle login/signup ──
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isSignUp) "Já tem conta?" else "Não tem conta?",
                color = RumoColors.SubtleText,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSignUp) "Entrar" else "Criar conta",
                color = RumoColors.Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    isSignUp = !isSignUp
                    errorMessage = null
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ── Reusable input field (matches iOS AuthTextField) ──
@Composable
private fun AuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = RumoColors.SubtleText
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RumoColors.SubtleText,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RumoColors.CardBorder,
            unfocusedBorderColor = RumoColors.CardBorder,
            focusedContainerColor = RumoColors.CardBg,
            unfocusedContainerColor = RumoColors.CardBg,
            cursorColor = RumoColors.Accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true
    )
}

// ── Password field with toggle (matches iOS AuthSecureField) ──
@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = RumoColors.SubtleText
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = RumoColors.SubtleText,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                    tint = RumoColors.SubtleText
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RumoColors.CardBorder,
            unfocusedBorderColor = RumoColors.CardBorder,
            focusedContainerColor = RumoColors.CardBg,
            unfocusedContainerColor = RumoColors.CardBg,
            cursorColor = RumoColors.Accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true
    )
}

// ── Reset password dialog (matches iOS ResetPasswordSheet) ──
@Composable
private fun ResetPasswordDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var resetEmail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resetSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RumoColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📧",
                    fontSize = 48.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (resetSent) {
                    Text(
                        text = "Email enviado!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verifique sua caixa de entrada para redefinir sua senha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RumoColors.SubtleText,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Recuperar Senha",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Digite seu email e enviaremos um link para redefinir sua senha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RumoColors.SubtleText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        placeholder = { Text("Email", color = RumoColors.SubtleText) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RumoColors.AccentBlue,
                            unfocusedBorderColor = RumoColors.CardBorder,
                            focusedContainerColor = RumoColors.CardBg,
                            unfocusedContainerColor = RumoColors.CardBg,
                            cursorColor = RumoColors.AccentBlue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = RumoColors.Red,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (resetEmail.isBlank()) {
                                errorMessage = "Digite seu email."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                val result = authRepository.recoverPassword(resetEmail.trim())
                                isLoading = false
                                if (result.isSuccess) {
                                    resetSent = true
                                } else {
                                    errorMessage = result.exceptionOrNull()?.localizedMessage
                                        ?: "Erro ao enviar email"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RumoColors.AccentBlue
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Enviar Link",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Fechar",
                    color = RumoColors.AccentBlue
                )
            }
        }
    )
}

private suspend fun handleGoogleSignInResult(
    result: GetCredentialResponse,
    authRepository: AuthRepository,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val credential = result.credential

    when (credential) {
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val authResult = authRepository.signInWithIdToken(idToken)
                    if (authResult.isSuccess) {
                        onSuccess()
                    } else {
                        val errorMsg = authResult.exceptionOrNull()?.localizedMessage
                            ?: "Erro ao autenticar com Supabase"
                        onError(errorMsg)
                    }
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("AuthScreen", "Google ID token parsing failed", e)
                    onError("Erro ao processar credenciais do Google")
                }
            } else {
                onError("Tipo de credencial inesperado")
            }
        }
        else -> {
            onError("Tipo de credencial inesperado")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun AuthScreenPreview() {
    RumoAgenteTheme {
        AuthScreen()
    }
}
