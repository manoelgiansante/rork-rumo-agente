package com.rumoagente.ui.screens

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore for onboarding preferences
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding_prefs"
)

object OnboardingPrefs {
    val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")

    suspend fun hasOnboarded(context: Context): Boolean {
        return context.onboardingDataStore.data
            .map { prefs -> prefs[HAS_ONBOARDED] ?: false }
            .firstOrNull() ?: false
    }

    suspend fun setOnboarded(context: Context) {
        context.onboardingDataStore.edit { prefs ->
            prefs[HAS_ONBOARDED] = true
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val glowColor: Color
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.SmartToy,
        title = "Seu Computador\nem Nuvem",
        subtitle = "Controle um computador completo direto do seu celular. Instale apps, navegue e trabalhe \u2014 de qualquer lugar.",
        glowColor = RumoColors.Accent
    ),
    OnboardingPage(
        icon = Icons.Default.Chat,
        title = "Agente IA\nInteligente",
        subtitle = "D\u00ea comandos por chat e o agente executa no computador. Sem precisar de secret\u00e1ria \u2014 a IA faz por voc\u00ea.",
        glowColor = RumoColors.AccentBlue
    ),
    OnboardingPage(
        icon = Icons.Default.Security,
        title = "Feito para o\nAgroneg\u00f3cio",
        subtitle = "Ponta do S, Rumo M\u00e1quinas, Aegro e muito mais. O agente domina os softwares da sua fazenda.",
        glowColor = Color(0xFF339933)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = pages[pageIndex]
            OnboardingPageContent(page = page)
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                pages.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "dot_width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) RumoColors.Accent else Color.White.copy(alpha = 0.2f),
                        animationSpec = tween(300),
                        label = "dot_color"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary button
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        coroutineScope.launch {
                            OnboardingPrefs.setOnboarded(context)
                            onOnboardingComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RumoColors.Accent
                )
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.size - 1) "Pr\u00f3ximo" else "Come\u00e7ar",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }

            // Skip button (hidden on last page)
            if (pagerState.currentPage < pages.size - 1) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            OnboardingPrefs.setOnboarded(context)
                            onOnboardingComplete()
                        }
                    }
                ) {
                    Text(
                        text = "Pular",
                        color = RumoColors.SubtleText,
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with radial glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Glow background
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .blur(40.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                page.glowColor.copy(alpha = 0.4f),
                                page.glowColor.copy(alpha = 0.0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Icon circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = page.glowColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = RumoColors.SubtleText,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
