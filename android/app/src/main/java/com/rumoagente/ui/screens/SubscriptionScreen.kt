package com.rumoagente.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.data.api.RetrofitInstance
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

// ════════════════════════════════════════════════════════════════════════
//  Data models — matches iOS SubscriptionPlan exactly
// ════════════════════════════════════════════════════════════════════════

enum class SubscriptionPlan(
    val displayName: String,
    val monthlyPrice: Double,
    val includedCredits: Int,
    val planDescription: String,
    val planId: String
) {
    FREE("Gratuito", 0.0, 10, "Teste o agente com 10 créditos", "free"),
    STARTER("Starter", 49.90, 100, "Ideal para pequenos produtores", "starter"),
    PRO("Pro", 149.90, 500, "Para fazendas de médio porte", "pro"),
    ENTERPRISE("Enterprise", 499.90, 2000, "Operações de grande escala", "enterprise");
}

// ════════════════════════════════════════════════════════════════════════
//  SubscriptionScreen — matches iOS SubscriptionView exactly
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit = {},
    currentPlan: SubscriptionPlan = SubscriptionPlan.FREE,
    currentCredits: Int = 10
) {
    var selectedPlan by remember { mutableStateOf(SubscriptionPlan.PRO) }
    var isProcessing by remember { mutableStateOf(false) }
    var checkoutError by remember { mutableStateOf<String?>(null) }
    var showTransactions by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val maxCredits = currentPlan.includedCredits.coerceAtLeast(1)
    val usageProgress = currentCredits.toFloat() / maxCredits.toFloat()

    fun openCheckoutUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun subscribe(plan: SubscriptionPlan) {
        // Match iOS: open web URL for checkout
        openCheckoutUrl("https://rork-rumo-agente.vercel.app/#subscription")
    }

    fun buyCredits(amount: Int) {
        openCheckoutUrl("https://rork-rumo-agente.vercel.app/#subscription")
    }

    Scaffold(
        containerColor = RumoColors.DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Planos e Créditos",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar", color = RumoColors.Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RumoColors.DarkBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Usage Overview (matches iOS usageOverview) ──────────────
            UsageOverviewCard(
                credits = currentCredits,
                maxCredits = maxCredits,
                progress = usageProgress
            )

            // ── Plans Section (matches iOS plansSection) ────────────────
            PlansSection(
                selectedPlan = selectedPlan,
                currentPlan = currentPlan,
                isProcessing = isProcessing,
                checkoutError = checkoutError,
                onSelectPlan = { selectedPlan = it },
                onSubscribe = { subscribe(it) }
            )

            // ── Extra Credits Section (matches iOS extraCreditsSection) ─
            ExtraCreditsSection(
                onBuyCredits = { amount -> buyCredits(amount) }
            )

            // ── Features Comparison (matches iOS featuresComparison) ────
            FeaturesComparisonSection(selectedPlan = selectedPlan)

            // ── Transaction History (matches iOS transactionHistorySection)
            TransactionHistorySection(
                showTransactions = showTransactions,
                onToggle = { showTransactions = !showTransactions }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Usage Overview Card — matches iOS usageOverview
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun UsageOverviewCard(
    credits: Int,
    maxCredits: Int,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RumoColors.CardBg)
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Uso Atual",
                        color = RumoColors.SubtleText,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$credits",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "/ $maxCredits",
                            color = RumoColors.SubtleText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Circular progress (matches iOS CircularProgressView)
                CircularUsageIndicator(
                    progress = 1f - progress,
                    size = 64.dp,
                    strokeWidth = 8.dp
                )
            }

            // Linear progress bar (matches iOS ProgressView)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RumoColors.Accent,
                trackColor = RumoColors.CardBorder,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CircularUsageIndicator(
    progress: Float,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension
        val radius = (diameter - stroke) / 2
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(diameter - stroke, diameter - stroke)

        // Background track
        drawArc(
            color = RumoColors.CardBorder,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Progress arc
        drawArc(
            color = RumoColors.Accent,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Plans Section — matches iOS plansSection
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun PlansSection(
    selectedPlan: SubscriptionPlan,
    currentPlan: SubscriptionPlan,
    isProcessing: Boolean,
    checkoutError: String?,
    onSelectPlan: (SubscriptionPlan) -> Unit,
    onSubscribe: (SubscriptionPlan) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Escolha seu Plano",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )

        SubscriptionPlan.entries.forEach { plan ->
            PlanCard(
                plan = plan,
                isSelected = selectedPlan == plan,
                isCurrent = currentPlan == plan,
                onTap = { onSelectPlan(plan) }
            )
        }

        // "Assinar" button — only show if not current plan and not free
        if (selectedPlan != currentPlan && selectedPlan != SubscriptionPlan.FREE) {
            Button(
                onClick = { onSubscribe(selectedPlan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RumoColors.Accent
                ),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Assinar ${selectedPlan.displayName}",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                }
            }

            if (checkoutError != null) {
                Text(
                    text = checkoutError,
                    color = RumoColors.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  PlanCard — matches iOS PlanCard exactly
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun PlanCard(
    plan: SubscriptionPlan,
    isSelected: Boolean,
    isCurrent: Boolean,
    onTap: () -> Unit
) {
    val borderColor = if (isSelected) RumoColors.Accent.copy(alpha = 0.5f)
    else RumoColors.CardBorder
    val bgColor = if (isSelected) RumoColors.Accent.copy(alpha = 0.06f)
    else RumoColors.CardBg

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onTap() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = plan.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = RumoColors.Accent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ATUAL",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = RumoColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (plan == SubscriptionPlan.PRO) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = RumoColors.Orange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "POPULAR",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = RumoColors.Orange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = plan.planDescription,
                    color = RumoColors.SubtleText,
                    fontSize = 12.sp
                )

                Text(
                    text = "${plan.includedCredits} créditos/mês",
                    color = RumoColors.Accent,
                    fontSize = 12.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (plan.monthlyPrice > 0) {
                    Text(
                        text = "R\$ ${String.format("%.2f", plan.monthlyPrice)}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "/mês",
                        color = RumoColors.SubtleText,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Grátis",
                        color = RumoColors.Accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Extra Credits Section — matches iOS extraCreditsSection
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtraCreditsSection(
    onBuyCredits: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Créditos Extras",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CreditPackCard(
                amount = 50,
                price = "R\$ 19,90",
                onClick = { onBuyCredits(50) },
                modifier = Modifier.weight(1f)
            )
            CreditPackCard(
                amount = 200,
                price = "R\$ 59,90",
                onClick = { onBuyCredits(200) },
                modifier = Modifier.weight(1f)
            )
            CreditPackCard(
                amount = 500,
                price = "R\$ 119,90",
                onClick = { onBuyCredits(500) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  CreditPackCard — matches iOS CreditPackCard exactly
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun CreditPackCard(
    amount: Int,
    price: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(RumoColors.CardBg)
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "+$amount",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Text(
                text = "créditos",
                color = RumoColors.SubtleText,
                fontSize = 12.sp
            )
            Text(
                text = price,
                color = RumoColors.Accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Features Comparison — matches iOS featuresComparison
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun FeaturesComparisonSection(selectedPlan: SubscriptionPlan) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "O que está incluído",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureRow(text = "Acesso ao agente inteligente", included = true)
            FeatureRow(text = "Chat com agente IA", included = true)
            FeatureRow(text = "Instalação de apps", included = true)
            FeatureRow(
                text = "Streaming da tela em tempo real",
                included = selectedPlan != SubscriptionPlan.FREE
            )
            FeatureRow(
                text = "Suporte prioritário",
                included = selectedPlan == SubscriptionPlan.PRO || selectedPlan == SubscriptionPlan.ENTERPRISE
            )
            FeatureRow(
                text = "Processamento dedicado",
                included = selectedPlan == SubscriptionPlan.ENTERPRISE
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String, included: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (included) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (included) RumoColors.Accent else RumoColors.SubtleText.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = if (included) Color.White else RumoColors.SubtleText.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Transaction History — matches iOS transactionHistorySection
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun TransactionHistorySection(
    showTransactions: Boolean,
    onToggle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Histórico de Transações",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Icon(
                imageVector = if (showTransactions) Icons.Default.KeyboardArrowUp
                else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = RumoColors.SubtleText,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = showTransactions,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            // Empty state (matches iOS)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = RumoColors.SubtleText,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Nenhuma transação ainda",
                        color = RumoColors.SubtleText,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Preview
// ════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun SubscriptionScreenPreview() {
    RumoAgenteTheme {
        SubscriptionScreen()
    }
}
