package com.rumoagente.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit = {}
) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Logo with glow effect
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            // Glow background
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .blur(30.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                RumoColors.Accent.copy(alpha = 0.5f),
                                RumoColors.Accent.copy(alpha = 0.0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Logo circle
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(RumoColors.Accent, RumoColors.AccentBlue)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Rumo Agente",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Seu assistente inteligente",
            style = MaterialTheme.typography.bodyMedium,
            color = RumoColors.SubtleText,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Name field (signup only)
        AnimatedVisibility(
            visible = !isLogin,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome completo") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = RumoColors.SubtleText
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RumoColors.Accent,
                    unfocusedBorderColor = RumoColors.CardBorder,
                    focusedContainerColor = RumoColors.CardBg,
                    unfocusedContainerColor = RumoColors.CardBg,
                    cursorColor = RumoColors.Accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = RumoColors.Accent,
                    unfocusedLabelColor = RumoColors.SubtleText
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
        }

        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = RumoColors.SubtleText
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RumoColors.Accent,
                unfocusedBorderColor = RumoColors.CardBorder,
                focusedContainerColor = RumoColors.CardBg,
                unfocusedContainerColor = RumoColors.CardBg,
                cursorColor = RumoColors.Accent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = RumoColors.Accent,
                unfocusedLabelColor = RumoColors.SubtleText
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = RumoColors.SubtleText
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                        tint = RumoColors.SubtleText
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RumoColors.Accent,
                unfocusedBorderColor = RumoColors.CardBorder,
                focusedContainerColor = RumoColors.CardBg,
                unfocusedContainerColor = RumoColors.CardBg,
                cursorColor = RumoColors.Accent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = RumoColors.Accent,
                unfocusedLabelColor = RumoColors.SubtleText
            ),
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )

        // Forgot password
        if (isLogin) {
            TextButton(
                onClick = { /* TODO: forgot password */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Esqueci minha senha",
                    color = RumoColors.AccentBlue,
                    fontSize = 13.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Primary button
        Button(
            onClick = {
                isLoading = true
                onAuthSuccess()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RumoColors.Accent
            ),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isLogin) "Entrar" else "Criar Conta",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Divider "ou"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = RumoColors.Divider
            )
            Text(
                text = "ou",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = RumoColors.SubtleText,
                fontSize = 13.sp
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = RumoColors.Divider
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google sign-in button
        OutlinedButton(
            onClick = { /* TODO: Google sign-in */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White
            ),
            border = null
        ) {
            Text(
                text = "G",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF4285F4),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Continuar com Google",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Apple sign-in button
        Button(
            onClick = { /* TODO: Apple sign-in */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RumoColors.CardBg
            )
        ) {
            Text(
                text = "\uF8FF",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Continuar com Apple",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Toggle login/signup
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isLogin) "Nao tem conta? " else "Ja tem conta? ",
                color = RumoColors.SubtleText,
                fontSize = 14.sp
            )
            Text(
                text = if (isLogin) "Criar conta" else "Entrar",
                color = RumoColors.Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    isLogin = !isLogin
                    isLoading = false
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun AuthScreenPreview() {
    RumoAgenteTheme {
        AuthScreen()
    }
}
